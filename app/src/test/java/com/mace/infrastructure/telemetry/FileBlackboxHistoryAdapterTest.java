package com.mace.infrastructure.telemetry;

import com.mace.domain.DomainFixtures;
import com.mace.domain.model.BlackboxEntry;
import com.mace.domain.model.BlackboxEntryKind;
import com.mace.domain.model.BlackboxEvent;
import com.mace.domain.model.BlackboxEventType;
import com.mace.domain.model.BlackboxSession;
import com.mace.domain.model.BlackboxSessionState;
import com.mace.domain.model.BlackboxSessionSummary;
import com.mace.domain.model.LongMetricReading;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessSample;
import com.mace.domain.port.out.BlackboxHistoryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileBlackboxHistoryAdapterTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC);

    private void writeSession(Path dir, int ringCapacity) {
        BlackboxSettings settings = new BlackboxSettings(dir, ringCapacity, 1000, 5, 64L * 1024 * 1024);
        try (PersistentBlackboxSink sink = PersistentBlackboxSink.open(new InMemoryBlackboxRing(ringCapacity),
                settings, false, clock, () -> 1L)) {
            sink.record(DomainFixtures.snapshot(DomainFixtures.cpu(55.5),
                    DomainFixtures.gpu(DomainFixtures.gpuAdapter(7, 0, "GPU"), 61)));
            sink.record(new ProcessSample(ProcessIdentity.of(4120, 9), 2L,
                    LongMetricReading.valid(70L * 1024 * 1024), LongMetricReading.valid(80L * 1024 * 1024)));
            sink.recordEvent(new BlackboxEvent(BlackboxEventType.HANG_ALERT, DomainFixtures.NOW, "frozen-app.exe"));
        }
    }

    @Test
    void listaLasSesionesConSuEstadoYContadores(@TempDir Path dir) {
        writeSession(dir, 64);

        List<BlackboxSessionSummary> sessions = new FileBlackboxHistoryAdapter(dir).listSessions();

        assertEquals(1, sessions.size());
        BlackboxSessionSummary session = sessions.get(0);
        assertEquals(BlackboxSessionState.CLOSED, session.state());
        assertEquals("Real", session.originLabel());
        assertEquals(7, session.records(), "inicio + CPU + GPU + RAM + proceso + evento + fin");
        assertEquals(0, session.gaps());
    }

    @Test
    void decodificaLasMuestrasV2ATextoLegible(@TempDir Path dir) {
        writeSession(dir, 64);
        FileBlackboxHistoryAdapter adapter = new FileBlackboxHistoryAdapter(dir);
        String id = adapter.listSessions().get(0).id();

        BlackboxSession session = adapter.readSession(id);

        List<BlackboxEntry> entries = session.entries();
        assertEquals(BlackboxEventType.SESSION_START, entries.get(0).event().orElseThrow());
        BlackboxEntry cpu = entries.stream().filter(e -> e.kind() == BlackboxEntryKind.CPU_SAMPLE).findFirst().orElseThrow();
        assertTrue(cpu.summary().contains("Temp 55.5 °C"), cpu.summary());
        BlackboxEntry gpu = entries.stream().filter(e -> e.kind() == BlackboxEntryKind.GPU_SAMPLE).findFirst().orElseThrow();
        assertTrue(gpu.summary().contains("Temp 61.0 °C"), gpu.summary());
        BlackboxEntry ram = entries.stream().filter(e -> e.kind() == BlackboxEntryKind.MEMORY_SAMPLE).findFirst().orElseThrow();
        assertTrue(ram.summary().startsWith("RAM en uso 8.0 de 16.0 GiB"), ram.summary());
        BlackboxEntry process = entries.stream().filter(e -> e.kind() == BlackboxEntryKind.PROCESS_SAMPLE).findFirst().orElseThrow();
        assertEquals("PID 4120 · Working Set 70.0 MiB · Private Bytes 80.0 MiB", process.summary());
        BlackboxEntry hang = entries.stream().filter(e -> e.event().filter(BlackboxEventType.HANG_ALERT::equals).isPresent())
                .findFirst().orElseThrow();
        assertEquals("frozen-app.exe", hang.summary());
        assertEquals(DomainFixtures.NOW, hang.timeOptional().orElseThrow());
    }

    @Test
    void losHuecosSeMuestranConSusRegistrosPerdidos(@TempDir Path dir) {
        writeSession(dir, 2);
        FileBlackboxHistoryAdapter adapter = new FileBlackboxHistoryAdapter(dir);
        BlackboxSessionSummary summary = adapter.listSessions().get(0);

        BlackboxSession session = adapter.readSession(summary.id());

        assertTrue(summary.gaps() > 0);
        assertEquals(summary.lostRecords(), session.entries().stream().filter(BlackboxEntry::isGap)
                .mapToLong(BlackboxEntry::lostRecords).sum());
        assertTrue(session.entries().stream().filter(BlackboxEntry::isGap).findFirst().orElseThrow()
                .summary().startsWith("Se perdieron"));
    }

    @Test
    void unArchivoAjenoSeMuestraComoCabeceraInvalidaSinModificarlo(@TempDir Path dir) throws IOException {
        Path foreign = dir.resolve("mace-blackbox-20200101T000000Z-1-000001.mbbx");
        Files.writeString(foreign, "no es una caja negra");

        BlackboxSessionSummary summary = new FileBlackboxHistoryAdapter(dir).listSessions().get(0);

        assertEquals(BlackboxSessionState.INVALID_HEADER, summary.state());
        assertEquals("Origen N/D", summary.originLabel());
        assertEquals("no es una caja negra", Files.readString(foreign));
    }

    @Test
    void unaSesionBorradaPorRetencionSeInformaComoAusente(@TempDir Path dir) {
        FileBlackboxHistoryAdapter adapter = new FileBlackboxHistoryAdapter(dir);

        BlackboxHistoryException error = assertThrows(BlackboxHistoryException.class,
                () -> adapter.readSession("mace-blackbox-20261021T000000Z-1-000001.mbbx"));

        assertTrue(error.sessionMissing());
    }

    @Test
    void rechazaIdentificadoresQueSalganDeLaCarpeta(@TempDir Path dir) {
        FileBlackboxHistoryAdapter adapter = new FileBlackboxHistoryAdapter(dir);

        assertThrows(IllegalArgumentException.class, () -> adapter.readSession("..\\mace-blackbox-x.mbbx"));
        assertThrows(IllegalArgumentException.class, () -> adapter.readSession("otro.txt"));
    }

    @Test
    void unaCarpetaInexistenteNoTieneSesiones(@TempDir Path dir) {
        assertFalse(new FileBlackboxHistoryAdapter(dir.resolve("no-existe")).listSessions().iterator().hasNext());
    }
}
