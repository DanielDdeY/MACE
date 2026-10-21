package com.mace.infrastructure.nativebridge;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Enlace FFM (Project Panama, JEP 454) con la DLL nativa de MACE ({@code native/include/mace_api.h}).
 *
 * <p>Secuencia de carga:</p>
 * <ol>
 *   <li>Se verifica el archivo y su SHA-256 (rechazo si no coincide con el hash esperado
 *       configurado en {@link NativeLibraryIntegrity}).</li>
 *   <li>Se llama a {@code CP_GetAbiInfo} <strong>antes</strong> de cualquier otra función y se
 *       validan ABI 1.x (x &ge; 4), versión 2, tamaños heredados, {@code pointer_size},
 *       {@code wchar_size} y capacidades ({@link AbiRequirements}). Una DLL anterior, que no
 *       exporta {@code CP_GetAbiInfo}, se rechaza con un mensaje claro.</li>
 *   <li>Solo entonces se enlazan los símbolos V2 y los heredados de solo lectura.</li>
 * </ol>
 *
 * <p>Ciclo de vida (según {@code native/docs/PHASE5.md}): {@link #close()} espera a que
 * terminen las llamadas en curso, impide nuevas y llama una sola vez a
 * {@code CP_ShutdownTelemetry}. La DLL <strong>permanece cargada</strong> hasta que termina el
 * proceso (el proveedor ADLX conserva una sesión por proceso); por eso se enlaza con
 * {@link Arena#global()}. Los tokens de caja negra deben cerrarse antes que esta instancia.</p>
 *
 * <p>Los {@link MethodHandle} son inmutables y seguros para uso concurrente; la memoria fuera
 * del heap de cada llamada la aporta el invocador con un {@code Arena.ofConfined()} propio.
 * Ejecutar la JVM con {@code --enable-native-access=ALL-UNNAMED}.</p>
 */
public final class NativeFunctions implements NativeApi, AutoCloseable {

    static final String SYMBOL_GET_ABI_INFO = "CP_GetAbiInfo";
    static final String SYMBOL_INITIALIZE_TELEMETRY = "CP_InitializeTelemetry";
    static final String SYMBOL_SHUTDOWN_TELEMETRY = "CP_ShutdownTelemetry";
    static final String SYMBOL_POLL_TELEMETRY = "CP_PollTelemetry";
    static final String SYMBOL_ENUMERATE_PROCESSES = "CP_EnumerateUserlandProcesses";
    static final String SYMBOL_ENUMERATE_GPU_ADAPTERS = "CP_EnumerateGpuAdapters";
    static final String SYMBOL_POLL_CPU_V2 = "CP_PollCpuTelemetryV2";
    static final String SYMBOL_POLL_GPU_V2 = "CP_PollGpuTelemetryV2";
    static final String SYMBOL_POLL_MEMORY_V2 = "CP_PollSystemMemoryV2";
    static final String SYMBOL_ENUMERATE_PROCESSES_V2 = "CP_EnumerateUserlandProcessesV2";
    static final String SYMBOL_GET_PROCESS_IDENTITY_V2 = "CP_GetProcessIdentityV2";
    static final String SYMBOL_GET_PROCESS_IMAGE_PATH_V2 = "CP_GetProcessImagePathV2";
    static final String SYMBOL_TERMINATE_PROCESS_V2 = "CP_TerminateProcessV2";
    static final String SYMBOL_SAMPLE_PROCESS_V2 = "CP_SampleProcessV2";
    static final String SYMBOL_CHECK_RESPONSIVENESS_V2 = "CP_CheckProcessResponsivenessV2";
    static final String SYMBOL_BLACKBOX_CREATE_V2 = "CP_BlackboxCreateV2";
    static final String SYMBOL_BLACKBOX_STATS_V2 = "CP_BlackboxGetStatsV2";
    static final String SYMBOL_BLACKBOX_APPEND_V2 = "CP_BlackboxAppendV2";
    static final String SYMBOL_BLACKBOX_READ_V2 = "CP_BlackboxReadV2";
    static final String SYMBOL_BLACKBOX_CLOSE_V2 = "CP_BlackboxCloseV2";

    private static final Linker LINKER = Linker.nativeLinker();

    private final Path libraryPath;
    private final String sha256;
    private final AbiInfo abiInfo;

    private final MethodHandle initializeTelemetryHandle;
    private final MethodHandle shutdownTelemetryHandle;
    private final MethodHandle pollTelemetryHandle;
    private final MethodHandle enumerateProcessesHandle;
    private final MethodHandle enumerateGpuAdaptersHandle;
    private final MethodHandle pollCpuV2Handle;
    private final MethodHandle pollGpuV2Handle;
    private final MethodHandle pollMemoryV2Handle;
    private final MethodHandle enumerateProcessesV2Handle;
    private final MethodHandle getProcessIdentityV2Handle;
    private final MethodHandle getProcessImagePathV2Handle;
    private final MethodHandle terminateProcessV2Handle;
    private final MethodHandle sampleProcessV2Handle;
    private final MethodHandle checkResponsivenessV2Handle;
    private final MethodHandle blackboxCreateV2Handle;
    private final MethodHandle blackboxStatsV2Handle;
    private final MethodHandle blackboxAppendV2Handle;
    private final MethodHandle blackboxReadV2Handle;
    private final MethodHandle blackboxCloseV2Handle;

    /** Lectura: cada llamada nativa. Escritura: inicialización y cierre. */
    private final ReadWriteLock lifecycle = new ReentrantReadWriteLock();
    private boolean telemetryInitialized;
    private boolean closed;

    NativeFunctions(Path libraryPath, String sha256, SymbolLookup lookup) {
        this.libraryPath = libraryPath;
        this.sha256 = sha256;
        this.abiInfo = queryAbiInfo(lookup, libraryPath);
        AbiRequirements.verify(abiInfo, libraryPath.toString());

        this.initializeTelemetryHandle = downcall(lookup, SYMBOL_INITIALIZE_TELEMETRY, FunctionDescriptor.of(JAVA_INT));
        this.shutdownTelemetryHandle = downcall(lookup, SYMBOL_SHUTDOWN_TELEMETRY, FunctionDescriptor.ofVoid());
        this.pollTelemetryHandle = downcall(lookup, SYMBOL_POLL_TELEMETRY, FunctionDescriptor.of(JAVA_INT, ADDRESS));
        this.enumerateProcessesHandle = downcall(lookup, SYMBOL_ENUMERATE_PROCESSES,
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        this.enumerateGpuAdaptersHandle = downcall(lookup, SYMBOL_ENUMERATE_GPU_ADAPTERS,
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        this.pollCpuV2Handle = downcall(lookup, SYMBOL_POLL_CPU_V2, FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
        this.pollGpuV2Handle = downcall(lookup, SYMBOL_POLL_GPU_V2,
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
        this.pollMemoryV2Handle = downcall(lookup, SYMBOL_POLL_MEMORY_V2, FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
        this.enumerateProcessesV2Handle = downcall(lookup, SYMBOL_ENUMERATE_PROCESSES_V2,
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        this.getProcessIdentityV2Handle = downcall(lookup, SYMBOL_GET_PROCESS_IDENTITY_V2,
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        this.getProcessImagePathV2Handle = downcall(lookup, SYMBOL_GET_PROCESS_IMAGE_PATH_V2,
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
        this.terminateProcessV2Handle = downcall(lookup, SYMBOL_TERMINATE_PROCESS_V2,
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        this.sampleProcessV2Handle = downcall(lookup, SYMBOL_SAMPLE_PROCESS_V2,
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
        this.checkResponsivenessV2Handle = downcall(lookup, SYMBOL_CHECK_RESPONSIVENESS_V2,
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
        this.blackboxCreateV2Handle = downcall(lookup, SYMBOL_BLACKBOX_CREATE_V2,
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
        this.blackboxStatsV2Handle = downcall(lookup, SYMBOL_BLACKBOX_STATS_V2,
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT));
        this.blackboxAppendV2Handle = downcall(lookup, SYMBOL_BLACKBOX_APPEND_V2,
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        this.blackboxReadV2Handle = downcall(lookup, SYMBOL_BLACKBOX_READ_V2,
                FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_INT, ADDRESS));
        this.blackboxCloseV2Handle = downcall(lookup, SYMBOL_BLACKBOX_CLOSE_V2, FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
    }

    // ------------------------------------------------------------------
    // Carga
    // ------------------------------------------------------------------

    /**
     * Localiza la DLL con {@link NativeLibraryLocator} y la carga.
     *
     * @throws NativeLibraryException si no se encuentra, no carga, no coincide con el hash
     *                                esperado, su ABI es incompatible o le faltan símbolos
     */
    public static NativeFunctions load() {
        Path path = NativeLibraryLocator.locate()
                .orElseThrow(() -> new NativeLibraryException(NativeLibraryLocator.notFoundMessage()));
        return load(path);
    }

    /** Carga la DLL ubicada en {@code libraryPath} usando el hash esperado configurado, si existe. */
    public static NativeFunctions load(Path libraryPath) {
        return load(libraryPath, NativeLibraryIntegrity.expectedSha256());
    }

    /**
     * Carga la DLL ubicada en {@code libraryPath}.
     *
     * @param expectedSha256 hash obligatorio; vacío para aceptar cualquier DLL con ABI compatible
     */
    @SuppressWarnings("restricted") // SymbolLookup.libraryLookup es un método restringido de FFM
    public static NativeFunctions load(Path libraryPath, Optional<String> expectedSha256) {
        Objects.requireNonNull(libraryPath, "libraryPath no puede ser null");
        Objects.requireNonNull(expectedSha256, "expectedSha256 no puede ser null");
        Path absolute = libraryPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new NativeLibraryException("No existe la biblioteca nativa: " + absolute);
        }
        String sha256 = NativeLibraryIntegrity.verify(absolute, expectedSha256);
        SymbolLookup lookup;
        try {
            // Arena global: la DLL no se descarga hasta que termina el proceso (PHASE5.md).
            lookup = SymbolLookup.libraryLookup(absolute, Arena.global());
        } catch (RuntimeException e) {
            throw new NativeLibraryException(
                    "No se pudo cargar la biblioteca nativa '" + absolute + "': " + e.getMessage(), e);
        }
        return new NativeFunctions(absolute, sha256, lookup);
    }

    /** @return {@code true} si {@link NativeLibraryLocator} encuentra la DLL en disco. */
    public static boolean isAvailable() {
        return NativeLibraryLocator.locate().isPresent();
    }

    /** Ruta absoluta de la DLL cargada. */
    public Path libraryPath() {
        return libraryPath;
    }

    /** SHA-256 de la DLL cargada (para el informe de integración). */
    public String sha256() {
        return sha256;
    }

    @Override
    public AbiInfo abiInfo() {
        return abiInfo;
    }

    @SuppressWarnings("restricted") // Linker.downcallHandle es un método restringido de FFM
    private static MethodHandle downcall(SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) {
        MemorySegment address = lookup.find(symbol).orElseThrow(() -> new NativeLibraryException(
                "La biblioteca nativa no exporta el símbolo '" + symbol
                        + "'. Verifica que se compiló con MACE_EXPORTS y la misma versión de mace_api.h."));
        return LINKER.downcallHandle(address, descriptor);
    }

    /**
     * {@code CP_GetAbiInfo}: primero {@code (NULL, 0, &required)}, que debe devolver
     * {@code MACE_E_BUFFER_TOO_SMALL} y 64; después la consulta con un registro de 64 bytes.
     */
    private static AbiInfo queryAbiInfo(SymbolLookup lookup, Path libraryPath) {
        MemorySegment symbol = lookup.find(SYMBOL_GET_ABI_INFO).orElseThrow(() -> new NativeLibraryException(
                "La biblioteca nativa '" + libraryPath + "' no exporta " + SYMBOL_GET_ABI_INFO
                        + ": es anterior al contrato ABI 1.4 o no es la DLL de MACE. Usa la DLL 1.4.x verificada."));
        @SuppressWarnings("restricted")
        MethodHandle getAbiInfo = LINKER.downcallHandle(symbol,
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment required = arena.allocate(JAVA_INT);
            int probe = (int) getAbiInfo.invokeExact(MemorySegment.NULL, 0, required);
            int requiredSize = required.get(JAVA_INT, 0);
            if (probe != NativeStatus.BUFFER_TOO_SMALL || requiredSize != NativeLayouts.ABI_INFO_SIZE) {
                throw new NativeLibraryException(SYMBOL_GET_ABI_INFO + " respondió " + NativeStatus.name(probe)
                        + " con tamaño " + Integer.toUnsignedString(requiredSize)
                        + " (se esperaba MACE_E_BUFFER_TOO_SMALL y " + NativeLayouts.ABI_INFO_SIZE + ")");
            }
            MemorySegment record = arena.allocate(NativeLayouts.ABI_INFO);
            int result = (int) getAbiInfo.invokeExact(record, (int) NativeLayouts.ABI_INFO_SIZE, required);
            if (result != NativeStatus.OK) {
                throw new NativeLibraryException(SYMBOL_GET_ABI_INFO + " falló: " + NativeStatus.name(result));
            }
            return AbiInfo.read(record);
        } catch (NativeLibraryException e) {
            throw e;
        } catch (Throwable t) {
            throw failure(SYMBOL_GET_ABI_INFO, t);
        }
    }

    // ------------------------------------------------------------------
    // Telemetría
    // ------------------------------------------------------------------

    /**
     * Inicializa la telemetría una sola vez aunque varios adaptadores compartan esta instancia.
     *
     * @return código devuelto por la DLL en la primera inicialización, o 0 si ya estaba lista
     */
    @Override
    public int ensureTelemetryInitialized() {
        lifecycle.writeLock().lock();
        try {
            ensureOpen();
            if (telemetryInitialized) {
                return 0;
            }
            int result = (int) initializeTelemetryHandle.invokeExact();
            telemetryInitialized = true;
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable t) {
            throw failure(SYMBOL_INITIALIZE_TELEMETRY, t);
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    public boolean isTelemetryInitialized() {
        lifecycle.readLock().lock();
        try {
            return telemetryInitialized;
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    /**
     * {@code int CP_PollTelemetry(TelemetryData*)} heredado. Solo diagnóstico: los adaptadores
     * usan V2 y no deben mezclar ambos sondeos (comparten la referencia del uso de CPU).
     */
    public int pollTelemetry(MemorySegment outData) {
        requireCapacity(outData, NativeLayouts.TELEMETRY_DATA_SIZE, "outData");
        return call(SYMBOL_POLL_TELEMETRY, () -> (int) pollTelemetryHandle.invokeExact(outData));
    }

    /** {@code CP_EnumerateUserlandProcesses} heredado; solo diagnóstico (no informa truncado). */
    public int enumerateUserlandProcesses(MemorySegment outBuffer, int maxCount, MemorySegment outCount) {
        if (maxCount <= 0) {
            throw new IllegalArgumentException("maxCount debe ser positivo: " + maxCount);
        }
        requireCapacity(outBuffer, NativeLayouts.PROCESS_DATA_SIZE * maxCount, "outBuffer");
        requireCapacity(outCount, JAVA_INT.byteSize(), "outCount");
        return call(SYMBOL_ENUMERATE_PROCESSES,
                () -> (int) enumerateProcessesHandle.invokeExact(outBuffer, maxCount, outCount));
    }

    @Override
    public int enumerateGpuAdapters(MemorySegment outAdapters, int capacity, MemorySegment outCount) {
        requireCapacity(outAdapters, NativeLayouts.GPU_ADAPTER_INFO_SIZE * Integer.toUnsignedLong(capacity), "outAdapters");
        requireCapacity(outCount, JAVA_INT.byteSize(), "outCount");
        return call(SYMBOL_ENUMERATE_GPU_ADAPTERS,
                () -> (int) enumerateGpuAdaptersHandle.invokeExact(outAdapters, capacity, outCount));
    }

    @Override
    public int pollCpuTelemetryV2(MemorySegment outData, int capacityBytes) {
        requireCapacity(outData, Integer.toUnsignedLong(capacityBytes), "outData");
        return call(SYMBOL_POLL_CPU_V2, () -> (int) pollCpuV2Handle.invokeExact(outData, capacityBytes));
    }

    @Override
    public int pollGpuTelemetryV2(MemorySegment luid, MemorySegment outData, int capacityBytes) {
        requireCapacity(luid, NativeLayouts.LUID_SIZE, "luid");
        requireCapacity(outData, Integer.toUnsignedLong(capacityBytes), "outData");
        return call(SYMBOL_POLL_GPU_V2, () -> (int) pollGpuV2Handle.invokeExact(luid, outData, capacityBytes));
    }

    @Override
    public int pollSystemMemoryV2(MemorySegment outData, int capacityBytes) {
        requireCapacity(outData, Integer.toUnsignedLong(capacityBytes), "outData");
        return call(SYMBOL_POLL_MEMORY_V2, () -> (int) pollMemoryV2Handle.invokeExact(outData, capacityBytes));
    }

    // ------------------------------------------------------------------
    // Procesos V2
    // ------------------------------------------------------------------

    @Override
    public int enumerateUserlandProcessesV2(MemorySegment outEntries, int capacity, MemorySegment outRequiredCount,
                                            MemorySegment outSkippedCount, MemorySegment outWin32Error) {
        requireCapacity(outEntries, NativeLayouts.PROCESS_INFO_SIZE * Integer.toUnsignedLong(capacity), "outEntries");
        requireCapacity(outRequiredCount, JAVA_INT.byteSize(), "outRequiredCount");
        return call(SYMBOL_ENUMERATE_PROCESSES_V2, () -> (int) enumerateProcessesV2Handle.invokeExact(
                outEntries, capacity, outRequiredCount, outSkippedCount, outWin32Error));
    }

    @Override
    public int getProcessIdentityV2(int pid, MemorySegment outIdentity, int capacityBytes, MemorySegment outWin32Error) {
        requireCapacity(outIdentity, Integer.toUnsignedLong(capacityBytes), "outIdentity");
        return call(SYMBOL_GET_PROCESS_IDENTITY_V2,
                () -> (int) getProcessIdentityV2Handle.invokeExact(pid, outIdentity, capacityBytes, outWin32Error));
    }

    @Override
    public int getProcessImagePathV2(MemorySegment identity, MemorySegment outPath, int capacityChars,
                                     MemorySegment outRequiredChars, MemorySegment outWin32Error) {
        requireCapacity(identity, NativeLayouts.PROCESS_IDENTITY_SIZE, "identity");
        requireCapacity(outPath, WideStrings.WCHAR_SIZE * Integer.toUnsignedLong(capacityChars), "outPath");
        requireCapacity(outRequiredChars, JAVA_INT.byteSize(), "outRequiredChars");
        return call(SYMBOL_GET_PROCESS_IMAGE_PATH_V2, () -> (int) getProcessImagePathV2Handle.invokeExact(
                identity, outPath, capacityChars, outRequiredChars, outWin32Error));
    }

    /** Solo debe invocarse como consecuencia directa de una acción manual del usuario. */
    @Override
    public int terminateProcessV2(MemorySegment identity, MemorySegment outWin32Error) {
        requireCapacity(identity, NativeLayouts.PROCESS_IDENTITY_SIZE, "identity");
        return call(SYMBOL_TERMINATE_PROCESS_V2,
                () -> (int) terminateProcessV2Handle.invokeExact(identity, outWin32Error));
    }

    @Override
    public int sampleProcessV2(MemorySegment identity, MemorySegment outSample, int capacityBytes,
                               MemorySegment outWin32Error) {
        requireCapacity(identity, NativeLayouts.PROCESS_IDENTITY_SIZE, "identity");
        requireCapacity(outSample, Integer.toUnsignedLong(capacityBytes), "outSample");
        return call(SYMBOL_SAMPLE_PROCESS_V2,
                () -> (int) sampleProcessV2Handle.invokeExact(identity, outSample, capacityBytes, outWin32Error));
    }

    @Override
    public int checkProcessResponsivenessV2(MemorySegment identity, MemorySegment outResult, int capacityBytes) {
        requireCapacity(identity, NativeLayouts.PROCESS_IDENTITY_SIZE, "identity");
        requireCapacity(outResult, Integer.toUnsignedLong(capacityBytes), "outResult");
        return call(SYMBOL_CHECK_RESPONSIVENESS_V2,
                () -> (int) checkResponsivenessV2Handle.invokeExact(identity, outResult, capacityBytes));
    }

    // ------------------------------------------------------------------
    // Caja negra V2 (token opaco)
    // ------------------------------------------------------------------

    @Override
    public int blackboxCreateV2(MemorySegment mappingName, int capacityRecords, MemorySegment outHandle,
                                MemorySegment outWin32Error) {
        requireCapacity(outHandle, JAVA_LONG.byteSize(), "outHandle");
        return call(SYMBOL_BLACKBOX_CREATE_V2,
                () -> (int) blackboxCreateV2Handle.invokeExact(mappingName, capacityRecords, outHandle, outWin32Error));
    }

    @Override
    public int blackboxGetStatsV2(long handle, MemorySegment outHeader, int capacityBytes) {
        requireCapacity(outHeader, Integer.toUnsignedLong(capacityBytes), "outHeader");
        return call(SYMBOL_BLACKBOX_STATS_V2,
                () -> (int) blackboxStatsV2Handle.invokeExact(handle, outHeader, capacityBytes));
    }

    @Override
    public int blackboxAppendV2(long handle, int recordType, MemorySegment payload, int payloadSize,
                                MemorySegment outSequence) {
        requireCapacity(payload, Integer.toUnsignedLong(payloadSize), "payload");
        return call(SYMBOL_BLACKBOX_APPEND_V2,
                () -> (int) blackboxAppendV2Handle.invokeExact(handle, recordType, payload, payloadSize, outSequence));
    }

    @Override
    public int blackboxReadV2(long handle, long fromSequence, MemorySegment outRecords, int capacityRecords,
                              MemorySegment outRequiredCount) {
        requireCapacity(outRecords, NativeLayouts.BLACKBOX_RECORD_V2_SIZE * Integer.toUnsignedLong(capacityRecords),
                "outRecords");
        requireCapacity(outRequiredCount, JAVA_INT.byteSize(), "outRequiredCount");
        return call(SYMBOL_BLACKBOX_READ_V2, () -> (int) blackboxReadV2Handle.invokeExact(
                handle, fromSequence, outRecords, capacityRecords, outRequiredCount));
    }

    @Override
    public int blackboxCloseV2(long handle) {
        return call(SYMBOL_BLACKBOX_CLOSE_V2, () -> (int) blackboxCloseV2Handle.invokeExact(handle));
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    public boolean isClosed() {
        lifecycle.readLock().lock();
        try {
            return closed;
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    /**
     * Espera a las llamadas en curso, impide nuevas y llama una sola vez a
     * {@code CP_ShutdownTelemetry} (si se inicializó). Idempotente. La DLL sigue cargada hasta
     * el fin del proceso; ninguna función puede invocarse sobre esta instancia después.
     */
    @Override
    public void close() {
        lifecycle.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            if (telemetryInitialized) {
                telemetryInitialized = false;
                shutdownTelemetryHandle.invokeExact();
            }
        } catch (Throwable ignored) {
            // No hay recuperación posible del apagado; la instancia queda cerrada igualmente.
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    @FunctionalInterface
    private interface NativeCall {
        int invoke() throws Throwable;
    }

    private int call(String symbol, NativeCall nativeCall) {
        lifecycle.readLock().lock();
        try {
            ensureOpen();
            return nativeCall.invoke();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Throwable t) {
            throw failure(symbol, t);
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("NativeFunctions ya fue cerrado; la telemetría nativa está apagada");
        }
    }

    private static void requireCapacity(MemorySegment segment, long minimumBytes, String name) {
        Objects.requireNonNull(segment, name + " no puede ser null");
        if (segment.address() == 0L && minimumBytes == 0L) {
            return; // NULL con capacidad 0: consulta de tamaño requerido.
        }
        if (segment.byteSize() < minimumBytes) {
            throw new IllegalArgumentException(
                    name + " necesita al menos " + minimumBytes + " bytes, tiene " + segment.byteSize());
        }
    }

    private static NativeLibraryException failure(String symbol, Throwable cause) {
        return new NativeLibraryException("Fallo al invocar " + symbol + ": " + cause, cause);
    }
}
