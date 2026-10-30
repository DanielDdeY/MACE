package com.mace.infrastructure.telemetry;

import com.mace.domain.DomainFixtures;
import com.mace.domain.model.BlackboxEvent;
import com.mace.domain.model.BlackboxEventType;
import com.mace.domain.model.BlackboxStats;
import com.mace.domain.model.CpuReading;
import com.mace.domain.model.GpuAdapter;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.LongMetricReading;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessSample;
import com.mace.infrastructure.nativebridge.V2Records;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentBlackboxSinkTest {

    private static final GpuAdapter GPU_A = DomainFixtures.gpuAdapter(1, 0, "iGPU");
    private static final GpuAdapter GPU_B = DomainFixtures.gpuAdapter(2, 1, "dGPU");

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC);
    private final AtomicLong nanos = new AtomicLong(1000);

    private PersistentBlackboxSink open(Path dir, int ringCapacity, int fileCapacity, int maxFiles) {
        BlackboxSettings settings = new BlackboxSettings(dir, ringCapacity, fileCapacity, maxFiles, 64L * 1024 * 1024);
        return PersistentBlackboxSink.open(new InMemoryBlackboxRing(ringCapacity), settings, true, clock, nanos::get);
    }

    private static HardwareSnapshot snapshot(double cpuTemp) {
        return DomainFixtures.snapshot(DomainFixtures.cpu(cpuTemp), DomainFixtures.gpu(GPU_A, 50), DomainFixtures.gpu(GPU_B, 60));
    }

    private static List<StoredBlackboxRecord> records(Path dir) throws IOException {
        return BlackboxFileReader.read(PersistentBlackboxSink.listBlackboxFiles(dir).get(0)).records();
    }

    @Test
    void persisteCpuCadaGpuYMemoriaConElFormatoV2(@TempDir Path dir) throws IOException {
        try (PersistentBlackboxSink sink = open(dir, 64, 1000, 5)) {
            sink.record(snapshot(55.5));
            sink.flush();
        }

        BlackboxFileReader.Contents contents = BlackboxFileReader.read(PersistentBlackboxSink.listBlackboxFiles(dir).get(0));
        List<StoredBlackboxRecord> records = contents.records();
        assertTrue(contents.headerValid());
        assertTrue(contents.cleanClose());
        assertTrue(contents.simulated());
        assertFalse(contents.isIncomplete());
        assertEquals(BlackboxEventType.SESSION_START.code(), records.get(0).type());
        assertEquals(List.of(1, 2, 2, 4), records.subList(1, 5).stream().map(StoredBlackboxRecord::type).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L), records.subList(1, 5).stream().map(StoredBlackboxRecord::nativeSequence).toList());
        CpuReading cpu = V2Records.decodeCpu(MemorySegment.ofArray(records.get(1).payload()));
        assertEquals(55.5, cpu.temperatureCelsius().value(), 1e-6);
        assertEquals(DomainFixtures.NOW.toEpochMilli(), records.get(1).wallEpochMillis());
        assertEquals(BlackboxEventType.SESSION_END.code(), records.get(records.size() - 1).type());
    }

    @Test
    void marcaLosHuecosCuandoElAnilloSobrescribeAntesDeCopiar(@TempDir Path dir) throws IOException {
        BlackboxStats stats;
        try (PersistentBlackboxSink sink = open(dir, 2, 1000, 5)) {
            sink.record(snapshot(50)); // 4 registros en un anillo de 2: se pierden los 2 primeros
            sink.flush();
            sink.record(snapshot(51));
            sink.flush();
            stats = sink.stats();
        }

        assertEquals(4, stats.recordsLost());
        assertEquals(2, stats.gapCount());
        assertTrue(stats.hasGaps());
        List<StoredBlackboxRecord> gaps = records(dir).stream().filter(StoredBlackboxRecord::isGap).toList();
        assertEquals(2, gaps.size());
        assertEquals(1L, gaps.get(0).nativeSequence(), "primera secuencia perdida");
        assertEquals(2L, gaps.get(0).lostRecords());
        assertEquals(5L, gaps.get(1).nativeSequence());
        assertEquals(2L, gaps.get(1).lostRecords());
    }

    @Test
    void losEventosSeEscribenTrasVaciarElAnilloParaConservarElOrden(@TempDir Path dir) throws IOException {
        try (PersistentBlackboxSink sink = open(dir, 64, 1000, 5)) {
            sink.record(new ProcessSample(ProcessIdentity.of(10, 5), 3L, LongMetricReading.valid(1), LongMetricReading.valid(2)));
            sink.recordEvent(new BlackboxEvent(BlackboxEventType.HANG_ALERT, DomainFixtures.NOW, "frozen-app.exe ñ"));
        }

        List<StoredBlackboxRecord> records = records(dir);
        assertEquals(3, records.get(1).type(), "muestra de proceso");
        assertEquals(BlackboxEventType.HANG_ALERT.code(), records.get(2).type());
        assertEquals("frozen-app.exe ñ", records.get(2).text());
    }

    @Test
    void nuncaModificaArchivosExistentesYDetectaLosIncompletos(@TempDir Path dir) throws IOException {
        Path foreign = dir.resolve("mace-blackbox-20200101T000000Z-1-000001.mbbx");
        byte[] original = "no es una caja negra válida".getBytes();
        Files.write(foreign, original);

        try (PersistentBlackboxSink sink = open(dir, 64, 1000, 5)) {
            assertEquals(1, sink.incompletePreviousFiles().size());
        }

        assertArrayEquals(original, Files.readAllBytes(foreign));
        assertEquals(2, PersistentBlackboxSink.listBlackboxFiles(dir).size());
    }

    @Test
    void rotaAUnArchivoNuevoCuandoSeLlena(@TempDir Path dir) throws IOException {
        try (PersistentBlackboxSink sink = open(dir, 64, 5, 10)) {
            for (int i = 0; i < 3; i++) {
                sink.record(snapshot(50 + i));
                sink.flush();
            }
        }

        List<Path> files = PersistentBlackboxSink.listBlackboxFiles(dir);
        assertTrue(files.size() >= 3);
        long total = 0;
        for (Path file : files) {
            BlackboxFileReader.Contents contents = BlackboxFileReader.read(file);
            assertTrue(contents.cleanClose(), file + " cerrado limpiamente");
            assertTrue(contents.records().size() <= 5);
            total += contents.records().size();
        }
        assertEquals(1 + 12 + 1, total, "inicio + 3×(CPU, 2 GPU, memoria) + fin");
    }

    @Test
    void laRetencionBorraLosMasAntiguos(@TempDir Path dir) throws IOException {
        try (PersistentBlackboxSink sink = open(dir, 64, 2, 3)) {
            for (int i = 0; i < 5; i++) {
                sink.record(snapshot(50));
                sink.flush();
            }
        }

        List<Path> files = PersistentBlackboxSink.listBlackboxFiles(dir);
        assertEquals(3, files.size());
        assertTrue(files.get(files.size() - 1).getFileName().toString().endsWith(".mbbx"));
        List<StoredBlackboxRecord> last = BlackboxFileReader.read(files.get(files.size() - 1)).records();
        assertEquals(BlackboxEventType.SESSION_END.code(), last.get(last.size() - 1).type());
    }

    @Test
    void recuperaLosRegistrosValidosDeUnArchivoTruncado(@TempDir Path dir) throws IOException {
        PersistentBlackboxSink sink = open(dir, 64, 1000, 5);
        sink.record(snapshot(50));
        sink.flush();
        // Simula un corte: no se llama a close() y el último registro queda a medias.
        Path file = PersistentBlackboxSink.listBlackboxFiles(dir).get(0);
        long size = Files.size(file);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.truncate(size - 100);
        }

        BlackboxFileReader.Contents contents = BlackboxFileReader.read(file);
        sink.close();

        assertTrue(contents.isIncomplete());
        assertFalse(contents.cleanClose());
        assertTrue(contents.trailingDamaged());
        assertEquals(4, contents.records().size(), "inicio + CPU + 2 GPU; la memoria quedó truncada");
    }

    @Test
    void trasCerrarYaNoRegistraNiFalla(@TempDir Path dir) {
        PersistentBlackboxSink sink = open(dir, 64, 1000, 5);
        sink.close();
        sink.close();

        sink.record(snapshot(50));
        sink.flush();
        assertFalse(sink.stats().enabled());
    }

    @Test
    void elTextoDeEventoSeTruncaSinPartirCaracteres() {
        byte[] bytes = PersistentBlackboxSink.utf8("é".repeat(100));
        assertEquals(128, bytes.length);
        assertEquals("é".repeat(64), new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }
}
