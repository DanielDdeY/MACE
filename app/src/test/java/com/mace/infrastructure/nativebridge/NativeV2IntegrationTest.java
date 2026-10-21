package com.mace.infrastructure.nativebridge;

import com.mace.domain.model.CpuReading;
import com.mace.domain.model.GpuAdapter;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.ImagePath;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.ProcessEntry;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessInventory;
import com.mace.domain.model.ProcessQueryStatus;
import com.mace.domain.model.ProcessSampleResult;
import com.mace.domain.model.ResponsivenessOutcome;
import com.mace.domain.model.TerminationOutcome;
import com.mace.infrastructure.telemetry.BlackboxRing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integración con la DLL real ({@code MACE_NATIVE_LIB} o {@code -Dmace.native.lib}). Se omite si no
 * hay DLL o no es Windows: los demás tests cubren la lógica sin DLL. Usar la DLL del paquete
 * verificado por Dev 1, no una copia residual de {@code app/lib/}.
 *
 * <p>La única terminación real se hace sobre un proceso auxiliar que crea esta misma prueba.</p>
 */
class NativeV2IntegrationTest {

    private static final byte SENTINEL = (byte) 0xA5;
    private static Path dll;

    private NativeFunctions functions;

    @BeforeAll
    static void localizarDll() {
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"), "Requiere Windows");
        dll = NativeLibraryLocator.locate().orElse(null);
        assumeTrue(dll != null, "DLL no disponible: define MACE_NATIVE_LIB para ejecutar la integración");
        System.out.println("[MACE IT] DLL " + dll + " SHA-256 " + NativeLibraryIntegrity.sha256(dll));
    }

    @BeforeEach
    void cargar() {
        functions = NativeFunctions.load(dll, NativeLibraryIntegrity.expectedSha256());
    }

    @AfterEach
    void cerrar() {
        functions.close();
    }

    @Test
    void elAbiEsElDelContrato() {
        AbiInfo abi = functions.abiInfo();
        assertEquals(1, abi.abiMajor());
        assertTrue(abi.abiMinor() >= 4);
        assertEquals(2, abi.version());
        assertEquals(64, abi.structSize());
        assertEquals(1584, abi.processDataSize());
        assertEquals(28, abi.telemetryDataSize());
        assertEquals(16, abi.blackboxHeaderSize());
        System.out.println("[MACE IT] ABI " + abi.abiVersion() + ", DLL " + abi.buildVersion()
                + ", capacidades 0x" + Long.toHexString(abi.capabilities()));
    }

    @Test
    @SuppressWarnings("restricted")
    void cpGetAbiInfoNoEscribeConBufferCorto() throws Throwable {
        MethodHandle getAbiInfo = Linker.nativeLinker().downcallHandle(
                SymbolLookup.libraryLookup(dll, Arena.global()).find("CP_GetAbiInfo").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment record = filled(arena, 64);
            MemorySegment required = arena.allocate(JAVA_INT);
            int status = (int) getAbiInfo.invokeExact(record, 63, required);
            assertEquals(NativeStatus.BUFFER_TOO_SMALL, status);
            assertEquals(64, required.get(JAVA_INT, 0));
            assertUntouched(record);
        }
    }

    @Test
    void sensoresV2ConEstadosPorMetricaYCadaGpu() throws InterruptedException {
        FfmSensorsAdapter sensors = new FfmSensorsAdapter(functions);
        HardwareSnapshot first = sensors.fetchHardwareSnapshot();
        Thread.sleep(1100);
        HardwareSnapshot second = sensors.fetchHardwareSnapshot();

        CpuReading cpu = second.cpu();
        System.out.println("[MACE IT] CPU " + cpu.vendor() + " proveedor " + cpu.provider() + " temp "
                + cpu.temperatureCelsius() + " potencia " + cpu.powerWatts() + " uso " + cpu.usagePercent());
        assertEquals(MetricState.VALID, cpu.usagePercent().state(), "tras un intervalo el uso de Windows es válido");
        assertTrue(cpu.usagePercent().value() >= 0 && cpu.usagePercent().value() <= 100);
        assertTrue(first.cpu().usagePercent().state() == MetricState.WARMING_UP
                || first.cpu().usagePercent().isValid());
        assertTrue(second.memory().totalBytes().isValid());
        assertTrue(second.memory().availableBytes().value() <= second.memory().totalBytes().value());

        List<GpuAdapter> adapters = sensors.listGpuAdapters();
        assertEquals(adapters.size(), second.gpus().size());
        for (GpuReading gpu : second.gpus()) {
            System.out.println("[MACE IT] " + gpu.adapter().displayName() + " " + gpu.adapterId() + " proveedor "
                    + gpu.provider() + " ámbito " + gpu.powerScope() + " temp " + gpu.temperatureCelsius()
                    + " rpm " + gpu.fanRpm() + " vram " + gpu.videoMemoryUsedBytes());
            assertTrue(adapters.stream().anyMatch(a -> a.id().equals(gpu.adapterId())));
        }
    }

    @Test
    void losSondeosV2NoEscribenConBufferCorto() {
        functions.ensureTelemetryInitialized();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cpu = filled(arena, 56);
            assertEquals(NativeStatus.BUFFER_TOO_SMALL, functions.pollCpuTelemetryV2(cpu, 55));
            assertUntouched(cpu);

            MemorySegment memory = filled(arena, 48);
            assertEquals(NativeStatus.BUFFER_TOO_SMALL, functions.pollSystemMemoryV2(memory, 47));
            assertUntouched(memory);

            List<GpuAdapter> adapters = FfmSensorsAdapter.enumerateAdapters(functions);
            if (!adapters.isEmpty()) {
                MemorySegment luid = arena.allocate(NativeLayouts.ADAPTER_LUID);
                V2Records.writeLuid(luid, 0, adapters.get(0).id());
                MemorySegment gpu = filled(arena, 104);
                assertEquals(NativeStatus.BUFFER_TOO_SMALL, functions.pollGpuTelemetryV2(luid, gpu, 103));
                assertUntouched(gpu);

                MemorySegment list = filled(arena, NativeLayouts.GPU_ADAPTER_INFO_SIZE * adapters.size());
                MemorySegment count = arena.allocate(JAVA_INT);
                assertEquals(NativeStatus.BUFFER_TOO_SMALL,
                        functions.enumerateGpuAdapters(list, adapters.size() - 1, count));
                assertUntouched(list);
            }

            MemorySegment processes = filled(arena, NativeLayouts.PROCESS_INFO_SIZE);
            MemorySegment required = arena.allocate(JAVA_INT);
            assertEquals(NativeStatus.BUFFER_TOO_SMALL, functions.enumerateUserlandProcessesV2(
                    processes, 1, required, MemorySegment.NULL, MemorySegment.NULL));
            assertTrue(required.get(JAVA_INT, 0) > 1);
            assertUntouched(processes);
        }
    }

    @Test
    void inventarioV2ConIdentidadCompletaYSinProcesosDelSistema() {
        ProcessInventory inventory = new FfmProcessAdapter(functions).enumerateProcesses();

        assertFalse(inventory.processes().isEmpty());
        long ownPid = ProcessHandle.current().pid();
        for (ProcessEntry entry : inventory.processes()) {
            assertTrue(entry.identity().creationTime100nsUtc() != 0);
            assertTrue(entry.pid().value() != ownPid, "la DLL excluye el propio proceso");
            entry.imagePath().asOptional().ifPresent(path ->
                    assertFalse(path.toLowerCase(Locale.ROOT).contains("\\windows\\system32\\"), path));
        }
        System.out.println("[MACE IT] " + inventory.processes().size() + " procesos, " + inventory.skippedCount() + " omitidos");
    }

    @Test
    void procesoAuxiliarIdentidadRutaMuestraVentanasYTerminacionManual(@TempDir Path dir) throws Exception {
        Process helper = startHelper(dir);
        try {
            FfmProcessAdapter processes = new FfmProcessAdapter(functions);
            ProcessIdentity identity = identityOf(helper.pid());
            ProcessIdentity wrong = ProcessIdentity.of(identity.pid().value(), identity.creationTime100nsUtc() + 1);
            Path javaExe = Path.of(ProcessHandle.current().info().command().orElseThrow());

            ImagePath path = processes.imagePath(identity);
            assertTrue(path.isAvailable(), "ruta: " + path);
            assertEquals(javaExe.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT),
                    path.value().toLowerCase(Locale.ROOT));
            assertEquals(ProcessQueryStatus.PID_REUSED, processes.imagePath(wrong).status());

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment id = arena.allocate(NativeLayouts.PROCESS_IDENTITY);
                V2Records.writeIdentity(id, 0, identity);
                MemorySegment shortPath = filled(arena, 4);
                MemorySegment requiredChars = arena.allocate(JAVA_INT);
                assertEquals(NativeStatus.BUFFER_TOO_SMALL,
                        functions.getProcessImagePathV2(id, shortPath, 2, requiredChars, MemorySegment.NULL));
                assertEquals(path.value().length() + 1, requiredChars.get(JAVA_INT, 0));
                assertUntouched(shortPath);
            }

            ProcessSampleResult sample = processes.sample(identity);
            assertEquals(ProcessQueryStatus.OK, sample.status());
            assertTrue(sample.sample().workingSet().isValid());
            assertEquals(ProcessQueryStatus.PID_REUSED, processes.sample(wrong).status());

            ResponsivenessOutcome windows = processes.checkResponsiveness(identity).outcome();
            // EnumWindows puede fallar en una sesión sin escritorio interactivo.
            assertTrue(windows == ResponsivenessOutcome.NO_WINDOW || windows == ResponsivenessOutcome.RESPONDING
                    || windows == ResponsivenessOutcome.ERROR, windows.name());
            assertEquals(ResponsivenessOutcome.PID_REUSED, processes.checkResponsiveness(wrong).outcome());

            // Identidad incorrecta: la DLL rechaza y el proceso sigue vivo.
            assertEquals(TerminationOutcome.PID_REUSED, processes.terminate(wrong).outcome());
            assertTrue(helper.isAlive());

            // Solo el auxiliar creado por esta prueba se termina, con su identidad exacta.
            assertEquals(helper.pid(), identity.pid().value());
            assertEquals(TerminationOutcome.TERMINATED, processes.terminate(identity).outcome());
            assertTrue(helper.waitFor(10, TimeUnit.SECONDS));
            assertFalse(helper.isAlive());
            // Tras salir, Windows puede denegar OpenProcess sobre el PID ya cerrado. El puente
            // conserva ese diagnóstico; no debe afirmar PROCESS_EXITED sin respuesta de la DLL.
            TerminationOutcome again = processes.terminate(identity).outcome();
            assertTrue(again == TerminationOutcome.PROCESS_EXITED || again == TerminationOutcome.ACCESS_DENIED
                    || again == TerminationOutcome.PID_REUSED, again.name());
            ProcessQueryStatus after = processes.sample(identity).status();
            for (int retry = 0; retry < 20 && after == ProcessQueryStatus.OK; retry++) {
                Thread.sleep(50);
                after = processes.sample(identity).status();
            }
            assertTrue(after == ProcessQueryStatus.PROCESS_EXITED || after == ProcessQueryStatus.ACCESS_DENIED
                    || after == ProcessQueryStatus.PID_REUSED, after.name());
        } finally {
            helper.destroyForcibly();
        }
    }

    @Test
    void losPidReservadosSeRechazanComoProtegidos() {
        FfmProcessAdapter processes = new FfmProcessAdapter(functions);

        assertEquals(TerminationOutcome.PROTECTED_PROCESS, processes.terminate(ProcessIdentity.of(4, 1)).outcome());
    }

    @Test
    void tokensDeCajaNegraLecturaDesbordamientoYDobleCierre() {
        functions.ensureTelemetryInitialized();
        NativeBlackboxRing ring = NativeBlackboxRing.create(functions, 4);
        long handle = ring.handle();
        CpuReading cpu = new FfmSensorsAdapter(functions).fetchHardwareSnapshot().cpu();
        byte[] payload = V2Records.encodeCpu(cpu);
        for (int i = 1; i <= 6; i++) {
            assertEquals(i, ring.append(BlackboxRing.RECORD_CPU, payload));
        }

        BlackboxRing.RingStats stats = ring.stats();
        assertEquals(4, stats.capacityRecords());
        assertEquals(4, stats.recordCount());
        assertEquals(7, stats.nextSequence());
        assertEquals(2, stats.overwrittenRecords());
        List<BlackboxRing.RingRecord> all = ring.read(0);
        assertEquals(List.of(3L, 4L, 5L, 6L), all.stream().map(BlackboxRing.RingRecord::sequence).toList());
        assertEquals(3L, ring.read(1).get(0).sequence(), "una secuencia sobrescrita empieza en la más antigua");
        assertEquals(cpu, V2Records.decodeCpu(MemorySegment.ofArray(all.get(0).payload())));

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(payload.length, 8);
            MemorySegment.copy(payload, 0, data, JAVA_BYTE, 0, payload.length);
            MemorySegment sequence = arena.allocate(JAVA_LONG);
            assertEquals(NativeStatus.INVALID_ARGUMENT,
                    functions.blackboxAppendV2(handle, BlackboxRing.RECORD_CPU, data, payload.length - 8, sequence));
            data.set(JAVA_INT, 4, 3);
            assertEquals(NativeStatus.UNSUPPORTED_VERSION,
                    functions.blackboxAppendV2(handle, BlackboxRing.RECORD_CPU, data, payload.length, sequence));

            MemorySegment records = filled(arena, NativeLayouts.BLACKBOX_RECORD_V2_SIZE * 2);
            MemorySegment required = arena.allocate(JAVA_INT);
            assertEquals(NativeStatus.BUFFER_TOO_SMALL, functions.blackboxReadV2(handle, 0, records, 2, required));
            assertEquals(4, required.get(JAVA_INT, 0));
            assertUntouched(records);
        }

        ring.close();
        assertEquals(NativeStatus.INVALID_ARGUMENT, functions.blackboxCloseV2(handle), "doble cierre");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment header = arena.allocate(NativeLayouts.BLACKBOX_HEADER_V2);
            assertEquals(NativeStatus.INVALID_ARGUMENT, functions.blackboxGetStatsV2(handle, header, 48));
        }
    }

    @Test
    void cerrarMientrasHayLectoresNoRompeLaJvm() throws Exception {
        FfmSensorsAdapter sensors = new FfmSensorsAdapter(functions);
        AtomicInteger polls = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch running = new CountDownLatch(4);
        List<Thread> readers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Thread reader = new Thread(() -> {
                running.countDown();
                try {
                    while (true) {
                        sensors.fetchHardwareSnapshot();
                        polls.incrementAndGet();
                    }
                } catch (IllegalStateException closed) {
                    rejected.incrementAndGet();
                }
            });
            reader.start();
            readers.add(reader);
        }
        running.await();
        Thread.sleep(300);

        functions.close();

        for (Thread reader : readers) {
            reader.join(Duration.ofSeconds(10).toMillis());
            assertFalse(reader.isAlive());
        }
        assertTrue(polls.get() > 0);
        assertEquals(4, rejected.get(), "tras el cierre ninguna llamada llega a la DLL");
        assertThrows(IllegalStateException.class, () -> functions.ensureTelemetryInitialized());
    }

    @Test
    void cicloDeReinicioCargaInicializaYApagaDeNuevo() {
        CpuReading before = new FfmSensorsAdapter(functions).fetchHardwareSnapshot().cpu();
        functions.close();
        assertTrue(functions.isClosed());

        try (NativeFunctions again = NativeFunctions.load(dll, Optional.empty())) {
            CpuReading after = new FfmSensorsAdapter(again).fetchHardwareSnapshot().cpu();
            assertEquals(before.vendor(), after.vendor());
            assertTrue(after.usagePercent().state() == MetricState.WARMING_UP || after.usagePercent().isValid(),
                    "el reinicio toma nuevas referencias de uso");
        }
    }

    @Test
    void unaDllHeredadaSeRechazaConMensajeClaro() {
        String legacy = System.getProperty("mace.it.legacyDll", System.getenv("MACE_LEGACY_DLL"));
        assumeTrue(legacy != null && Files.isRegularFile(Path.of(legacy)), "Define MACE_LEGACY_DLL para probarlo");

        NativeLibraryException error = assertThrows(NativeLibraryException.class,
                () -> NativeFunctions.load(Path.of(legacy), Optional.empty()));
        assertTrue(error.getMessage().contains("CP_GetAbiInfo"), error.getMessage());
    }

    // ------------------------------------------------------------------

    private ProcessIdentity identityOf(long pid) throws InterruptedException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(NativeLayouts.PROCESS_IDENTITY);
            MemorySegment win32 = arena.allocate(JAVA_INT);
            for (int attempt = 0; attempt < 50; attempt++) {
                if (functions.getProcessIdentityV2((int) pid, out, 16, win32) == NativeStatus.OK) {
                    ProcessIdentity identity = V2Records.readIdentity(out, 0);
                    assertNotNull(identity);
                    return identity;
                }
                Thread.sleep(100);
            }
        }
        throw new AssertionError("No se pudo leer la identidad del auxiliar " + pid);
    }

    private static Process startHelper(Path dir) throws IOException {
        Path source = dir.resolve("MaceHelper.java");
        Files.writeString(source, "public class MaceHelper { public static void main(String[] a) throws Exception {"
                + " Thread.sleep(120_000); } }", StandardCharsets.US_ASCII);
        String java = ProcessHandle.current().info().command().orElseThrow();
        return new ProcessBuilder(java, source.toString()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    }

    private static MemorySegment filled(Arena arena, long size) {
        MemorySegment segment = arena.allocate(size, 8);
        segment.fill(SENTINEL);
        return segment;
    }

    private static void assertUntouched(MemorySegment segment) {
        for (long i = 0; i < segment.byteSize(); i++) {
            if (segment.get(JAVA_BYTE, i) != SENTINEL) {
                throw new AssertionError("La DLL escribió el byte " + i + " de un buffer corto");
            }
        }
    }
}
