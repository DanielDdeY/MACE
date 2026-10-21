package com.mace.infrastructure.nativebridge;

import com.mace.domain.model.ImagePath;
import com.mace.domain.model.LongMetricReading;
import com.mace.domain.model.MemoryMetrics;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.ProcessEntry;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessInventory;
import com.mace.domain.model.ProcessQueryStatus;
import com.mace.domain.model.ProcessSample;
import com.mace.domain.model.ProcessSampleResult;
import com.mace.domain.model.ResponsivenessOutcome;
import com.mace.domain.model.ResponsivenessResult;
import com.mace.domain.model.TerminationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adaptador de procesos V2 contra la semántica simulada de la DLL; no requiere Windows. */
class FfmProcessAdapterTest {

    private static final long MIB = 1024L * 1024L;
    private static final ProcessIdentity CHROME = ProcessIdentity.of(4120, 133_700_000_000_000_001L);
    private static final ProcessIdentity TEAMS = ProcessIdentity.of(1500, 133_700_000_000_000_002L);
    private static final ProcessIdentity TOOL = ProcessIdentity.of(1900, 133_700_000_000_000_003L);

    private FakeNativeApi api;
    private FfmProcessAdapter adapter;

    @BeforeEach
    void setUp() {
        api = new FakeNativeApi();
        api.processes.add(entry(CHROME, "chrome.exe", new MemoryMetrics(512 * MIB, 380 * MIB)));
        // Private Bytes por encima del Working Set es legítimo.
        api.processes.add(entry(TEAMS, "ms-teams.exe", new MemoryMetrics(300 * MIB, 450 * MIB)));
        api.processes.add(entry(TOOL, "elevated-tool.exe", MemoryMetrics.unavailable(MetricState.ACCESS_DENIED)));
        api.paths.put(CHROME, "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
        api.paths.put(TEAMS, "C:\\Program Files\\WindowsApps\\ms-teams.exe");
        api.pathStatus.put(TOOL, NativeStatus.ACCESS_DENIED);
        api.skipped = 4;
        adapter = new FfmProcessAdapter(api, Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC), () -> 42L);
    }

    private static ProcessEntry entry(ProcessIdentity identity, String name, MemoryMetrics memory) {
        return new ProcessEntry(identity, name, ImagePath.unavailable(ProcessQueryStatus.ERROR), memory);
    }

    @Test
    void decodificaElInventarioConIdentidadMemoriaYRuta() {
        ProcessInventory inventory = adapter.enumerateProcesses();

        assertEquals(3, inventory.processes().size());
        assertEquals(4, inventory.skippedCount());
        assertEquals(42L, inventory.monotonicNanos());
        ProcessEntry chrome = inventory.find(CHROME).orElseThrow();
        assertEquals("chrome.exe", chrome.name());
        assertEquals("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe", chrome.imagePath().value());
        assertEquals(512 * MIB, chrome.memory().workingSetReading().value());
        ProcessEntry teams = inventory.find(TEAMS).orElseThrow();
        assertTrue(teams.memory().privateBytes() > teams.memory().workingSetBytes());
    }

    @Test
    void laRutaNoDisponibleConservaElMotivoYLaMemoriaSuEstado() {
        ProcessEntry tool = adapter.enumerateProcesses().find(TOOL).orElseThrow();

        assertFalse(tool.imagePath().isAvailable());
        assertEquals(ProcessQueryStatus.ACCESS_DENIED, tool.imagePath().status());
        assertEquals(MetricState.ACCESS_DENIED, tool.memory().workingSetReading().state());
    }

    @Test
    void procesoFinalizadoOPidReutilizadoAlConsultarLaRutaSeMuestranComoTales() {
        api.pathStatus.put(CHROME, NativeStatus.PROCESS_EXITED);
        api.pathStatus.put(TEAMS, NativeStatus.PID_REUSED);

        ProcessInventory inventory = adapter.enumerateProcesses();

        assertEquals(ProcessQueryStatus.PROCESS_EXITED, inventory.find(CHROME).orElseThrow().imagePath().status());
        assertEquals(ProcessQueryStatus.PID_REUSED, inventory.find(TEAMS).orElseThrow().imagePath().status());
    }

    @Test
    void reintentaConUnBufferMayorSiAparecenProcesosEntreConsultaYLectura() {
        api.processGrowthOnRead = 2;
        api.processExtraWhenGrowing = 1_000;

        ProcessInventory inventory = adapter.enumerateProcesses();

        assertEquals(3, inventory.processes().size());
        assertEquals(3, api.processEnumerationCalls);
    }

    @Test
    void rechazaInventariosPorEncimaDelLimiteSeguro() {
        api.processGrowthOnRead = 1;
        api.processExtraWhenGrowing = FfmProcessAdapter.MAX_PROCESSES + 1;

        NativeLibraryException error = assertThrows(NativeLibraryException.class, adapter::enumerateProcesses);
        assertTrue(error.getMessage().contains("límite"));
    }

    @Test
    void leeRutasUtf16LargasSinTruncar() {
        String longPath = "C:\\Datos\\" + "carpeta-ñandú-日本語\\".repeat(30) + "app-😀.exe";
        assertTrue(longPath.length() > FfmProcessAdapter.INITIAL_PATH_CHARS);
        api.paths.put(CHROME, longPath);

        ImagePath path = adapter.enumerateProcesses().find(CHROME).orElseThrow().imagePath();

        assertEquals(longPath, path.value());
    }

    @Test
    void laRutaSeConsultaUnaVezPorIdentidadYSeOlvidaAlSalirDelInventario() {
        adapter.enumerateProcesses();
        int callsAfterFirst = api.pathCalls;
        adapter.enumerateProcesses();
        assertEquals(callsAfterFirst, api.pathCalls, "caché por identidad completa");

        api.processes.removeIf(entry -> entry.identity().equals(CHROME));
        ProcessIdentity reused = ProcessIdentity.of(CHROME.pid().value(), CHROME.creationTime100nsUtc() + 1);
        api.processes.add(entry(reused, "otro.exe", new MemoryMetrics(MIB, MIB)));
        api.paths.put(reused, "C:\\otro.exe");

        ProcessInventory inventory = adapter.enumerateProcesses();

        assertEquals("C:\\otro.exe", inventory.find(reused).orElseThrow().imagePath().value(),
                "un PID reutilizado no hereda la ruta del proceso anterior");
    }

    @Test
    void terminaConLaIdentidadExactaYReservadoACero() {
        adapter.terminate(CHROME);

        ByteBuffer bytes = ByteBuffer.wrap(api.terminateIdentityBytes.get(0)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(4120, bytes.getInt(0));
        assertEquals(0, bytes.getInt(4), "reserved");
        assertEquals(CHROME.creationTime100nsUtc(), bytes.getLong(8));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "0, TERMINATED",
            "-12, PROTECTED_PROCESS",
            "-5, ACCESS_DENIED",
            "-6, PROCESS_EXITED",
            "-7, PID_REUSED",
            "-1, INVALID_IDENTITY",
            "-11, FAILED"
    })
    void cadaEstadoDeTerminacionSeConservaPorSeparado(int status, TerminationOutcome expected) {
        api.terminateStatus.put(CHROME, status);

        assertEquals(expected, adapter.terminate(CHROME).outcome());
    }

    @Test
    void unPidFueraDeUint32NoLlegaALaDll() {
        ProcessIdentity huge = ProcessIdentity.of(0x1_0000_0000L, 5);

        assertEquals(TerminationOutcome.INVALID_IDENTITY, adapter.terminate(huge).outcome());
        assertTrue(api.terminateIdentityBytes.isEmpty());
    }

    @Test
    void muestreaPorIdentidadYPropagaLosMotivosDeFallo() {
        api.samples.put(CHROME, new ProcessSample(CHROME, 99L, LongMetricReading.valid(10 * MIB), LongMetricReading.valid(20 * MIB)));
        api.sampleStatus.put(TEAMS, NativeStatus.PID_REUSED);
        api.sampleStatus.put(TOOL, NativeStatus.ACCESS_DENIED);

        ProcessSampleResult ok = adapter.sample(CHROME);
        assertEquals(20 * MIB, ok.sample().privateBytes().value());
        assertEquals(ProcessQueryStatus.PID_REUSED, adapter.sample(TEAMS).status());
        ProcessSampleResult denied = adapter.sample(TOOL);
        assertEquals(ProcessQueryStatus.ACCESS_DENIED, denied.status());
        assertEquals(5, denied.win32Error());
    }

    @Test
    void unaMuestraDeOtraIdentidadNoSeAcepta() {
        ProcessIdentity other = ProcessIdentity.of(CHROME.pid().value(), 1);
        api.samples.put(CHROME, new ProcessSample(other, 1L, LongMetricReading.valid(1), LongMetricReading.valid(1)));

        assertEquals(ProcessQueryStatus.PID_REUSED, adapter.sample(CHROME).status());
    }

    @Test
    void distingueTodosLosResultadosDeVentanas() {
        api.responsiveness.put(CHROME, new int[]{0, 3, 0, 0});
        api.responsiveness.put(TEAMS, new int[]{1, 2, 1, 0});
        api.responsiveness.put(TOOL, new int[]{2, 0, 0, 0});
        ProcessIdentity exited = ProcessIdentity.of(77, 1);
        api.responsiveness.put(exited, new int[]{3, 0, 0, 0});
        ProcessIdentity error = ProcessIdentity.of(78, 1);
        api.responsiveness.put(error, new int[]{4, 0, 0, 1400});
        ProcessIdentity reused = ProcessIdentity.of(79, 1);
        api.responsivenessStatus.put(reused, NativeStatus.PID_REUSED);

        assertEquals(ResponsivenessOutcome.RESPONDING, adapter.checkResponsiveness(CHROME).outcome());
        ResponsivenessResult hung = adapter.checkResponsiveness(TEAMS);
        assertEquals(ResponsivenessOutcome.HUNG, hung.outcome());
        assertEquals(1, hung.windowsHung());
        assertEquals(ResponsivenessOutcome.NO_WINDOW, adapter.checkResponsiveness(TOOL).outcome());
        assertEquals(ResponsivenessOutcome.PROCESS_EXITED, adapter.checkResponsiveness(exited).outcome());
        ResponsivenessResult failed = adapter.checkResponsiveness(error);
        assertEquals(ResponsivenessOutcome.ERROR, failed.outcome());
        assertEquals(1400, failed.win32Error());
        assertEquals(ResponsivenessOutcome.PID_REUSED, adapter.checkResponsiveness(reused).outcome());
    }

    @Test
    void elConstructorValidaSusArgumentos() {
        assertThrows(NullPointerException.class, () -> new FfmProcessAdapter(null));
    }
}
