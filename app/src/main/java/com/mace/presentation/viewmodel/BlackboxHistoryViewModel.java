package com.mace.presentation.viewmodel;

import com.mace.domain.model.BlackboxEventType;
import com.mace.domain.model.BlackboxStats;
import com.mace.infrastructure.telemetry.BlackboxFileFormat;
import com.mace.infrastructure.telemetry.BlackboxFileReader;
import com.mace.infrastructure.telemetry.StoredBlackboxRecord;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Explora los archivos persistentes sin leerlos en el hilo de JavaFX ni alterar el sondeo. */
public final class BlackboxHistoryViewModel implements AutoCloseable {

    public static final int MAX_VISIBLE_RECORDS = 150;
    private static final DateTimeFormatter RECORD_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    public enum SessionState {
        IN_PROGRESS("En curso"), CLOSED("Cerrada"), INTERRUPTED("Sin cierre registrado"),
        INCOMPLETE("Registros incompletos"), INVALID_HEADER("Cabecera inválida"),
        READ_ERROR("No disponible");

        private final String label;
        SessionState(String label) { this.label = label; }
        public String label() { return label; }
    }

    public record SessionFile(Path path, boolean simulated, boolean originKnown, SessionState state, long records,
                              long committedRecords, long gaps, long lostRecords) {
        public SessionFile {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(state, "state");
        }

        public String label() {
            String origin = !originKnown ? "Origen N/D" : simulated ? "Simulada" : "Real";
            return path.getFileName() + " · " + origin
                    + " · " + state.label();
        }
    }

    public record RecordRow(long sequence, String time, String type, String detail,
                            long lostRecords, boolean gap) {
        public static RecordRow from(StoredBlackboxRecord record) {
            int typeCode = record.type();
            if (record.isGap()) {
                return new RecordRow(record.fileSequence(), formatTime(record.wallEpochMillis()),
                        "Hueco", "Desde secuencia nativa " + record.nativeSequence(),
                        record.lostRecords(), true);
            }
            String binaryType = switch (typeCode) {
                case 1 -> "Muestra CPU";
                case 2 -> "Muestra GPU";
                case 3 -> "Muestra proceso";
                case 4 -> "Muestra RAM";
                default -> null;
            };
            if (binaryType != null) {
                return new RecordRow(record.fileSequence(), formatTime(record.wallEpochMillis()),
                        binaryType, "Datos binarios V2 · secuencia nativa " + record.nativeSequence(), 0, false);
            }
            BlackboxEventType event;
            try {
                event = BlackboxEventType.fromCode(typeCode);
            } catch (IllegalArgumentException ignored) {
                return new RecordRow(record.fileSequence(), formatTime(record.wallEpochMillis()),
                        "Tipo " + typeCode, "Carga no interpretada", 0, false);
            }
            return new RecordRow(record.fileSequence(), formatTime(record.wallEpochMillis()),
                    eventLabel(event), record.text().replace('\n', ' ').replace('\r', ' '), 0, false);
        }

        private static String eventLabel(BlackboxEventType event) {
            return switch (event) {
                case SESSION_START -> "Inicio de sesión";
                case SESSION_END -> "Fin de sesión";
                case THERMAL_ALERT -> "Alerta térmica";
                case LEAK_ALERT -> "Alerta de memoria";
                case HANG_ALERT -> "Programa sin respuesta";
                case TERMINATION_REQUEST -> "Solicitud de finalización";
                case TERMINATION_RESULT -> "Resultado de finalización";
            };
        }

        private static String formatTime(long epochMillis) {
            try {
                return RECORD_TIME.format(Instant.ofEpochMilli(epochMillis));
            } catch (DateTimeException error) {
                return "Hora N/D";
            }
        }
    }

    private final Path directory;
    private final Supplier<BlackboxStats> statsSupplier;
    private final Executor executor;
    private final Consumer<Runnable> uiDispatcher;
    private final ExecutorService ownedExecutor;
    private final ObservableList<SessionFile> mutableSessions = FXCollections.observableArrayList();
    private final ObservableList<RecordRow> mutableRecords = FXCollections.observableArrayList();
    private final ObservableList<SessionFile> sessions = FXCollections.unmodifiableObservableList(mutableSessions);
    private final ObservableList<RecordRow> records = FXCollections.unmodifiableObservableList(mutableRecords);
    private final ReadOnlyObjectWrapper<SessionFile> selectedSession = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyStringWrapper status = new ReadOnlyStringWrapper("Abre Historial para buscar sesiones");
    private final ReadOnlyStringWrapper selectedSummary = new ReadOnlyStringWrapper("Selecciona una sesión");
    private BlackboxFileReader.Contents selectedContents;
    private Path selectedPath;
    private long listRequest;
    private long recordRequest;
    private boolean onlyEventsAndGaps;
    private volatile boolean closed;

    public BlackboxHistoryViewModel(Path directory, Supplier<BlackboxStats> statsSupplier) {
        this(directory, statsSupplier, newReaderExecutor(), Platform::runLater, true);
    }

    /** Permite probar la lectura y las carreras sin iniciar el toolkit JavaFX. */
    BlackboxHistoryViewModel(Path directory, Supplier<BlackboxStats> statsSupplier,
                             Executor executor, Consumer<Runnable> uiDispatcher) {
        this(directory, statsSupplier, executor, uiDispatcher, false);
    }

    private BlackboxHistoryViewModel(Path directory, Supplier<BlackboxStats> statsSupplier,
                                     Executor executor, Consumer<Runnable> uiDispatcher, boolean ownsExecutor) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.statsSupplier = Objects.requireNonNull(statsSupplier, "statsSupplier");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.uiDispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher");
        this.ownedExecutor = ownsExecutor ? (ExecutorService) executor : null;
    }

    private static ExecutorService newReaderExecutor() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "mace-blackbox-reader");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void refresh() {
        if (closed) return;
        BlackboxStats stats = statsSupplier.get();
        long request = ++listRequest;
        ++recordRequest;
        status.set("Buscando archivos de caja negra…");
        executor.execute(() -> {
            List<SessionFile> found;
            String failure = null;
            try {
                found = readSessions(stats);
            } catch (IOException error) {
                found = List.of();
                failure = "No se pudo abrir la carpeta: " + error.getMessage();
            }
            List<SessionFile> result = found;
            String errorMessage = failure;
            uiDispatcher.accept(() -> {
                if (closed || request != listRequest) return;
                mutableSessions.setAll(result);
                if (errorMessage != null) {
                    selectedSession.set(null);
                    selectedContents = null;
                    mutableRecords.clear();
                    selectedSummary.set("Sin sesión seleccionada");
                    status.set(errorMessage);
                    return;
                }
                if (result.isEmpty()) {
                    selectedSession.set(null);
                    selectedContents = null;
                    mutableRecords.clear();
                    selectedSummary.set("No hay sesiones guardadas");
                    status.set("No hay archivos de caja negra en " + directory);
                    return;
                }
                SessionFile selected = result.stream().filter(file -> file.path().equals(selectedPath))
                        .findFirst().orElseGet(() -> result.stream()
                                .filter(file -> isCurrentFile(file.path(), stats)).findFirst()
                                .orElse(result.getFirst()));
                select(selected);
            });
        });
    }

    public void select(SessionFile file) {
        if (closed || file == null) return;
        selectedPath = file.path();
        selectedSession.set(file);
        selectedContents = null;
        mutableRecords.clear();
        selectedSummary.set("Leyendo " + file.path().getFileName() + "…");
        status.set("Leyendo registros en segundo plano…");
        long request = ++recordRequest;
        executor.execute(() -> {
            BlackboxFileReader.Contents contents = null;
            String failure = null;
            try {
                contents = BlackboxFileReader.read(file.path());
            } catch (NoSuchFileException error) {
                failure = "El archivo ya no existe (posible rotación). Pulsa Actualizar.";
            } catch (IOException error) {
                failure = "No se pudo leer el archivo: " + error.getMessage();
            }
            BlackboxFileReader.Contents result = contents;
            String errorMessage = failure;
            uiDispatcher.accept(() -> {
                if (closed || request != recordRequest) return;
                if (errorMessage != null) {
                    selectedSummary.set(errorMessage);
                    status.set(errorMessage);
                    return;
                }
                selectedContents = result;
                SessionState state = sessionState(result, isCurrentFile(file.path(), statsSupplier.get()));
                long gaps = result.gapCount();
                long lost = result.records().stream().filter(StoredBlackboxRecord::isGap)
                        .mapToLong(StoredBlackboxRecord::lostRecords).sum();
                selectedSummary.set((!result.headerValid() ? "Origen N/D"
                        : result.simulated() ? "Datos simulados" : "Datos reales")
                        + " · " + state.label() + " · " + result.records().size() + " registros recuperables"
                        + " · " + gaps + " huecos · " + lost + " perdidos");
                rebuildRecords();
                status.set("Mostrando los " + mutableRecords.size() + " registros más recientes"
                        + (onlyEventsAndGaps ? " de eventos y huecos" : "") + ".");
            });
        });
    }

    public void setOnlyEventsAndGaps(boolean value) {
        onlyEventsAndGaps = value;
        rebuildRecords();
        if (selectedContents != null) {
            status.set("Mostrando los " + mutableRecords.size() + " registros más recientes"
                    + (value ? " de eventos y huecos" : "") + ".");
        }
    }

    private void rebuildRecords() {
        if (selectedContents == null) return;
        List<StoredBlackboxRecord> filtered = selectedContents.records().stream()
                .filter(record -> !onlyEventsAndGaps || record.isGap() || isApplicationEvent(record.type()))
                .toList();
        int first = Math.max(0, filtered.size() - MAX_VISIBLE_RECORDS);
        List<RecordRow> recent = new ArrayList<>(filtered.size() - first);
        for (int index = filtered.size() - 1; index >= first; index--) {
            recent.add(RecordRow.from(filtered.get(index)));
        }
        mutableRecords.setAll(recent);
    }

    private List<SessionFile> readSessions(BlackboxStats stats) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        List<Path> paths;
        try (Stream<Path> stream = Files.list(directory)) {
            paths = stream.filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith("mace-blackbox-")
                                && name.endsWith(BlackboxFileFormat.FILE_EXTENSION)
                                && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
                    })
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        }
        List<SessionFile> result = new ArrayList<>(paths.size());
        for (Path path : paths) {
            try {
                BlackboxFileReader.Contents contents = BlackboxFileReader.read(path);
                long lost = contents.records().stream().filter(StoredBlackboxRecord::isGap)
                        .mapToLong(StoredBlackboxRecord::lostRecords).sum();
                result.add(new SessionFile(path, contents.simulated(), contents.headerValid(),
                        sessionState(contents, isCurrentFile(path, stats)), contents.records().size(),
                        contents.committedRecords(), contents.gapCount(), lost));
            } catch (NoSuchFileException ignored) {
                // La retención puede quitar un archivo entre el listado y la lectura.
            } catch (IOException error) {
                result.add(new SessionFile(path, false, false, SessionState.READ_ERROR, 0, 0, 0, 0));
            }
        }
        return List.copyOf(result);
    }

    private static SessionState sessionState(BlackboxFileReader.Contents contents, boolean current) {
        if (current) return SessionState.IN_PROGRESS;
        if (!contents.headerValid()) return SessionState.INVALID_HEADER;
        if (contents.trailingDamaged()) return SessionState.INCOMPLETE;
        return contents.cleanClose() ? SessionState.CLOSED : SessionState.INTERRUPTED;
    }

    private static boolean isApplicationEvent(int type) {
        try {
            BlackboxEventType.fromCode(type);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isCurrentFile(Path path, BlackboxStats stats) {
        if (!stats.enabled() || stats.location().isBlank()) return false;
        try {
            return path.toAbsolutePath().normalize().equals(Path.of(stats.location()).toAbsolutePath().normalize());
        } catch (RuntimeException invalidLocation) {
            return false;
        }
    }

    public Path directory() { return directory; }
    public ObservableList<SessionFile> sessions() { return sessions; }
    public ObservableList<RecordRow> records() { return records; }
    public ReadOnlyObjectProperty<SessionFile> selectedSessionProperty() {
        return selectedSession.getReadOnlyProperty();
    }
    public ReadOnlyStringProperty statusProperty() { return status.getReadOnlyProperty(); }
    public ReadOnlyStringProperty selectedSummaryProperty() { return selectedSummary.getReadOnlyProperty(); }

    @Override public void close() {
        closed = true;
        ++listRequest;
        ++recordRequest;
        if (ownedExecutor != null) ownedExecutor.shutdownNow();
    }
}
