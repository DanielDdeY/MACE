package com.mace.presentation.viewmodel;

import com.mace.application.usecase.MonitoringService;
import com.mace.application.usecase.TerminateProcessService;
import com.mace.application.usecase.TerminationResult;
import com.mace.domain.model.BlackboxStats;
import com.mace.domain.model.GpuAdapterId;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.MonitoringSnapshot;
import com.mace.domain.model.ProcessEntry;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ResponsivenessResult;
import com.mace.domain.port.out.MonitoringPublisherPort;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Estado de presentación del único flujo V2; las propiedades antiguas sostienen la vista actual. */
public final class MainDashboardViewModel implements AutoCloseable {

    private static final int HISTORY_SIZE = 30;
    private static final double GIB = 1024.0 * 1024.0 * 1024.0;

    // Compatibilidad temporal con MainDashboardController mientras se construye la vista de Dev 4.
    private final DoubleProperty cpuTemp = new SimpleDoubleProperty(Double.NaN);
    private final DoubleProperty cpuWatts = new SimpleDoubleProperty(Double.NaN);
    private final DoubleProperty cpuUsage = new SimpleDoubleProperty(Double.NaN);
    private final BooleanProperty cpuSensorAvailable = new SimpleBooleanProperty(false);
    private final BooleanProperty cpuPowerAvailable = new SimpleBooleanProperty(false);
    private final BooleanProperty cpuUsageAvailable = new SimpleBooleanProperty(false);
    private final DoubleProperty gpuTemp = new SimpleDoubleProperty(Double.NaN);
    private final DoubleProperty gpuWatts = new SimpleDoubleProperty(Double.NaN);
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

    private final ReadOnlyStringWrapper cpuVendor = new ReadOnlyStringWrapper("Desconocido");
    private final ReadOnlyStringWrapper cpuProvider = new ReadOnlyStringWrapper("Ninguno");
    private final ReadOnlyStringWrapper cpuPowerScope = new ReadOnlyStringWrapper("Ámbito desconocido");
    private final ReadOnlyObjectWrapper<MetricDisplay> cpuTemperatureDisplay = missing();
    private final ReadOnlyObjectWrapper<MetricDisplay> cpuPowerDisplay = missing();
    private final ReadOnlyObjectWrapper<MetricDisplay> cpuUsageDisplay = missing();
    private final ReadOnlyObjectWrapper<MetricDisplay> memoryTotalDisplay = missing();
    private final ReadOnlyObjectWrapper<MetricDisplay> memoryAvailableDisplay = missing();
    private final ReadOnlyObjectWrapper<MetricDisplay> memoryUsedDisplay = missing();
    private final ReadOnlyObjectWrapper<MetricDisplay> memoryUsedPercentDisplay = missing();
    private final MetricHistory cpuTemperaturePoints = new MetricHistory(HISTORY_SIZE);
    private final MetricHistory cpuUsagePoints = new MetricHistory(HISTORY_SIZE);
    private final MetricHistory cpuPowerPoints = new MetricHistory(HISTORY_SIZE);
    private final MetricHistory memoryUsedPercentPoints = new MetricHistory(HISTORY_SIZE);
    private final Map<GpuAdapterId, GpuCardViewModel> gpuCardsById = new LinkedHashMap<>();
    private final ObservableList<GpuCardViewModel> mutableGpuCards = FXCollections.observableArrayList();
    private final ObservableList<GpuCardViewModel> gpuCards = FXCollections.unmodifiableObservableList(mutableGpuCards);
    private final ObservableList<AlertItemViewModel> mutableAlerts = FXCollections.observableArrayList();
    private final ObservableList<AlertItemViewModel> alerts = FXCollections.unmodifiableObservableList(mutableAlerts);
    private final ReadOnlyObjectWrapper<Map<ProcessIdentity, ResponsivenessResult>> responsiveness =
            new ReadOnlyObjectWrapper<>(Map.of());
    private final ReadOnlyBooleanWrapper processInventoryAvailable = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<BlackboxStats> blackboxStats =
            new ReadOnlyObjectWrapper<>(BlackboxStats.disabled());
    private final ReadOnlyObjectWrapper<MonitoringSnapshot> latestSnapshot = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyLongWrapper monitoringSequence = new ReadOnlyLongWrapper();
    private final ReadOnlyLongWrapper hardwareSequence = new ReadOnlyLongWrapper();

    private final MonitoringService monitoringService;
    private final TerminateProcessService terminateProcessService;
    private final Consumer<Runnable> fxDispatcher;
    private final String blackboxFailure;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile Flow.Subscription subscription;
    private volatile boolean closed;
    private long lastAppliedSequence;
    private long lastHardwareSequence;
    private GpuAdapterId selectedLegacyGpuId;

    public MainDashboardViewModel(MonitoringService monitoringService,
                                  TerminateProcessService terminateProcessService,
                                  MonitoringPublisherPort monitoringPublisher,
                                  String sourceDescription,
                                  String blackboxFailure) {
        this(monitoringService, terminateProcessService, monitoringPublisher, sourceDescription,
                blackboxFailure, Platform::runLater);
    }

    /** Permite probar el orden de entrega sin iniciar el toolkit de JavaFX. */
    MainDashboardViewModel(MonitoringService monitoringService,
                           TerminateProcessService terminateProcessService,
                           MonitoringPublisherPort monitoringPublisher,
                           String sourceDescription,
                           String blackboxFailure,
                           Consumer<Runnable> fxDispatcher) {
        this.monitoringService = Objects.requireNonNull(monitoringService, "monitoringService no puede ser null");
        this.terminateProcessService = Objects.requireNonNull(terminateProcessService,
                "terminateProcessService no puede ser null");
        this.fxDispatcher = Objects.requireNonNull(fxDispatcher, "fxDispatcher no puede ser null");
        this.blackboxFailure = blackboxFailure;
        dataSourceDescription.set(Objects.requireNonNull(sourceDescription, "sourceDescription no puede ser null"));
        cpuTempHistory.setName("Temp. CPU");
        cpuUsageHistory.setName("Uso CPU");
        gpuTempHistory.setName("Temp. GPU");
        if (blackboxFailure != null) {
            blackboxStatus.set("Desactivada: " + blackboxFailure);
        }
        Objects.requireNonNull(monitoringPublisher, "monitoringPublisher no puede ser null")
                .subscribeMonitoring(new MonitoringSubscriber());
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
        if (closed || snapshot.sequence() <= lastAppliedSequence) {
            return;
        }
        if (snapshot.hardwareSequence() > lastHardwareSequence) {
            applyHardware(snapshot.hardware(), snapshot.hardwareSequence());
            lastHardwareSequence = snapshot.hardwareSequence();
            hardwareSequence.set(lastHardwareSequence);
        }
        processInventoryAvailable.set(snapshot.processes().isPresent());
        snapshot.processes().ifPresent(inventory -> processes.setAll(inventory.processes().stream()
                .map(MainDashboardViewModel::toRow).toList()));
        responsiveness.set(snapshot.responsiveness());
        List<AlertItemViewModel> nextAlerts = new ArrayList<>();
        snapshot.thermalAlerts().forEach(alert -> nextAlerts.add(AlertItemViewModel.from(alert)));
        snapshot.leakAlerts().forEach(alert -> nextAlerts.add(AlertItemViewModel.from(alert)));
        snapshot.hangAlerts().forEach(alert -> nextAlerts.add(AlertItemViewModel.from(alert)));
        mutableAlerts.setAll(nextAlerts);
        blackboxStats.set(snapshot.blackbox());
        applyBlackbox(snapshot.blackbox());
        latestSnapshot.set(snapshot);
        lastAppliedSequence = snapshot.sequence();
        monitoringSequence.set(lastAppliedSequence);
    }

    private static ProcessRow toRow(ProcessEntry entry) {
        return new ProcessRow(entry.identity(), entry.name(), entry.imagePath(), entry.memory());
    }

    private void applyHardware(HardwareSnapshot snapshot, long sequence) {
        var cpu = snapshot.cpu();
        cpuVendor.set(cpu.vendor().label());
        cpuProvider.set(cpu.provider().label());
        cpuPowerScope.set(cpu.powerScope().label());
        cpuTemperatureDisplay.set(MetricDisplay.decimal(cpu.temperatureCelsius(), "°C", 1));
        cpuPowerDisplay.set(MetricDisplay.decimal(cpu.powerWatts(), "W", 1));
        cpuUsageDisplay.set(MetricDisplay.decimal(cpu.usagePercent(), "%", 1));
        cpuTemperaturePoints.append(sequence, cpu.temperatureCelsius());
        cpuPowerPoints.append(sequence, cpu.powerWatts());
        cpuUsagePoints.append(sequence, cpu.usagePercent());

        var memory = snapshot.memory();
        memoryTotalDisplay.set(MetricDisplay.scaled(memory.totalBytes(), GIB, "GiB", 1));
        memoryAvailableDisplay.set(MetricDisplay.scaled(memory.availableBytes(), GIB, "GiB", 1));
        memoryUsedDisplay.set(MetricDisplay.scaled(memory.usedBytes(), GIB, "GiB", 1));
        memoryUsedPercentDisplay.set(MetricDisplay.decimal(memory.usedPercent(), "%", 1));
        memoryUsedPercentPoints.append(sequence, memory.usedPercent());

        Map<GpuAdapterId, GpuCardViewModel> nextCards = new LinkedHashMap<>();
        for (GpuReading reading : snapshot.gpus()) {
            GpuCardViewModel card = gpuCardsById.get(reading.adapterId());
            if (card == null) {
                card = new GpuCardViewModel(reading.adapterId(), HISTORY_SIZE);
            }
            card.update(reading, sequence);
            nextCards.put(reading.adapterId(), card);
        }
        gpuCardsById.clear();
        gpuCardsById.putAll(nextCards);
        mutableGpuCards.setAll(nextCards.values());

        // Estos campos solo alimentan el diseño anterior, que dibuja una GPU. La lista V2
        // anterior retiene todas las tarjetas y sus estados individuales para la nueva vista.
        // La vista antigua escucha disponibilidad y valor por separado: al recuperar un
        // sensor debe recibir primero el número, y al perderlo primero el estado N/D.
        if (!cpu.temperatureCelsius().isValid()) cpuSensorAvailable.set(false);
        if (!cpu.powerWatts().isValid()) cpuPowerAvailable.set(false);
        if (!cpu.usagePercent().isValid()) cpuUsageAvailable.set(false);
        cpuTemp.set(cpu.temperatureCelsius().orElse(Double.NaN));
        cpuWatts.set(cpu.powerWatts().orElse(Double.NaN));
        cpuUsage.set(cpu.usagePercent().orElse(Double.NaN));
        if (cpu.temperatureCelsius().isValid()) cpuSensorAvailable.set(true);
        if (cpu.powerWatts().isValid()) cpuPowerAvailable.set(true);
        if (cpu.usagePercent().isValid()) cpuUsageAvailable.set(true);

        GpuReading legacyGpu = snapshot.gpus().stream()
                .filter(reading -> reading.temperatureCelsius().isValid())
                .findFirst().orElse(snapshot.gpus().isEmpty() ? null : snapshot.gpus().get(0));
        GpuAdapterId currentLegacyId = legacyGpu == null ? null : legacyGpu.adapterId();
        if (!Objects.equals(selectedLegacyGpuId, currentLegacyId)) {
            gpuTempHistory.getData().clear();
            selectedLegacyGpuId = currentLegacyId;
        }
        gpuDetected.set(legacyGpu != null);
        if (legacyGpu == null || !legacyGpu.temperatureCelsius().isValid()) gpuAvailable.set(false);
        if (legacyGpu == null || !legacyGpu.powerWatts().isValid()) gpuPowerAvailable.set(false);
        if (legacyGpu == null || !legacyGpu.fanRpm().isValid()) gpuFanAvailable.set(false);
        gpuTemp.set(legacyGpu == null ? Double.NaN : legacyGpu.temperatureCelsius().orElse(Double.NaN));
        gpuWatts.set(legacyGpu == null ? Double.NaN : legacyGpu.powerWatts().orElse(Double.NaN));
        gpuFanRpm.set(legacyGpu == null ? 0 : (int) Math.min(Integer.MAX_VALUE, legacyGpu.fanRpm().orElse(0)));
        if (legacyGpu != null && legacyGpu.temperatureCelsius().isValid()) gpuAvailable.set(true);
        if (legacyGpu != null && legacyGpu.powerWatts().isValid()) gpuPowerAvailable.set(true);
        if (legacyGpu != null && legacyGpu.fanRpm().isValid()) gpuFanAvailable.set(true);

        if (cpu.temperatureCelsius().isValid()) {
            pushHistoryPoint(cpuTempHistory, sequence, cpu.temperatureCelsius().value());
        }
        if (cpu.usagePercent().isValid()) {
            pushHistoryPoint(cpuUsageHistory, sequence, cpu.usagePercent().value());
        }
        if (legacyGpu != null && legacyGpu.temperatureCelsius().isValid()) {
            pushHistoryPoint(gpuTempHistory, sequence, legacyGpu.temperatureCelsius().value());
        }
    }

    private void applyBlackbox(BlackboxStats stats) {
        blackboxStatus.set(stats.enabled() ? "Activa: historial persistente" :
                "Desactivada" + (blackboxFailure == null ? "" : ": " + blackboxFailure));
        blackboxCounts.set("Registros: " + stats.recordsPersisted() + " · Perdidos: "
                + stats.recordsLost() + " · Huecos: " + stats.gapCount());
        blackboxLocation.set(stats.location().isBlank() ? "Sin archivo" : stats.location());
    }

    private static void pushHistoryPoint(XYChart.Series<Number, Number> series, long sequence, double value) {
        series.getData().add(new XYChart.Data<>(sequence, value));
        if (series.getData().size() > HISTORY_SIZE) {
            series.getData().remove(0);
        }
    }

    private static ReadOnlyObjectWrapper<MetricDisplay> missing() {
        return new ReadOnlyObjectWrapper<>(MetricDisplay.unavailable(MetricState.NOT_INITIALIZED));
    }

    // API de presentación V2.
    public ReadOnlyStringProperty cpuVendorProperty() { return cpuVendor.getReadOnlyProperty(); }
    public ReadOnlyStringProperty cpuProviderProperty() { return cpuProvider.getReadOnlyProperty(); }
    public ReadOnlyStringProperty cpuPowerScopeProperty() { return cpuPowerScope.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<MetricDisplay> cpuTemperatureDisplayProperty() { return cpuTemperatureDisplay.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<MetricDisplay> cpuPowerDisplayProperty() { return cpuPowerDisplay.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<MetricDisplay> cpuUsageDisplayProperty() { return cpuUsageDisplay.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<MetricDisplay> memoryTotalDisplayProperty() { return memoryTotalDisplay.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<MetricDisplay> memoryAvailableDisplayProperty() { return memoryAvailableDisplay.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<MetricDisplay> memoryUsedDisplayProperty() { return memoryUsedDisplay.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<MetricDisplay> memoryUsedPercentDisplayProperty() { return memoryUsedPercentDisplay.getReadOnlyProperty(); }
    public MetricHistory cpuTemperaturePoints() { return cpuTemperaturePoints; }
    public MetricHistory cpuUsagePoints() { return cpuUsagePoints; }
    public MetricHistory cpuPowerPoints() { return cpuPowerPoints; }
    public MetricHistory memoryUsedPercentPoints() { return memoryUsedPercentPoints; }
    public ObservableList<GpuCardViewModel> gpuCards() { return gpuCards; }
    public ObservableList<AlertItemViewModel> alerts() { return alerts; }
    public ReadOnlyObjectProperty<Map<ProcessIdentity, ResponsivenessResult>> responsivenessProperty() {
        return responsiveness.getReadOnlyProperty();
    }
    public ReadOnlyBooleanProperty processInventoryAvailableProperty() { return processInventoryAvailable.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<BlackboxStats> blackboxStatsProperty() { return blackboxStats.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<MonitoringSnapshot> latestSnapshotProperty() { return latestSnapshot.getReadOnlyProperty(); }
    public ReadOnlyLongProperty monitoringSequenceProperty() { return monitoringSequence.getReadOnlyProperty(); }
    public ReadOnlyLongProperty hardwareSequenceProperty() { return hardwareSequence.getReadOnlyProperty(); }

    // API temporal consumida por MainDashboardController.
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
                fxDispatcher.accept(() -> applyMonitoring(item));
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
