package com.mace.presentation.viewmodel;

import com.mace.application.usecase.MonitoringService;
import com.mace.application.usecase.TerminateProcessService;
import com.mace.application.usecase.TerminationResult;
import com.mace.domain.model.BlackboxStats;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.MonitoringSnapshot;
import com.mace.domain.model.ProcessEntry;
import com.mace.domain.port.out.MonitoringPublisherPort;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adaptación temporal del dashboard actual al único flujo V2. Dev 4 puede sustituir las
 * propiedades de una GPU por vistas de la lista completa sin crear otro sondeo nativo.
 */
public final class MainDashboardViewModel implements AutoCloseable {

    private static final int HISTORY_SIZE = 30;

    private final DoubleProperty cpuTemp = new SimpleDoubleProperty();
    private final DoubleProperty cpuWatts = new SimpleDoubleProperty();
    private final DoubleProperty cpuUsage = new SimpleDoubleProperty();
    private final BooleanProperty cpuSensorAvailable = new SimpleBooleanProperty(false);
    private final BooleanProperty cpuPowerAvailable = new SimpleBooleanProperty(false);
    private final BooleanProperty cpuUsageAvailable = new SimpleBooleanProperty(false);
    private final DoubleProperty gpuTemp = new SimpleDoubleProperty();
    private final DoubleProperty gpuWatts = new SimpleDoubleProperty();
    private final IntegerProperty gpuFanRpm = new SimpleIntegerProperty();
    private final BooleanProperty gpuDetected = new SimpleBooleanProperty(false);
    private final BooleanProperty gpuAvailable = new SimpleBooleanProperty(false);
    private final BooleanProperty gpuPowerAvailable = new SimpleBooleanProperty(false);
    private final BooleanProperty gpuFanAvailable = new SimpleBooleanProperty(false);
    private final StringProperty dataSourceDescription = new SimpleStringProperty();
    private final StringProperty blackboxStatus = new SimpleStringProperty("Esperando el primer sondeo");
    private final StringProperty blackboxCounts = new SimpleStringProperty("Registros: 0 · Perdidos: 0 · Huecos: 0");
    private final StringProperty blackboxLocation = new SimpleStringProperty("Sin archivo");
    private final ObservableList<ProcessRow> processes = FXCollections.observableArrayList();

    private final XYChart.Series<Number, Number> cpuTempHistory = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> cpuUsageHistory = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> gpuTempHistory = new XYChart.Series<>();

    private final MonitoringService monitoringService;
    private final TerminateProcessService terminateProcessService;
    private final String blackboxFailure;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile Flow.Subscription subscription;
    private volatile boolean closed;
    private long lastHardwareSequence;
    private int tick;

    public MainDashboardViewModel(MonitoringService monitoringService,
                                  TerminateProcessService terminateProcessService,
                                  MonitoringPublisherPort monitoringPublisher,
                                  String sourceDescription,
                                  String blackboxFailure) {
        this.monitoringService = Objects.requireNonNull(monitoringService, "monitoringService no puede ser null");
        this.terminateProcessService = Objects.requireNonNull(terminateProcessService,
                "terminateProcessService no puede ser null");
        this.blackboxFailure = blackboxFailure;
        this.dataSourceDescription.set(Objects.requireNonNull(sourceDescription, "sourceDescription no puede ser null"));
        Objects.requireNonNull(monitoringPublisher, "monitoringPublisher no puede ser null")
                .subscribeMonitoring(new MonitoringSubscriber());

        cpuTempHistory.setName("Temp. CPU");
        cpuUsageHistory.setName("Uso CPU");
        gpuTempHistory.setName("Temp. GPU");
        if (blackboxFailure != null) {
            blackboxStatus.set("Desactivada: " + blackboxFailure);
        }
    }

    /** Inicia el único bucle de sondeo de la aplicación. */
    public void start() {
        if (closed) {
            throw new IllegalStateException("El dashboard está cerrado");
        }
        if (started.compareAndSet(false, true)) {
            monitoringService.start();
        }
    }

    /** Usa exactamente la identidad que pertenecía a la fila confirmada por el usuario. */
    public TerminationResult terminateProcess(ProcessRow row) {
        Objects.requireNonNull(row, "row no puede ser null");
        TerminationResult result = terminateProcessService.terminate(row.identity());
        if (result.status().requiresRefresh()) {
            monitoringService.requestProcessRefresh();
        }
        return result;
    }

    private void applyMonitoring(MonitoringSnapshot snapshot) {
        if (closed) {
            return;
        }
        if (snapshot.hardwareSequence() != lastHardwareSequence) {
            lastHardwareSequence = snapshot.hardwareSequence();
            applyHardware(snapshot.hardware());
        }
        snapshot.processes().ifPresent(inventory -> processes.setAll(inventory.processes().stream()
                .map(MainDashboardViewModel::toRow).toList()));
        applyBlackbox(snapshot.blackbox());
    }

    private static ProcessRow toRow(ProcessEntry entry) {
        return new ProcessRow(entry.identity(), entry.name(), entry.imagePath(), entry.memory());
    }

    private void applyHardware(HardwareSnapshot snapshot) {
        var cpu = snapshot.cpu();
        cpuSensorAvailable.set(cpu.temperatureCelsius().isValid());
        cpuPowerAvailable.set(cpu.powerWatts().isValid());
        cpuUsageAvailable.set(cpu.usagePercent().isValid());
        cpuTemp.set(cpu.temperatureCelsius().orElse(0));
        cpuWatts.set(cpu.powerWatts().orElse(0));
        cpuUsage.set(cpu.usagePercent().orElse(0));

        GpuReading gpu = snapshot.gpus().stream().filter(reading -> reading.temperatureCelsius().isValid())
                .findFirst().orElse(snapshot.gpus().isEmpty() ? null : snapshot.gpus().get(0));
        gpuDetected.set(gpu != null);
        gpuAvailable.set(gpu != null && gpu.temperatureCelsius().isValid());
        gpuPowerAvailable.set(gpu != null && gpu.powerWatts().isValid());
        gpuFanAvailable.set(gpu != null && gpu.fanRpm().isValid());
        gpuTemp.set(gpu == null ? 0 : gpu.temperatureCelsius().orElse(0));
        gpuWatts.set(gpu == null ? 0 : gpu.powerWatts().orElse(0));
        gpuFanRpm.set(gpu == null ? 0 : (int) Math.min(Integer.MAX_VALUE, gpu.fanRpm().orElse(0)));

        if (cpu.temperatureCelsius().isValid()) {
            pushHistoryPoint(cpuTempHistory, cpu.temperatureCelsius().value());
        }
        if (cpu.usagePercent().isValid()) {
            pushHistoryPoint(cpuUsageHistory, cpu.usagePercent().value());
        }
        if (gpu != null && gpu.temperatureCelsius().isValid()) {
            pushHistoryPoint(gpuTempHistory, gpu.temperatureCelsius().value());
        }
        tick++;
    }

    private void applyBlackbox(BlackboxStats stats) {
        blackboxStatus.set(stats.enabled() ? "Activa: historial persistente" :
                "Desactivada" + (blackboxFailure == null ? "" : ": " + blackboxFailure));
        blackboxCounts.set("Registros: " + stats.recordsPersisted() + " · Perdidos: "
                + stats.recordsLost() + " · Huecos: " + stats.gapCount());
        blackboxLocation.set(stats.location().isBlank() ? "Sin archivo" : stats.location());
    }

    private void pushHistoryPoint(XYChart.Series<Number, Number> series, double value) {
        series.getData().add(new XYChart.Data<>(tick, value));
        if (series.getData().size() > HISTORY_SIZE) {
            series.getData().remove(0);
        }
    }

    public DoubleProperty cpuTempProperty() { return cpuTemp; }
    public DoubleProperty cpuWattsProperty() { return cpuWatts; }
    public DoubleProperty cpuUsageProperty() { return cpuUsage; }
    public BooleanProperty cpuSensorAvailableProperty() { return cpuSensorAvailable; }
    public BooleanProperty cpuPowerAvailableProperty() { return cpuPowerAvailable; }
    public BooleanProperty cpuUsageAvailableProperty() { return cpuUsageAvailable; }
    public DoubleProperty gpuTempProperty() { return gpuTemp; }
    public DoubleProperty gpuWattsProperty() { return gpuWatts; }
    public IntegerProperty gpuFanRpmProperty() { return gpuFanRpm; }
    public BooleanProperty gpuDetectedProperty() { return gpuDetected; }
    public BooleanProperty gpuAvailableProperty() { return gpuAvailable; }
    public BooleanProperty gpuPowerAvailableProperty() { return gpuPowerAvailable; }
    public BooleanProperty gpuFanAvailableProperty() { return gpuFanAvailable; }
    public StringProperty dataSourceDescriptionProperty() { return dataSourceDescription; }
    public StringProperty blackboxStatusProperty() { return blackboxStatus; }
    public StringProperty blackboxCountsProperty() { return blackboxCounts; }
    public StringProperty blackboxLocationProperty() { return blackboxLocation; }
    public ObservableList<ProcessRow> getProcesses() { return processes; }
    public XYChart.Series<Number, Number> getCpuTempHistory() { return cpuTempHistory; }
    public XYChart.Series<Number, Number> getCpuUsageHistory() { return cpuUsageHistory; }
    public XYChart.Series<Number, Number> getGpuTempHistory() { return gpuTempHistory; }

    @Override
    public void close() {
        closed = true;
        Flow.Subscription current = subscription;
        if (current != null) {
            current.cancel();
        }
    }

    private final class MonitoringSubscriber implements Flow.Subscriber<MonitoringSnapshot> {
        @Override
        public void onSubscribe(Flow.Subscription newSubscription) {
            if (closed) {
                newSubscription.cancel();
            } else {
                subscription = newSubscription;
                newSubscription.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(MonitoringSnapshot item) {
            if (!closed) {
                Platform.runLater(() -> applyMonitoring(item));
            }
        }

        @Override
        public void onError(Throwable throwable) {
            System.getLogger(MainDashboardViewModel.class.getName())
                    .log(System.Logger.Level.ERROR, "El flujo de monitoreo falló", throwable);
        }

        @Override
        public void onComplete() {
            Flow.Subscription current = subscription;
            if (current != null) {
                current.cancel();
            }
        }
    }
}
