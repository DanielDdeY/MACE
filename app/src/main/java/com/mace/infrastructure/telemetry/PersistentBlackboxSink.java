package com.mace.infrastructure.telemetry;

import com.mace.domain.model.BlackboxEvent;
import com.mace.domain.model.BlackboxEventType;
import com.mace.domain.model.BlackboxStats;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.ProcessSample;
import com.mace.domain.port.out.BlackboxSinkPort;
import com.mace.infrastructure.nativebridge.V2Records;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * Caja negra persistente: único dueño del archivo histórico.
 *
 * <p>Flujo: cada sondeo se codifica en registros V2 y se añade al {@link BlackboxRing} temporal
 * (anillo de la DLL en modo nativo, anillo Java en modo simulado). {@link #flush()} lee el anillo
 * por secuencia y copia los registros al archivo. Si el anillo sobrescribió registros antes de
 * copiarlos, se escribe un registro {@code GAP} con la primera secuencia perdida y la cantidad,
 * de modo que la pérdida queda visible. Los eventos de aplicación (alertas, terminaciones) se
 * escriben directamente tras vaciar el anillo, para conservar el orden.</p>
 *
 * <p>Política de archivos en {@link BlackboxSettings}. Seguro para uso concurrente.</p>
 */
public final class PersistentBlackboxSink implements BlackboxSinkPort, AutoCloseable {

    private static final Logger LOG = System.getLogger(PersistentBlackboxSink.class.getName());
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final String FILE_PREFIX = "mace-blackbox-";

    private final BlackboxRing ring;
    private final BlackboxSettings settings;
    private final boolean simulated;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final long sessionPid;
    private final String sessionStamp;
    private final Map<Long, Long> wallTimes = new HashMap<>();
    private final List<BlackboxFileReader.Contents> incompletePrevious;

    private BlackboxFileWriter writer;
    private int part;
    private long nextExpectedSequence = 1;
    private long persisted;
    private long lost;
    private long gaps;
    private boolean closed;

    private PersistentBlackboxSink(BlackboxRing ring, BlackboxSettings settings, boolean simulated, Clock clock,
                                   LongSupplier nanoTime, List<BlackboxFileReader.Contents> incompletePrevious) {
        this.ring = ring;
        this.settings = settings;
        this.simulated = simulated;
        this.clock = clock;
        this.nanoTime = nanoTime;
        this.sessionPid = ProcessHandle.current().pid();
        this.sessionStamp = FILE_STAMP.format(clock.instant());
        this.incompletePrevious = incompletePrevious;
    }

    /**
     * Crea la carpeta si hace falta, revisa archivos de sesiones anteriores (sin modificarlos),
     * abre un archivo nuevo y registra el inicio de sesión.
     *
     * @throws UncheckedIOException si la carpeta o el archivo no pueden crearse
     */
    public static PersistentBlackboxSink open(BlackboxRing ring, BlackboxSettings settings, boolean simulated,
                                              Clock clock, LongSupplier nanoTime) {
        Objects.requireNonNull(ring, "ring no puede ser null");
        Objects.requireNonNull(settings, "settings no puede ser null");
        Objects.requireNonNull(clock, "clock no puede ser null");
        Objects.requireNonNull(nanoTime, "nanoTime no puede ser null");
        try {
            Files.createDirectories(settings.directory());
            List<BlackboxFileReader.Contents> incomplete = scanIncomplete(settings.directory());
            PersistentBlackboxSink sink = new PersistentBlackboxSink(ring, settings, simulated, clock, nanoTime, incomplete);
            sink.rotate();
            sink.writeEvent(new BlackboxEvent(BlackboxEventType.SESSION_START, clock.instant(),
                    (simulated ? "simulated " : "native ") + "pid=" + sink.sessionPid
                            + " incompletePrevious=" + incomplete.size()));
            return sink;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir la caja negra en " + settings.directory(), e);
        }
    }

    /** Archivos de sesiones anteriores que no se cerraron limpiamente (se conservan sin cambios). */
    public List<BlackboxFileReader.Contents> incompletePreviousFiles() {
        return incompletePrevious;
    }

    // ------------------------------------------------------------------
    // BlackboxSinkPort
    // ------------------------------------------------------------------

    @Override
    public synchronized void record(HardwareSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot no puede ser null");
        if (closed) {
            return;
        }
        long wall = snapshot.timestamp().toEpochMilli();
        appendToRing(BlackboxRing.RECORD_CPU, V2Records.encodeCpu(snapshot.cpu()), wall);
        for (GpuReading gpu : snapshot.gpus()) {
            appendToRing(BlackboxRing.RECORD_GPU, V2Records.encodeGpu(gpu), wall);
        }
        appendToRing(BlackboxRing.RECORD_SYSTEM_MEMORY, V2Records.encodeSystemMemory(snapshot.memory()), wall);
    }

    @Override
    public synchronized void record(ProcessSample sample) {
        Objects.requireNonNull(sample, "sample no puede ser null");
        if (closed) {
            return;
        }
        appendToRing(BlackboxRing.RECORD_PROCESS_SAMPLE, V2Records.encodeSample(sample), clock.millis());
    }

    @Override
    public synchronized void recordEvent(BlackboxEvent event) {
        Objects.requireNonNull(event, "event no puede ser null");
        if (closed) {
            return;
        }
        drain();
        writeEvent(event);
    }

    @Override
    public synchronized void flush() {
        if (closed) {
            return;
        }
        drain();
        writer.force();
    }

    @Override
    public synchronized BlackboxStats stats() {
        return new BlackboxStats(!closed, persisted, lost, gaps,
                writer == null ? settings.directory().toString() : writer.path().toString());
    }

    /**
     * Vacía el anillo, registra el fin de sesión, cierra el archivo (cierre limpio) y libera el
     * anillo. Debe llamarse antes de apagar la telemetría nativa. Idempotente.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            drain();
            writeEvent(new BlackboxEvent(BlackboxEventType.SESSION_END, clock.instant(),
                    "persisted=" + persisted + " lost=" + lost + " gaps=" + gaps));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo completar la caja negra al cerrar", e);
        } finally {
            closed = true;
            try {
                writer.close();
            } finally {
                ring.close();
            }
        }
    }

    // ------------------------------------------------------------------

    private void appendToRing(int type, byte[] payload, long wallMillis) {
        long sequence = ring.append(type, payload);
        wallTimes.put(sequence, wallMillis);
    }

    /** Copia el anillo al archivo desde la próxima secuencia esperada, marcando huecos. */
    private void drain() {
        List<BlackboxRing.RingRecord> records = ring.read(nextExpectedSequence);
        for (BlackboxRing.RingRecord record : records) {
            if (record.sequence() > nextExpectedSequence) {
                long missing = record.sequence() - nextExpectedSequence;
                writeRecord(BlackboxFileFormat.TYPE_GAP, nextExpectedSequence, record.monotonicNanos(),
                        clock.millis(), missing, utf8("lost=" + missing + " from=" + nextExpectedSequence));
                lost += missing;
                gaps++;
            }
            Long wall = wallTimes.remove(record.sequence());
            writeRecord(record.recordType(), record.sequence(), record.monotonicNanos(),
                    wall == null ? clock.millis() : wall, 0, record.payload());
            nextExpectedSequence = record.sequence() + 1;
        }
        wallTimes.keySet().removeIf(sequence -> sequence < nextExpectedSequence);
    }

    private void writeEvent(BlackboxEvent event) {
        writeRecord(event.type().code(), 0, nanoTime.getAsLong(), event.timestamp().toEpochMilli(), 0,
                utf8(event.detail()));
    }

    private void writeRecord(int type, long nativeSequence, long monotonic, long wall, long lostRecords, byte[] payload) {
        if (writer.isFull()) {
            rotate();
        }
        writer.append(type, nativeSequence, monotonic, wall, lostRecords, payload);
        persisted++;
    }

    private void rotate() {
        if (writer != null) {
            writer.close();
        }
        part++;
        Path next = settings.directory().resolve(String.format("%s%s-%d-%06d%s",
                FILE_PREFIX, sessionStamp, sessionPid, part, BlackboxFileFormat.FILE_EXTENSION));
        applyRetention();
        try {
            writer = BlackboxFileWriter.create(next, settings.fileCapacityRecords(), clock.millis(), simulated, sessionPid);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el archivo de caja negra " + next, e);
        }
    }

    /** Borra los archivos más antiguos hasta dejar sitio para uno nuevo dentro de los límites. */
    private void applyRetention() {
        long projected = BlackboxFileFormat.HEADER_SIZE
                + (long) settings.fileCapacityRecords() * BlackboxFileFormat.RECORD_SIZE;
        List<Path> oldestFirst = listBlackboxFiles(settings.directory());
        long total = oldestFirst.stream().mapToLong(PersistentBlackboxSink::sizeOf).sum();
        int remaining = oldestFirst.size();
        for (Path file : oldestFirst) {
            if (remaining < settings.maxFiles() && total + projected <= settings.maxTotalBytes()) {
                break;
            }
            long size = sizeOf(file);
            try {
                if (Files.deleteIfExists(file)) {
                    total -= size;
                    remaining--;
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "No se pudo borrar {0} por retención: {1}", file, e.getMessage());
            }
        }
    }

    /** Archivos de caja negra de la carpeta, del más antiguo al más reciente. */
    public static List<Path> listBlackboxFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(FILE_PREFIX) && name.endsWith(BlackboxFileFormat.FILE_EXTENSION);
                    })
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No se pudo listar {0}: {1}", directory, e.getMessage());
            return List.of();
        }
    }

    private static List<BlackboxFileReader.Contents> scanIncomplete(Path directory) {
        List<BlackboxFileReader.Contents> incomplete = new ArrayList<>();
        for (Path file : listBlackboxFiles(directory)) {
            try {
                BlackboxFileReader.Contents contents = BlackboxFileReader.read(file);
                if (contents.isIncomplete()) {
                    incomplete.add(contents);
                    LOG.log(Level.INFO, "Caja negra incompleta de una sesión anterior: {0} ({1} registros recuperables)",
                            file, contents.records().size());
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "No se pudo revisar {0}: {1}", file, e.getMessage());
            }
        }
        return List.copyOf(incomplete);
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    /** UTF-8 truncado a 128 bytes sin cortar un carácter. */
    static byte[] utf8(String text) {
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
        ByteBuffer out = ByteBuffer.allocate(BlackboxFileFormat.PAYLOAD_CAPACITY);
        encoder.encode(CharBuffer.wrap(text), out, true);
        byte[] bytes = new byte[out.position()];
        out.flip().get(bytes);
        return bytes;
    }
}
