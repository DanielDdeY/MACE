package com.mace.presentation.view;

import com.mace.application.usecase.TerminationResult;
import com.mace.application.usecase.TerminationStatus;
import com.mace.domain.model.AlertLevel;
import com.mace.domain.model.GpuAdapterId;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ResponsivenessResult;
import com.mace.presentation.view.component.GpuCard;
import com.mace.presentation.view.component.MetricCard;
import com.mace.presentation.view.component.NavIcon;
import com.mace.presentation.viewmodel.AlertItemViewModel;
import com.mace.presentation.viewmodel.GpuCardViewModel;
import com.mace.presentation.viewmodel.MainDashboardViewModel;
import com.mace.presentation.viewmodel.MetricHistory;
import com.mace.presentation.viewmodel.ProcessMemorySummary;
import com.mace.presentation.viewmodel.ProcessRow;
import com.mace.presentation.viewmodel.ProcessTableViewModel;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Vista del único flujo V2: cada tarjeta y gráfico reutiliza datos del mismo sondeo. */
public final class MainDashboardController {

    private static final int PIE_PROCESS_LIMIT = 8;
    private static final DateTimeFormatter ALERT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML private ToggleButton navCpu;
    @FXML private ToggleButton navGpu;
    @FXML private ToggleButton navHistorial;
    @FXML private ToggleButton navAlerts;
    @FXML private ToggleButton navProcesos;
    @FXML private Label dataSourceLabel;

    @FXML private ScrollPane paneCpu;
    @FXML private ScrollPane paneGpu;
    @FXML private ScrollPane paneHistorial;
    @FXML private VBox paneAlerts;
    @FXML private VBox paneProcesos;

    @FXML private Label cpuVendorLabel;
    @FXML private Label cpuProviderLabel;
    @FXML private FlowPane cpuMetricsPane;
    @FXML private Label cpuStatusLabel;
    @FXML private Label cpuMaxLabel;
    @FXML private Label cpuAvgLabel;
    @FXML private Label gpuEmptyLabel;
    @FXML private VBox gpuCardsBox;

    @FXML private LineChart<Number, Number> cpuTempChart;
    @FXML private LineChart<Number, Number> cpuUsageChart;
    @FXML private LineChart<Number, Number> gpuTempChart;
    @FXML private LineChart<Number, Number> gpuUsageChart;
    @FXML private LineChart<Number, Number> gpuPowerChart;
    @FXML private ComboBox<GpuCardViewModel> gpuSelector;
    @FXML private Label blackboxStatusLabel;
    @FXML private Label blackboxCountsLabel;
    @FXML private Label blackboxLocationLabel;

    @FXML private Label alertSummaryLabel;
    @FXML private ListView<AlertItemViewModel> alertList;

    @FXML private TextField searchProcesos;
    @FXML private Label inventoryStatusLabel;
    @FXML private TableView<ProcessRow> tablaProcesos;
    @FXML private TableColumn<ProcessRow, Number> procPidColumn;
    @FXML private TableColumn<ProcessRow, String> procNombreColumn;
    @FXML private TableColumn<ProcessRow, String> procRutaColumn;
    @FXML private TableColumn<ProcessRow, Number> procMemoriaColumn;
    @FXML private TableColumn<ProcessRow, String> procResponseColumn;
    @FXML private TableColumn<ProcessRow, Void> procAccionColumn;
    @FXML private PieChart memoryPieChart;
    @FXML private Label ramTotalLabel;
    @FXML private Label ramCountLabel;

    private final MainDashboardViewModel dashboardViewModel;
    private final Map<GpuAdapterId, GpuCard> gpuCardsById = new HashMap<>();
    private final List<MetricCard> cpuMetricCards = new ArrayList<>();
    private ProcessTableViewModel procesosViewModel;
    private GpuCardViewModel selectedGpu;
    private GpuAdapterId selectedGpuId;

    private final ListChangeListener<MetricHistory.Point> selectedTempListener = change ->
            renderChart(gpuTempChart, selectedGpu == null ? null : selectedGpu.temperatureHistory());
    private final ListChangeListener<MetricHistory.Point> selectedUsageListener = change ->
            renderChart(gpuUsageChart, selectedGpu == null ? null : selectedGpu.usageHistory());
    private final ListChangeListener<MetricHistory.Point> selectedPowerListener = change ->
            renderChart(gpuPowerChart, selectedGpu == null ? null : selectedGpu.powerHistory());

    public MainDashboardController(MainDashboardViewModel dashboardViewModel) {
        this.dashboardViewModel = Objects.requireNonNull(dashboardViewModel, "dashboardViewModel no puede ser null");
    }

    @FXML
    public void initialize() {
        configureNavigation();
        configureCpu();
        configureGpus();
        configureHistory();
        configureAlerts();
        configureProcesses();
        bindBlackbox();
        dashboardViewModel.start();
    }

    private void configureNavigation() {
        navCpu.setGraphic(NavIcon.cpu());
        navGpu.setGraphic(NavIcon.gpu());
        navHistorial.setGraphic(NavIcon.historial());
        navAlerts.setGraphic(NavIcon.alerts());
        navProcesos.setGraphic(NavIcon.procesos());
        ToggleGroup group = new ToggleGroup();
        for (ToggleButton button : List.of(navCpu, navGpu, navHistorial, navAlerts, navProcesos)) {
            button.setToggleGroup(group);
        }
        navCpu.setOnAction(e -> showSection(paneCpu));
        navGpu.setOnAction(e -> showSection(paneGpu));
        navHistorial.setOnAction(e -> showSection(paneHistorial));
        navAlerts.setOnAction(e -> showSection(paneAlerts));
        navProcesos.setOnAction(e -> showSection(paneProcesos));
        group.selectedToggleProperty().addListener((obs, oldValue, next) -> {
            if (next == null && oldValue != null) oldValue.setSelected(true);
        });
        navCpu.setSelected(true);
        showSection(paneCpu);
    }

    private void showSection(Node section) {
        for (Node node : List.of(paneCpu, paneGpu, paneHistorial, paneAlerts, paneProcesos)) {
            boolean visible = node == section;
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    private void configureCpu() {
        cpuVendorLabel.textProperty().bind(dashboardViewModel.cpuVendorProperty().map(v -> "Fabricante: " + v));
        cpuProviderLabel.textProperty().bind(dashboardViewModel.cpuProviderProperty().map(v -> "Proveedor: " + v));
        cpuMetricCards.addAll(List.of(
                new MetricCard("Temperatura CPU", dashboardViewModel.cpuTemperatureDisplayProperty()),
                new MetricCard("Uso CPU", dashboardViewModel.cpuUsageDisplayProperty()),
                new MetricCard("Potencia CPU", dashboardViewModel.cpuPowerDisplayProperty(),
                        dashboardViewModel.cpuPowerScopeProperty()),
                new MetricCard("RAM física total", dashboardViewModel.memoryTotalDisplayProperty()),
                new MetricCard("RAM en uso", dashboardViewModel.memoryUsedDisplayProperty()),
                new MetricCard("RAM disponible", dashboardViewModel.memoryAvailableDisplayProperty()),
                new MetricCard("Uso de RAM", dashboardViewModel.memoryUsedPercentDisplayProperty())));
        cpuMetricsPane.getChildren().addAll(cpuMetricCards);
        dashboardViewModel.cpuTemperaturePoints().points().addListener(
                (ListChangeListener<MetricHistory.Point>) change -> updateCpuStats());
        dashboardViewModel.cpuTemperatureDisplayProperty().addListener((obs, oldValue, next) -> updateCpuStatus());
        dashboardViewModel.alerts().addListener((ListChangeListener<AlertItemViewModel>) change -> updateCpuStatus());
        updateCpuStatus();
        updateCpuStats();
    }

    private void updateCpuStatus() {
        cpuStatusLabel.getStyleClass().removeAll("status-ok", "status-warning", "status-critical", "status-muted");
        var temperature = dashboardViewModel.cpuTemperatureDisplayProperty().get();
        if (!temperature.isValid()) {
            cpuStatusLabel.setText(temperature.text());
            cpuStatusLabel.getStyleClass().add("status-muted");
            return;
        }
        AlertLevel level = dashboardViewModel.alerts().stream()
                .filter(alert -> alert.kind() == AlertItemViewModel.Kind.THERMAL && alert.gpuId().isEmpty())
                .flatMap(alert -> alert.thermalLevel().stream())
                .max(Enum::compareTo).orElse(AlertLevel.NONE);
        setThermalLabel(cpuStatusLabel, level);
    }

    private static void setThermalLabel(Label label, AlertLevel level) {
        if (level == AlertLevel.CRITICAL) {
            label.setText("Crítico");
            label.getStyleClass().add("status-critical");
        } else if (level == AlertLevel.WARNING) {
            label.setText("Advertencia");
            label.getStyleClass().add("status-warning");
        } else {
            label.setText("Normal");
            label.getStyleClass().add("status-ok");
        }
    }

    private void updateCpuStats() {
        List<Double> valid = dashboardViewModel.cpuTemperaturePoints().points().stream()
                .filter(MetricHistory.Point::isValid).map(point -> point.value().orElseThrow()).toList();
        cpuMaxLabel.setText(valid.isEmpty() ? "N/D" : String.format(Locale.ROOT, "%.1f °C",
                valid.stream().mapToDouble(Double::doubleValue).max().orElseThrow()));
        cpuAvgLabel.setText(valid.isEmpty() ? "N/D" : String.format(Locale.ROOT, "%.1f °C",
                valid.stream().mapToDouble(Double::doubleValue).average().orElseThrow()));
    }

    private void configureGpus() {
        dashboardViewModel.gpuCards().addListener((ListChangeListener<GpuCardViewModel>) change -> syncGpuCards());
        dashboardViewModel.alerts().addListener((ListChangeListener<AlertItemViewModel>) change -> updateGpuStatuses());
        dashboardViewModel.hardwareSequenceProperty().addListener((obs, previous, next) -> updateGpuEmptyState());
        syncGpuCards();
    }

    private void syncGpuCards() {
        List<GpuCardViewModel> models = dashboardViewModel.gpuCards();
        Map<GpuAdapterId, GpuCard> next = new HashMap<>();
        List<GpuCard> ordered = new ArrayList<>();
        for (GpuCardViewModel model : models) {
            GpuCard card = gpuCardsById.get(model.id());
            if (card == null || card.model() != model) {
                if (card != null) card.close();
                card = new GpuCard(model);
            }
            next.put(model.id(), card);
            ordered.add(card);
        }
        gpuCardsById.forEach((id, card) -> {
            if (!next.containsKey(id)) card.close();
        });
        gpuCardsById.clear();
        gpuCardsById.putAll(next);
        if (!gpuCardsBox.getChildren().equals(ordered)) {
            gpuCardsBox.getChildren().setAll(ordered);
        }
        updateGpuEmptyState();
        updateGpuStatuses();
        restoreGpuSelection();
    }

    private void updateGpuEmptyState() {
        boolean empty = dashboardViewModel.gpuCards().isEmpty();
        gpuEmptyLabel.setVisible(empty);
        gpuEmptyLabel.setManaged(empty);
        gpuEmptyLabel.setText(dashboardViewModel.hardwareSequenceProperty().get() == 0
                ? "Esperando la primera detección" : "No se detectaron GPU físicas");
    }

    private void updateGpuStatuses() {
        for (GpuCard card : gpuCardsById.values()) {
            AlertLevel level = dashboardViewModel.alerts().stream()
                    .filter(alert -> alert.kind() == AlertItemViewModel.Kind.THERMAL)
                    .filter(alert -> alert.gpuId().filter(card.model().id()::equals).isPresent())
                    .flatMap(alert -> alert.thermalLevel().stream())
                    .max(Enum::compareTo).orElse(AlertLevel.NONE);
            card.setThermalStatus(level);
        }
    }

    private void configureHistory() {
        watchHistory(cpuUsageChart, dashboardViewModel.cpuUsagePoints());
        watchHistory(cpuTempChart, dashboardViewModel.cpuTemperaturePoints());
        gpuSelector.setItems(dashboardViewModel.gpuCards());
        gpuSelector.setConverter(new StringConverter<>() {
            @Override public String toString(GpuCardViewModel card) {
                return card == null ? "" : card.nameProperty().get();
            }
            @Override public GpuCardViewModel fromString(String text) { return null; }
        });
        gpuSelector.valueProperty().addListener((obs, previous, next) -> {
            if (next != null) selectedGpuId = next.id();
            selectGpuHistory(next);
        });
        restoreGpuSelection();
    }

    private void restoreGpuSelection() {
        if (gpuSelector == null) return;
        GpuCardViewModel target = dashboardViewModel.gpuCards().stream()
                .filter(card -> selectedGpuId != null && card.id().equals(selectedGpuId))
                .findFirst().orElse(null);
        if (target == null && selectedGpuId == null && !dashboardViewModel.gpuCards().isEmpty()) {
            target = dashboardViewModel.gpuCards().getFirst();
        }
        if (target == null) {
            gpuSelector.getSelectionModel().clearSelection();
            selectGpuHistory(null);
        } else {
            gpuSelector.getSelectionModel().select(target);
            selectGpuHistory(target);
        }
    }

    private void selectGpuHistory(GpuCardViewModel next) {
        if (selectedGpu == next) return;
        if (selectedGpu != null) {
            selectedGpu.temperatureHistory().points().removeListener(selectedTempListener);
            selectedGpu.usageHistory().points().removeListener(selectedUsageListener);
            selectedGpu.powerHistory().points().removeListener(selectedPowerListener);
        }
        selectedGpu = next;
        if (next != null) {
            next.temperatureHistory().points().addListener(selectedTempListener);
            next.usageHistory().points().addListener(selectedUsageListener);
            next.powerHistory().points().addListener(selectedPowerListener);
        }
        renderChart(gpuTempChart, next == null ? null : next.temperatureHistory());
        renderChart(gpuUsageChart, next == null ? null : next.usageHistory());
        renderChart(gpuPowerChart, next == null ? null : next.powerHistory());
    }

    private static void watchHistory(LineChart<Number, Number> chart, MetricHistory history) {
        history.points().addListener((ListChangeListener<MetricHistory.Point>) change -> renderChart(chart, history));
        renderChart(chart, history);
    }

    /** Cada tramo válido es una serie separada: JavaFX no une puntos a través de N/D. */
    private static void renderChart(LineChart<Number, Number> chart, MetricHistory history) {
        if (chart == null) return;
        List<XYChart.Series<Number, Number>> series = new ArrayList<>();
        if (history != null) {
            for (List<MetricHistory.Point> segment : history.validSegments()) {
                XYChart.Series<Number, Number> line = new XYChart.Series<>();
                for (MetricHistory.Point point : segment) {
                    line.getData().add(new XYChart.Data<>(point.hardwareSequence(), point.value().orElseThrow()));
                }
                series.add(line);
            }
        }
        chart.getData().setAll(series);
    }

    private void configureAlerts() {
        alertList.setItems(dashboardViewModel.alerts());
        alertList.setPlaceholder(new Label("Sin alertas activas"));
        alertList.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(AlertItemViewModel item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String time = ALERT_TIME.format(item.timestamp().atZone(ZoneId.systemDefault()));
                String kind = switch (item.kind()) {
                    case THERMAL -> switch (item.thermalLevel().orElse(AlertLevel.NONE)) {
                        case CRITICAL -> "Temperatura crítica";
                        case WARNING -> "Advertencia térmica";
                        case NONE -> "Aviso térmico";
                    };
                    case MEMORY_LEAK -> "Posible fuga de memoria";
                    case HUNG_PROCESS -> "Ventana sin respuesta";
                };
                setText(time + " · " + kind + " · " + item.source() + " · " + item.message());
                setGraphic(null);
            }
        });
        dashboardViewModel.alerts().addListener((ListChangeListener<AlertItemViewModel>) change -> updateAlertSummary());
        updateAlertSummary();
    }

    private void updateAlertSummary() {
        int count = dashboardViewModel.alerts().size();
        alertSummaryLabel.setText(count == 0 ? "Sin alertas activas" : count + " alerta(s) activas");
        navAlerts.setText(count == 0 ? "Alertas" : "Alertas (" + count + ")");
    }

    private void configureProcesses() {
        procesosViewModel = new ProcessTableViewModel(dashboardViewModel.getProcesses());
        procPidColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleLongProperty(data.getValue().pid()));
        procNombreColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(data.getValue().name()));
        procRutaColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(data.getValue().fullPath()));
        procMemoriaColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleDoubleProperty(data.getValue().workingSetMb()));
        procMemoriaColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : Double.isFinite(value.doubleValue())
                        ? String.format(Locale.ROOT, "%.1f MiB", value.doubleValue()) : "N/D");
            }
        });
        procResponseColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(responseText(data.getValue())));
        dashboardViewModel.responsivenessProperty().addListener((obs, previous, next) -> tablaProcesos.refresh());
        procAccionColumn.setCellFactory(column -> new TableCell<>() {
            private final Button terminateButton = new Button("Finalizar Proceso");
            {
                terminateButton.getStyleClass().add("danger-button");
                terminateButton.setOnAction(event -> {
                    ProcessRow row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row != null) procesosViewModel.requestTerminate(row);
                });
            }
            @Override protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty ? null : terminateButton);
            }
        });
        searchProcesos.textProperty().addListener((obs, previous, next) -> {
            procesosViewModel.searchTextProperty().set(next);
            restoreProcessSelectionLater();
        });
        tablaProcesos.setItems(procesosViewModel.getSortedProcesses());
        procesosViewModel.getSortedProcesses().comparatorProperty().bind(tablaProcesos.comparatorProperty());
        tablaProcesos.setOnMouseClicked(event -> rememberExplicitSelection());
        tablaProcesos.setOnKeyReleased(event -> rememberExplicitSelection());
        procesosViewModel.setOnTerminateRequested(this::confirmAndTerminate);

        dashboardViewModel.getProcesses().addListener((ListChangeListener<ProcessRow>) change -> {
            refreshProcessSummary();
            restoreProcessSelectionLater();
        });
        dashboardViewModel.processInventoryAvailableProperty().addListener((obs, previous, available) ->
                updateInventoryStatus());
        dashboardViewModel.monitoringSequenceProperty().addListener((obs, previous, next) ->
                updateInventoryStatus());
        refreshProcessSummary();
        updateInventoryStatus();
    }

    private String responseText(ProcessRow row) {
        ResponsivenessResult result = dashboardViewModel.responsivenessProperty().get().get(row.identity());
        if (result == null) return "Sin consultar";
        if (result.isHung()) {
            return result.outcome().label() + " (" + result.windowsHung() + "/" + result.windowsExamined() + ")";
        }
        return result.outcome().label() + (result.win32Error() == 0 ? ""
                : " (Win32 " + Integer.toUnsignedString(result.win32Error()) + ")");
    }

    private void rememberExplicitSelection() {
        ProcessRow row = tablaProcesos.getSelectionModel().getSelectedItem();
        if (row != null) procesosViewModel.rememberSelection(row);
    }

    private void restoreProcessSelectionLater() {
        Platform.runLater(() -> {
            ProcessRow row = procesosViewModel.selectedVisibleRow().orElse(null);
            if (row == null) {
                tablaProcesos.getSelectionModel().clearSelection();
            } else {
                tablaProcesos.getSelectionModel().select(row);
            }
        });
    }

    private void updateInventoryStatus() {
        if (dashboardViewModel.monitoringSequenceProperty().get() == 0) {
            inventoryStatusLabel.setText("Esperando inventario");
        } else if (dashboardViewModel.processInventoryAvailableProperty().get()) {
            inventoryStatusLabel.setText("Inventario actualizado");
        } else {
            inventoryStatusLabel.setText("Inventario N/D; se muestran los últimos procesos conocidos");
        }
    }

    private void refreshProcessSummary() {
        List<ProcessRow> rows = List.copyOf(dashboardViewModel.getProcesses());
        memoryPieChart.getData().setAll(ProcessMemorySummary.topSlices(rows, PIE_PROCESS_LIMIT).stream()
                .map(slice -> new PieChart.Data(slice.label(), slice.mebibytes())).toList());
        ramTotalLabel.setText(String.format(Locale.ROOT, "%.0f MiB", ProcessMemorySummary.totalListedMebibytes(rows)));
        ramCountLabel.setText(String.valueOf(rows.size()));
    }

    /** La identidad de la fila queda capturada antes del diálogo modal, incluso si la tabla cambia. */
    private void confirmAndTerminate(ProcessRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar finalización");
        confirm.setHeaderText("¿Finalizar el proceso \"" + row.name() + "\" (PID " + row.pid() + ")?");
        confirm.setContentText("Esta acción no se puede deshacer.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                TerminationResult result = dashboardViewModel.terminateProcess(row);
                if (result.status() != TerminationStatus.SUCCESS) showTerminationError(row, result);
            }
        });
    }

    private void showTerminationError(ProcessRow row, TerminationResult result) {
        TerminationStatus status = result.status();
        Alert error = new Alert(status.requiresRefresh() ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
        error.setTitle("No se pudo finalizar el proceso");
        error.setHeaderText(row.name() + " (PID " + row.pid() + ")");
        String message = switch (status) {
            case REJECTED_SYSTEM_PROCESS -> "MACE bloqueó la operación porque es un proceso del sistema o el propio MACE.";
            case REJECTED_PROTECTED_PROCESS -> "La DLL protegió este proceso; no se intentará cerrarlo por PID.";
            case ACCESS_DENIED -> "Windows denegó el acceso. Puede requerirse ejecutar MACE como administrador.";
            case PROCESS_EXITED -> "El proceso ya terminó. La lista se actualizará.";
            case PID_REUSED -> "El PID ahora pertenece a otro proceso. La lista se actualizará sin reintentar.";
            case IDENTITY_REQUIRED -> "La fila no conserva la identidad completa; actualiza la lista.";
            case FAILED -> "Windows no pudo completar la operación.";
            case SUCCESS -> "El proceso se finalizó.";
        };
        if (result.win32Error() != 0) {
            message += " Código Win32: " + Integer.toUnsignedString(result.win32Error()) + ".";
        }
        error.setContentText(message);
        error.showAndWait();
    }

    private void bindBlackbox() {
        dataSourceLabel.textProperty().bind(dashboardViewModel.dataSourceDescriptionProperty());
        blackboxStatusLabel.textProperty().bind(dashboardViewModel.blackboxStatusProperty());
        blackboxCountsLabel.textProperty().bind(dashboardViewModel.blackboxCountsProperty());
        blackboxLocationLabel.textProperty().bind(dashboardViewModel.blackboxLocationProperty());
    }
}
