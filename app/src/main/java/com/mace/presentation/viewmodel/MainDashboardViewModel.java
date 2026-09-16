package com.mace.presentation.viewmodel;

import com.mace.application.usecase.ListProcessesService;
import com.mace.application.usecase.PollSystemMetricsService;
import com.mace.application.usecase.TerminateProcessService;
import com.mace.application.usecase.TerminationResult;
import com.mace.domain.model.ProcessEntry;
import com.mace.domain.model.ProcessId;
import com.mace.domain.model.TelemetrySnapshot;
import com.mace.domain.port.out.TelemetryPublisherPort;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ViewModel del dashboard principal. Consume exclusivamente casos de uso y el flujo de
 * telemetría; no conoce FFM, Win32 ni implementaciones concretas de infraestructura.
 */
public final class MainDashboardViewModel implements AutoCloseable {

    private final DoubleProperty cpuTemp = new SimpleDoubleProperty(0);
    private final DoubleProperty cpuWatts = new SimpleDoubleProperty(0);
    private final DoubleProperty gpuTemp = new SimpleDoubleProperty(0);
    private final DoubleProperty gpuWatts = new SimpleDoubleProperty(0);
    private final IntegerProperty gpuFanRpm = new SimpleIntegerProperty(0);
    private final BooleanProperty gpuAvailable = new SimpleBooleanProperty(false);
    private final ObservableList<ProcessRow> processes = FXCollections.observableArrayList();

    private final PollSystemMetricsService pollService;
    private final ListProcessesService listProcessesService;
    private final TerminateProcessService terminateProcessService;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public MainDashboardViewModel(
            PollSystemMetricsService pollService,
            ListProcessesService listProcessesService,
            TerminateProcessService terminateProcessService,
            TelemetryPublisherPort telemetryPublisher
    ) {
        this.pollService = Objects.requireNonNull(pollService, "pollService no puede ser null");
        this.listProcessesService = Objects.requireNonNull(listProcessesService, "listProcessesService no puede ser null");
        this.terminateProcessService = Objects.requireNonNull(terminateProcessService, "terminateProcessService no puede ser null");
        Objects.requireNonNull(telemetryPublisher, "telemetryPublisher no puede ser null")
                .subscribe(new TelemetrySubscriber());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "mace-monitoring");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Inicia el sondeo periódico a 1 Hz. Es idempotente. */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        scheduler.scheduleAtFixedRate(this::pollSafely, 0, 1, TimeUnit.SECONDS);
    }

    private void pollSafely() {
        try {
            pollService.pollOnce();
            List<ProcessRow> rows = listProcessesService.list().stream().map(MainDashboardViewModel::toRow).toList();
            Platform.runLater(() -> processes.setAll(rows));
        } catch (RuntimeException ex) {
            System.getLogger(MainDashboardViewModel.class.getName())
                    .log(System.Logger.Level.WARNING, "Error durante el sondeo de MACE", ex);
        }
    }

    public TerminationResult terminateProcess(ProcessRow row) {
        Objects.requireNonNull(row, "row no puede ser null");
        TerminationResult result = terminateProcessService.terminate(new ProcessId(row.pid()));
        if (result.isSuccess()) {
            scheduler.execute(this::refreshProcessesSafely);
        }
        return result;
    }

    private void refreshProcessesSafely() {
        try {
            List<ProcessRow> rows = listProcessesService.list().stream().map(MainDashboardViewModel::toRow).toList();
            Platform.runLater(() -> processes.setAll(rows));
        } catch (RuntimeException ex) {
            System.getLogger(MainDashboardViewModel.class.getName())
                    .log(System.Logger.Level.WARNING, "No se pudo refrescar la lista de procesos", ex);
        }
    }

    private static ProcessRow toRow(ProcessEntry entry) {
        return new ProcessRow(
                entry.pid().value(),
                entry.name(),
                entry.fullPath(),
                entry.memory().workingSetBytes(),
                entry.memory().privateBytes());
    }

    private void applyTelemetry(TelemetrySnapshot snapshot) {
        cpuTemp.set(snapshot.cpuTemp());
        cpuWatts.set(snapshot.cpuWatts());
        gpuTemp.set(snapshot.gpuTemp());
        gpuWatts.set(snapshot.gpuWatts());
        gpuFanRpm.set(snapshot.gpuFanRpm());
        gpuAvailable.set(snapshot.gpuAvailable());
    }

    public DoubleProperty cpuTempProperty() { return cpuTemp; }
    public DoubleProperty cpuWattsProperty() { return cpuWatts; }
    public DoubleProperty gpuTempProperty() { return gpuTemp; }
    public DoubleProperty gpuWattsProperty() { return gpuWatts; }
    public IntegerProperty gpuFanRpmProperty() { return gpuFanRpm; }
    public BooleanProperty gpuAvailableProperty() { return gpuAvailable; }
    public ObservableList<ProcessRow> getProcesses() { return processes; }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    private final class TelemetrySubscriber implements Flow.Subscriber<TelemetrySnapshot> {
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(TelemetrySnapshot item) {
            Platform.runLater(() -> applyTelemetry(item));
        }

        @Override
        public void onError(Throwable throwable) {
            System.getLogger(MainDashboardViewModel.class.getName())
                    .log(System.Logger.Level.ERROR, "El flujo de telemetría falló", throwable);
        }

        @Override
        public void onComplete() {
            if (subscription != null) {
                subscription.cancel();
            }
        }
    }
}
