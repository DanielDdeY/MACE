package com.mace.presentation;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import static java.lang.foreign.ValueLayout.*;

/** A process-lifetime reservation, shared by MACE and Preview in one user/session.
 * No mutex ownership/wait, file lock, PID file, native telemetry DLL or runtime bypass.
 */
final class WindowsSingleInstance {
    enum Status { ACQUIRED, BUSY, UNAVAILABLE }
    record Result(Status status, int error) {}
    private static final String PRODUCT = "MACE.Instance.v1";
    // Deliberately never closed, including after Java cleanup and during shutdown hooks.
    // The kernel releases these non-inheritable handles at process death (also halt/kill).
    private static final List<MemorySegment> PROCESS_HANDLES = new ArrayList<>();

    static Result acquire() { return acquire(PRODUCT); }

    /** Package-private namespace injection for disposable test JVMs; no product setting exposes it. */
    static Result acquire(String namespace) {
        try (Arena arena = Arena.ofConfined()) {
            var api = Api.INSTANCE;
            var tokenOut = arena.allocate(ADDRESS);
            var current = MemorySegment.ofAddress(-1L);
            if ((int) api.openToken.invokeExact(current, 8, tokenOut) == 0)
                return new Result(Status.UNAVAILABLE, -1);
            var token = tokenOut.get(ADDRESS, 0);
            String sid;
            try {
                var length = arena.allocate(JAVA_INT);
                int ignored = (int) api.tokenInfo.invokeExact(token, 1, MemorySegment.NULL, 0, length);
                int bytes = length.get(JAVA_INT, 0);
                if (bytes < ADDRESS.byteSize() || bytes > 65536) return new Result(Status.UNAVAILABLE, -1);
                var info = arena.allocate(bytes, ADDRESS.byteAlignment());
                if ((int) api.tokenInfo.invokeExact(token, 1, info, bytes, length) == 0)
                    return new Result(Status.UNAVAILABLE, -1);
                var sidOut = arena.allocate(ADDRESS);
                if ((int) api.sidToString.invokeExact(info.get(ADDRESS, 0), sidOut) == 0)
                    return new Result(Status.UNAVAILABLE, -1);
                var sidText = sidOut.get(ADDRESS, 0);
                try {
                    int characters = (int) api.stringLength.invokeExact(sidText);
                    sid = sidText.reinterpret((long) characters * 2 + 2).getString(0, StandardCharsets.UTF_16LE);
                } finally { api.free(sidText); }
            } finally { api.close(token); }
            var session = arena.allocate(JAVA_INT);
            if ((int) api.sessionId.invokeExact((int) ProcessHandle.current().pid(), session) == 0)
                return new Result(Status.UNAVAILABLE, -1);
            String name = objectName(namespace, sid, session.get(JAVA_INT, 0));
            // Only read/synchronize access is needed for existence detection. Medium label also
            // permits same-SID high/medium callers without granting mutation or mutex ownership.
            String sddl = "D:P(A;;0x00120000;;;SY)(A;;0x00120000;;;" + sid + ")S:(ML;;NW;;;ME)";
            var descriptorOut = arena.allocate(ADDRESS);
            if ((int) api.securityDescriptor.invokeExact(arena.allocateFrom(sddl, StandardCharsets.UTF_16LE),
                    1, descriptorOut, MemorySegment.NULL) == 0) return new Result(Status.UNAVAILABLE, -1);
            var descriptor = descriptorOut.get(ADDRESS, 0);
            try {
                // SECURITY_ATTRIBUTES, Windows x64: DWORD, padding, pointer, BOOL, padding.
                var attributes = arena.allocate(24, 8);
                attributes.set(JAVA_INT, 0, 24);
                attributes.set(ADDRESS, 8, descriptor);
                attributes.set(JAVA_INT, 16, 0);
                var errors = arena.allocate(Linker.Option.captureStateLayout());
                var handle = (MemorySegment) api.create.invokeExact(errors, attributes,
                        arena.allocateFrom(name, StandardCharsets.UTF_16LE), 0, 0x00120000);
                int error = errors.get(JAVA_INT, api.errorOffset);
                if (handle.address() == 0) return new Result(Status.UNAVAILABLE, error);
                if (error == 183) {
                    // Close BEFORE notification: a loser must not keep a dying winner's lock alive.
                    api.close(handle);
                    return new Result(Status.BUSY, error);
                }
                synchronized (PROCESS_HANDLES) { PROCESS_HANDLES.add(handle); }
                return new Result(Status.ACQUIRED, 0);
            } finally { api.free(descriptor); }
        } catch (Throwable error) {
            return new Result(Status.UNAVAILABLE, -1); // Fail closed, before native/helper initialization.
        }
    }

    static String objectName(String namespace, String sid, int session) throws Exception {
        if (!namespace.matches("[A-Za-z0-9.-]{1,80}")) throw new IllegalArgumentException("Invalid namespace");
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((sid + ":" + Integer.toUnsignedString(session)).getBytes(StandardCharsets.UTF_8));
        return "Local\\" + namespace + "." + HexFormat.of().formatHex(digest);
    }

    private static final class Api {
        static final Api INSTANCE = new Api();
        final Linker linker = Linker.nativeLinker();
        // System KnownDLLs, independent of mace_native.dll and its deployment directory.
        final SymbolLookup kernel = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
        final SymbolLookup security = SymbolLookup.libraryLookup("advapi32.dll", Arena.global());
        final MethodHandle create = linker.downcallHandle(kernel.find("CreateMutexExW").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT),
                Linker.Option.captureCallState("GetLastError"));
        final long errorOffset = Linker.Option.captureStateLayout()
                .byteOffset(MemoryLayout.PathElement.groupElement("GetLastError"));
        final MethodHandle close = down(kernel, "CloseHandle", FunctionDescriptor.of(JAVA_INT, ADDRESS));
        final MethodHandle localFree = down(kernel, "LocalFree", FunctionDescriptor.of(ADDRESS, ADDRESS));
        final MethodHandle stringLength = down(kernel, "lstrlenW", FunctionDescriptor.of(JAVA_INT, ADDRESS));
        final MethodHandle sessionId = down(kernel, "ProcessIdToSessionId", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));
        final MethodHandle openToken = down(security, "OpenProcessToken", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        final MethodHandle tokenInfo = down(security, "GetTokenInformation", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        final MethodHandle sidToString = down(security, "ConvertSidToStringSidW", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        final MethodHandle securityDescriptor = down(security, "ConvertStringSecurityDescriptorToSecurityDescriptorW",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
        MethodHandle down(SymbolLookup library, String name, FunctionDescriptor descriptor) {
            return linker.downcallHandle(library.find(name).orElseThrow(), descriptor);
        }
        void close(MemorySegment handle) throws Throwable { int ignored = (int) close.invokeExact(handle); }
        void free(MemorySegment memory) throws Throwable { MemorySegment ignored = (MemorySegment) localFree.invokeExact(memory); }
    }
}
