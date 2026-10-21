package com.mace.presentation.viewmodel;

import com.mace.domain.model.BlackboxEvent;
import com.mace.domain.model.BlackboxEventType;
import com.mace.domain.model.BlackboxStats;
import com.mace.infrastructure.mock.MockScenario;
import com.mace.infrastructure.mock.MockSensorsAdapter;
import com.mace.infrastructure.telemetry.BlackboxSettings;
import com.mace.infrastructure.telemetry.InMemoryBlackboxRing;
import com.mace.infrastructure.telemetry.PersistentBlackboxSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackboxHistoryViewModelTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T05:00:00Z"), ZoneOffset.UTC);

    private static PersistentBlackboxSink open(Path directory, int ringCapacity) {
        return PersistentBlackboxSink.open(new InMemoryBlackboxRing(ringCapacity),
                new BlackboxSettings(directory, ringCapacity, 100, 5, 1024 * 1024),
                true, CLOCK, () -> 1234L);
    }

    @Test
    void distingueSesionActivaYResaltaPerdidasSinDescodificarPayloadBinario(@TempDir Path directory) {
        AtomicReference<BlackboxStats> stats = new AtomicReference<>(BlackboxStats.disabled());
        try (PersistentBlackboxSink sink = open(directory, 2);
             BlackboxHistoryViewModel model = new BlackboxHistoryViewModel(directory, stats::get,
                     Runnable::run, Runnable::run)) {
            sink.record(new MockSensorsAdapter(MockScenario.NORMAL).fetchHardwareSnapshot());
            sink.flush();
            sink.recordEvent(new BlackboxEvent(BlackboxEventType.THERMAL_ALERT, CLOCK.instant(),
                    "CPU elevada"));
            stats.set(sink.stats());

            model.refresh();
            assertEquals(1, model.sessions().size());
            assertTrue(model.sessions().getFirst().simulated());
            assertEquals(BlackboxHistoryViewModel.SessionState.IN_PROGRESS,
                    model.sessions().getFirst().state());
            assertTrue(model.sessions().getFirst().lostRecords() > 0);
            assertTrue(model.records().stream().anyMatch(row -> row.gap() && row.lostRecords() > 0));
            assertTrue(model.records().stream().anyMatch(row -> row.type().equals("Alerta térmica")
                    && row.detail().equals("CPU elevada")));
            assertTrue(model.records().stream().filter(row -> row.type().startsWith("Muestra"))
                    .allMatch(row -> row.detail().startsWith("Datos binarios V2")));

            model.setOnlyEventsAndGaps(true);
            assertFalse(model.records().stream().anyMatch(row -> row.type().startsWith("Muestra")));
            assertTrue(model.records().stream().anyMatch(BlackboxHistoryViewModel.RecordRow::gap));

            sink.close();
            stats.set(sink.stats());
            model.refresh();
            assertEquals(BlackboxHistoryViewModel.SessionState.CLOSED, model.sessions().getFirst().state());
        }
    }

    @Test
    void recuperaArchivoIncompletoYToleraDesaparicionPorRetencion(@TempDir Path directory) throws IOException {
        try (PersistentBlackboxSink sink = open(directory, 8)) {
            sink.record(new MockSensorsAdapter(MockScenario.NORMAL).fetchHardwareSnapshot());
            sink.flush();
        }
        Path file = PersistentBlackboxSink.listBlackboxFiles(directory).getFirst();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.truncate(Files.size(file) - 30);
        }
        try (BlackboxHistoryViewModel model = new BlackboxHistoryViewModel(directory,
                BlackboxStats::disabled, Runnable::run, Runnable::run)) {
            model.refresh();
            assertEquals(BlackboxHistoryViewModel.SessionState.INCOMPLETE, model.sessions().getFirst().state());
            assertFalse(model.records().isEmpty());
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(new byte[] {0}), 0);
            }
            model.refresh();
            assertEquals(BlackboxHistoryViewModel.SessionState.INVALID_HEADER,
                    model.sessions().getFirst().state());
            assertTrue(model.sessions().getFirst().label().contains("Origen N/D"));
            Files.delete(file);
            model.select(model.sessions().getFirst());
            assertTrue(model.statusProperty().get().contains("ya no existe"));
            assertTrue(model.records().isEmpty());
        }
    }

    @Test
    void limitaLaTablaALosRegistrosRecientes(@TempDir Path directory) {
        try (PersistentBlackboxSink sink = PersistentBlackboxSink.open(new InMemoryBlackboxRing(8),
                new BlackboxSettings(directory, 8, 300, 5, 1024 * 1024),
                true, CLOCK, () -> 1234L)) {
            for (int index = 0; index < 220; index++) {
                sink.recordEvent(new BlackboxEvent(BlackboxEventType.HANG_ALERT, CLOCK.instant(),
                        "evento " + index));
            }
        }
        try (BlackboxHistoryViewModel model = new BlackboxHistoryViewModel(directory,
                BlackboxStats::disabled, Runnable::run, Runnable::run)) {
            model.refresh();
            assertEquals(BlackboxHistoryViewModel.MAX_VISIBLE_RECORDS, model.records().size());
            assertEquals("Fin de sesión", model.records().getFirst().type());
            assertTrue(model.records().getLast().sequence() > 1);
        }
    }
}
