package com.mace.infrastructure.nativebridge;

import org.junit.jupiter.api.Test;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real FFM downcalls into deterministic upcall stubs; no DLL or hardware needed. */
class NativeLifecycleTest {
    static final class Stub {
        final AtomicInteger inits=new AtomicInteger(),shutdowns=new AtomicInteger();
        final CountDownLatch stopping=new CountDownLatch(1),release=new CountDownLatch(1);
        boolean slowShutdown;
        public int init() { return inits.incrementAndGet()==1 ? NativeStatus.BUSY : NativeStatus.OK; }
        public void shutdown() {
            shutdowns.incrementAndGet(); stopping.countDown();
            if (slowShutdown) try { release.await(5,TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        public int abi(MemorySegment out,int capacity,MemorySegment required) {
            required.reinterpret(4).set(JAVA_INT,0,64);
            if (capacity<64) return NativeStatus.BUFFER_TOO_SMALL;
            out=out.reinterpret(64); out.fill((byte)0);
            int[] prefix={64,2,1,4,1584,28,16,2,8,0};
            for (int i=0;i<prefix.length;i++) out.set(JAVA_INT,i*4L,prefix[i]);
            out.set(JAVA_LONG,40,AbiRequirements.REQUIRED_CAPABILITIES);
            out.set(JAVA_INT,48,1); out.set(JAVA_INT,52,4); out.set(JAVA_INT,56,2);
            return 0;
        }
    }
    private NativeFunctions functions(Stub stub,Arena arena) throws Exception {
        var linker=Linker.nativeLinker(); var lookup=MethodHandles.lookup();
        MemorySegment init=linker.upcallStub(lookup.findVirtual(Stub.class,"init",MethodType.methodType(int.class)).bindTo(stub),
            FunctionDescriptor.of(JAVA_INT),arena);
        MemorySegment shutdown=linker.upcallStub(lookup.findVirtual(Stub.class,"shutdown",MethodType.methodType(void.class)).bindTo(stub),
            FunctionDescriptor.ofVoid(),arena);
        MemorySegment abi=linker.upcallStub(lookup.findVirtual(Stub.class,"abi",
            MethodType.methodType(int.class,MemorySegment.class,int.class,MemorySegment.class)).bindTo(stub),
            FunctionDescriptor.of(JAVA_INT,ADDRESS,JAVA_INT,ADDRESS),arena);
        SymbolLookup symbols=name->Optional.of(switch (name) {
            case "CP_GetAbiInfo" -> abi; case "CP_ShutdownTelemetry" -> shutdown; default -> init;
        });
        return new NativeFunctions(Path.of("fixture-lifecycle"),"TEST",symbols);
    }
    @Test void busyNoMarcaInicializadoYSoloElExitoHaceShutdown() throws Exception {
        try (Arena arena=Arena.ofShared()) {
            Stub stub=new Stub(); NativeFunctions functions=functions(stub,arena);
            assertEquals(NativeStatus.BUSY,functions.ensureTelemetryInitialized());
            assertFalse(functions.isTelemetryInitialized());
            assertEquals(0,functions.ensureTelemetryInitialized());
            assertTrue(functions.isTelemetryInitialized());
            assertEquals(0,functions.ensureTelemetryInitialized()); assertEquals(2,stub.inits.get());
            functions.close(); functions.close(); assertEquals(1,stub.shutdowns.get());
        }
    }
    @Test void cerrarDespuesDeBusyNoApagaElRuntimeDeOtroPropietario() throws Exception {
        try (Arena arena=Arena.ofShared()) {
            Stub stub=new Stub(); NativeFunctions functions=functions(stub,arena);
            assertEquals(NativeStatus.BUSY,functions.ensureTelemetryInitialized());
            functions.close(); assertEquals(0,stub.shutdowns.get());
        }
    }
    @Test void initNoEsperaElCierreEnCurso() throws Exception {
        try (Arena arena=Arena.ofShared()) {
            Stub stub=new Stub(); stub.slowShutdown=true; NativeFunctions functions=functions(stub,arena);
            functions.ensureTelemetryInitialized(); functions.ensureTelemetryInitialized();
            Thread closing=Thread.ofPlatform().start(functions::close);
            try {
                assertTrue(stub.stopping.await(2,TimeUnit.SECONDS));
                assertTimeoutPreemptively(java.time.Duration.ofMillis(250),
                    ()->assertEquals(NativeStatus.BUSY,functions.ensureTelemetryInitialized()));
            } finally { stub.release.countDown(); closing.join(3000); }
            assertFalse(closing.isAlive()); assertTrue(functions.isClosed());
        }
    }
}
