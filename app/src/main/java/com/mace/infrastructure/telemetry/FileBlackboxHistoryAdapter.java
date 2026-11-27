package com.mace.infrastructure.telemetry;

import com.mace.domain.model.BlackboxEntry;
import com.mace.domain.model.BlackboxEntryKind;
import com.mace.domain.model.BlackboxEventType;
import com.mace.domain.model.BlackboxSession;
import com.mace.domain.model.BlackboxSessionState;
import com.mace.domain.model.BlackboxSessionSummary;
import com.mace.domain.model.CpuReading;
import com.mace.domain.model.GpuAdapter;
import com.mace.domain.model.GpuAdapterId;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.LongMetricReading;
import com.mace.domain.model.MetricReading;
import com.mace.domain.model.ProcessSample;
import com.mace.domain.model.SystemMemoryReading;
import com.mace.domain.port.out.BlackboxHistoryException;
import com.mace.domain.port.out.BlackboxHistoryPort;
import com.mace.infrastructure.nativebridge.NativeLayouts;
import com.mace.infrastructure.nativebridge.NativeLibraryException;
import com.mace.infrastructure.nativebridge.V2Records;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Implementación de {@link BlackboxHistoryPort} sobre los archivos {@code *.mbbx}. Solo lee: usa
 * {@link BlackboxFileReader}, que recupera archivos incompletos sin modificarlos. Las muestras V2
 * se decodifican a un texto legible («Uso 5.5 % · Temp N/D (Sin proveedor)…»).
 */
public final class FileBlackboxHistoryAdapter implements BlackboxHistoryPort {

    private static final String FILE_PREFIX = "mace-blackbox-";
    private static final double GIB = 1024.0 * 1024.0 * 1024.0;
    private static final double MIB = 1024.0 * 1024.0;

    private final Path directory;

    public FileBlackboxHistoryAdapter(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory no puede ser null").toAbsolutePath().normalize();
    }

    public Path directory() {
        return directory;
    }

    @Override
    public List<BlackboxSessionSummary> listSessions() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(directory)) {
            files = stream.filter(FileBlackboxHistoryAdapter::isSessionFile).toList();
        } catch (IOException e) {
            throw new BlackboxHistoryException("No se pudo abrir la carpeta " + directory + ": " + e.getMessage(), false, e);
        }
        List<BlackboxSessionSummary> sessions = new ArrayList<>(files.size());
        for (Path file : files) {
            try {
                sessions.add(summarize(file, BlackboxFileReader.read(file)));
            } catch (NoSuchFileException ignored) {
                // La retención puede borrar un archivo entre el listado y la lectura.
            } catch (IOException e) {
                sessions.add(BlackboxSessionSummary.unreadable(file.getFileName().toString(), file.toString()));
            }
        }
        return List.copyOf(sessions);
    }

    @Override
    public BlackboxSession readSession(String sessionId) {
        Path file = resolve(sessionId);
        BlackboxFileReader.Contents contents;
        try {
            contents = BlackboxFileReader.read(file);
        } catch (NoSuchFileException e) {
            throw new BlackboxHistoryException("La sesión " + sessionId + " ya no existe (posible rotación)", true, e);
        } catch (IOException e) {
            throw new BlackboxHistoryException("No se pudo leer " + sessionId + ": " + e.getMessage(), false, e);
        }
        List<BlackboxEntry> entries = new ArrayList<>(contents.records().size());
        for (StoredBlackboxRecord record : contents.records()) {
            entries.add(toEntry(record));
        }
        return new BlackboxSession(summarize(file, contents), entries);
    }

    // ------------------------------------------------------------------

    private Path resolve(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId no puede ser null");
        boolean valid = sessionId.startsWith(FILE_PREFIX) && sessionId.endsWith(BlackboxFileFormat.FILE_EXTENSION)
                && sessionId.indexOf('/') < 0 && sessionId.indexOf('\\') < 0 && !sessionId.contains("..");
        if (!valid) {
            throw new IllegalArgumentException("Identificador de sesión inválido: " + sessionId);
        }
        Path file = directory.resolve(sessionId).normalize();
        if (!file.getParent().equals(directory) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new BlackboxHistoryException("La sesión " + sessionId + " ya no existe (posible rotación)", true, null);
        }
        return file;
    }

    private static boolean isSessionFile(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(FILE_PREFIX) && name.endsWith(BlackboxFileFormat.FILE_EXTENSION)
                && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static BlackboxSessionSummary summarize(Path file, BlackboxFileReader.Contents contents) {
        long lost = contents.records().stream().filter(StoredBlackboxRecord::isGap)
                .mapToLong(StoredBlackboxRecord::lostRecords).sum();
        BlackboxSessionState state;
        if (!contents.headerValid()) {
            state = BlackboxSessionState.INVALID_HEADER;
        } else if (contents.trailingDamaged()) {
            state = BlackboxSessionState.INCOMPLETE;
        } else {
            state = contents.cleanClose() ? BlackboxSessionState.CLOSED : BlackboxSessionState.INTERRUPTED;
        }
        return new BlackboxSessionSummary(file.getFileName().toString(), file.toString(), contents.simulated(),
                contents.headerValid(), state, contents.records().size(), contents.committedRecords(),
                contents.gapCount(), lost);
    }

    static BlackboxEntry toEntry(StoredBlackboxRecord record) {
        Instant time;
        try {
            time = Instant.ofEpochMilli(record.wallEpochMillis());
        } catch (DateTimeException e) {
            time = null;
        }
        if (record.isGap()) {
            return new BlackboxEntry(record.fileSequence(), BlackboxEntryKind.GAP, null, time, record.nativeSequence(),
                    record.lostRecords(), "Se perdieron " + record.lostRecords()
                    + " registros desde la secuencia nativa " + record.nativeSequence());
        }
        BlackboxEntryKind sampleKind = switch (record.type()) {
            case BlackboxRing.RECORD_CPU -> BlackboxEntryKind.CPU_SAMPLE;
            case BlackboxRing.RECORD_GPU -> BlackboxEntryKind.GPU_SAMPLE;
            case BlackboxRing.RECORD_PROCESS_SAMPLE -> BlackboxEntryKind.PROCESS_SAMPLE;
            case BlackboxRing.RECORD_SYSTEM_MEMORY -> BlackboxEntryKind.MEMORY_SAMPLE;
            default -> null;
        };
        if (sampleKind != null) {
            return new BlackboxEntry(record.fileSequence(), sampleKind, null, time, record.nativeSequence(), 0,
                    describeSample(sampleKind, record.payload()));
        }
        try {
            BlackboxEventType event = BlackboxEventType.fromCode(record.type());
            String text = record.text().replace('\n', ' ').replace('\r', ' ');
            return new BlackboxEntry(record.fileSequence(), BlackboxEntryKind.EVENT, event, time, 0, 0, text);
        } catch (IllegalArgumentException unknownType) {
            return new BlackboxEntry(record.fileSequence(), BlackboxEntryKind.UNKNOWN, null, time, 0, 0,
                    "Tipo " + record.type() + " no interpretado");
        }
    }

    /** Decodifica el payload V2 con el mismo códec que usa el puente FFM. */
    static String describeSample(BlackboxEntryKind kind, byte[] payload) {
        MemorySegment segment = MemorySegment.ofArray(payload);
        try {
            return switch (kind) {
                case CPU_SAMPLE -> {
                    CpuReading cpu = V2Records.decodeCpu(segment);
                    yield "CPU " + cpu.vendor().label() + " · Uso " + metric(cpu.usagePercent(), "%")
                            + " · Temp " + metric(cpu.temperatureCelsius(), "°C")
                            + " · Potencia " + metric(cpu.powerWatts(), "W");
                }
                case GPU_SAMPLE -> {
                    GpuAdapterId id = V2Records.readLuid(segment, NativeLayouts.GPU_LUID_OFFSET);
                    GpuReading gpu = V2Records.decodeGpu(segment, new GpuAdapter(id, 0, 0, 0, 0, 0, 0, id.toString()));
                    yield "GPU " + id + " · Uso " + metric(gpu.usagePercent(), "%")
                            + " · Temp " + metric(gpu.temperatureCelsius(), "°C")
                            + " · Potencia " + metric(gpu.powerWatts(), "W");
                }
                case MEMORY_SAMPLE -> {
                    SystemMemoryReading memory = V2Records.decodeSystemMemory(segment);
                    LongMetricReading used = memory.usedBytes();
                    yield used.isValid()
                            ? String.format(Locale.ROOT, "RAM en uso %.1f de %.1f GiB (%s)", used.value() / GIB,
                            memory.totalBytes().value() / GIB, metric(memory.usedPercent(), "%"))
                            : "RAM N/D (" + used.state().label() + ")";
                }
                case PROCESS_SAMPLE -> {
                    ProcessSample sample = V2Records.decodeSample(segment);
                    yield sample == null ? "Muestra de proceso sin identidad"
                            : "PID " + sample.identity().pid().value() + " · Working Set "
                            + bytes(sample.workingSet()) + " · Private Bytes " + bytes(sample.privateBytes());
                }
                default -> "";
            };
        } catch (NativeLibraryException | IllegalArgumentException | IndexOutOfBoundsException e) {
            return "Datos V2 no interpretables";
        }
    }

    private static String metric(MetricReading reading, String unit) {
        return reading.isValid() ? String.format(Locale.ROOT, "%.1f %s", reading.value(), unit)
                : "N/D (" + reading.state().label() + ")";
    }

    private static String bytes(LongMetricReading reading) {
        return reading.isValid() ? String.format(Locale.ROOT, "%.1f MiB", reading.value() / MIB)
                : "N/D (" + reading.state().label() + ")";
    }
}
