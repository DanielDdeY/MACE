package com.mace.presentation.view;

import com.mace.application.usecase.TerminationResult;
import com.mace.application.usecase.TerminationStatus;
import com.mace.presentation.view.component.GaugeDial;
import com.mace.presentation.viewmodel.MainDashboardViewModel;
import com.mace.presentation.viewmodel.ProcessRow;
import com.mace.presentation.viewmodel.ProcessTableViewModel;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;

/**
 * Controlador del dashboard principal.
 *
 * La interfaz se organiza en apartados independientes (CPU, GPU, Historial,
 * y Procesos) navegables desde el menu lateral. Solo un apartado esta
 * visible a la vez.
 *
 * El ViewModel se inyecta por constructor (ver controllerFactory en
 * MaceApplication) y ya viene conectado a los servicios reales de dominio;
 * este controlador no crea ningun dato, solo lo muestra.
 *
 * Regla de seguridad del proyecto: la accion de finalizar un proceso vive
 * en el apartado Procesos y siempre exige confirmacion explicita.
 */
public final class MainDashboardController {

    /** Mismos umbrales que domain/service/ThermalAlertEvaluator.java, para mostrar el mismo criterio en la UI. */
    private static final float WARNING_THRESHOLD = 75.0f;
    private static final float CRITICAL_THRESHOLD = 85.0f;

    // ---- Navegacion ----
    @FXML private ToggleButton navCpu;
    @FXML private ToggleButton navGpu;
    @FXML private ToggleButton navHistorial;
    @FXML private ToggleButton navProcesos;

    // ---- Apartados ----
    @FXML private VBox paneCpu;
    @FXML private VBox paneGpu;
    @FXML private VBox paneHistorial;
    @FXML private VBox paneProcesos;

    // ---- CPU / GPU ----
    @FXML private HBox cpuGaugesBox;
    @FXML private HBox gpuGaugesBox;
    @FXML private Label cpuStatusLabel;
    @FXML private Label cpuMaxLabel;
    @FXML private Label cpuAvgLabel;
    @FXML private Label gpuStatusLabel;
    @FXML private Label gpuFanLabel;
    @FXML private Label gpuAvailableLabel;
    @FXML private Label gpuMaxLabel;

    // ---- Historial ----
    @FXML private LineChart<Number, Number> cpuTempChart;
    @FXML private LineChart<Number, Number> gpuTempChart;

    // ---- Procesos ----
    @FXML private TextField searchProcesos;
    @FXML private TableView<ProcessRow> tablaProcesos;
    @FXML private TableColumn<ProcessRow, Number> procPidColumn;
    @FXML private TableColumn<ProcessRow, String> procNombreColumn;
    @FXML private TableColumn<ProcessRow, String> procRutaColumn;
    @FXML private TableColumn<ProcessRow, Number> procMemoriaColumn;
    @FXML private TableColumn<ProcessRow, Void> procAccionColumn;

    // ---- Resumen de procesos ----
    @FXML private PieChart memoryPieChart;
    @FXML private Label ramTotalLabel;
    @FXML private Label ramCountLabel;

    private final MainDashboardViewModel dashboardViewModel;
    private ProcessTableViewModel procesosViewModel;

    private GaugeDial cpuTempGauge;
    private GaugeDial cpuWattsGauge;
    private GaugeDial gpuTempGauge;
    private GaugeDial gpuWattsGauge;

    public MainDashboardController(MainDashboardViewModel dashboardViewModel) {
        this.dashboardViewModel = Objects.requireNonNull(dashboardViewModel, "dashboardViewModel no puede ser null");
    }

    @FXML
    public void initialize() {
        buildGauges();
        configureNavigation();
        configureHistoryCharts();
        configureProcesosTable();
        bindTelemetry();
        bindProcessSummary();
        bindStatsPanels();

        dashboardViewModel.start();
    }

    // ==================== NAVEGACION ====================

    private void configureNavigation() {
        ToggleGroup group = new ToggleGroup();
        navCpu.setToggleGroup(group);
        navGpu.setToggleGroup(group);
        navHistorial.setToggleGroup(group);
        navProcesos.setToggleGroup(group);

        navCpu.setOnAction(e -> showSection(paneCpu));
        navGpu.setOnAction(e -> showSection(paneGpu));
        navHistorial.setOnAction(e -> showSection(paneHistorial));
        navProcesos.setOnAction(e -> showSection(paneProcesos));

        // Evita que el usuario pueda dejar los botones sin seleccionar.
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });

        navCpu.setSelected(true);
        showSection(paneCpu);
    }

    /** Muestra un unico apartado y oculta el resto (sin dejar espacio en blanco). */
    private void showSection(Region target) {
        List<Region> sections = List.of(paneCpu, paneGpu, paneHistorial, paneProcesos);
        for (Region section : sections) {
            boolean visible = section == target;
            section.setVisible(visible);
            section.setManaged(visible);
        }
    }

    // ==================== CPU / GPU ====================

    private void buildGauges() {
        cpuTempGauge = new GaugeDial("Temp. CPU", "°C", 100);
        cpuWattsGauge = new GaugeDial("Consumo CPU", "W", 65);
        cpuGaugesBox.getChildren().addAll(cpuTempGauge, cpuWattsGauge);

        gpuTempGauge = new GaugeDial("Temp. GPU", "°C", 100);
        gpuWattsGauge = new GaugeDial("Consumo GPU", "W", 200);
        gpuGaugesBox.getChildren().addAll(gpuTempGauge, gpuWattsGauge);
    }

    private void bindTelemetry() {
        dashboardViewModel.cpuTempProperty().addListener((obs, o, n) -> cpuTempGauge.setValue(n.doubleValue()));
        dashboardViewModel.cpuWattsProperty().addListener((obs, o, n) -> cpuWattsGauge.setValue(n.doubleValue()));
        dashboardViewModel.gpuTempProperty().addListener((obs, o, n) -> gpuTempGauge.setValue(n.doubleValue()));
        dashboardViewModel.gpuWattsProperty().addListener((obs, o, n) -> gpuWattsGauge.setValue(n.doubleValue()));

        dashboardViewModel.gpuAvailableProperty().addListener((obs, o, available) -> {
            if (!available) {
                gpuTempGauge.setUnavailable();
                gpuWattsGauge.setUnavailable();
            } else {
                gpuTempGauge.setValue(dashboardViewModel.gpuTempProperty().get());
                gpuWattsGauge.setValue(dashboardViewModel.gpuWattsProperty().get());
            }
        });

        // En una Radeon/AMD gpuAvailable empieza y permanece en false; inicializamos
        // explícitamente el estado para que la UI muestre N/D desde el primer frame.
        if (!dashboardViewModel.gpuAvailableProperty().get()) {
            gpuTempGauge.setUnavailable();
            gpuWattsGauge.setUnavailable();
        }
    }

    // ==================== HISTORIAL (graficos separados) ====================

    private void configureHistoryCharts() {
        cpuTempChart.getData().add(dashboardViewModel.getCpuTempHistory());
        gpuTempChart.getData().add(dashboardViewModel.getGpuTempHistory());
    }

    // ==================== ESTADISTICAS EN VIVO (CPU / GPU) ====================

    /**
     * Se apoya en el mismo historial que alimenta los graficos de linea, asi que
     * no pide nada nuevo al backend: solo calcula sobre datos reales ya recibidos.
     */
    private void bindStatsPanels() {
        dashboardViewModel.getCpuTempHistory().getData()
                .addListener((ListChangeListener<XYChart.Data<Number, Number>>) change -> updateCpuStats());

        dashboardViewModel.getGpuTempHistory().getData()
                .addListener((ListChangeListener<XYChart.Data<Number, Number>>) change -> updateGpuStats());

        dashboardViewModel.gpuFanRpmProperty().addListener((obs, o, n) ->
                gpuFanLabel.setText(n.intValue() + " RPM"));

        dashboardViewModel.gpuAvailableProperty().addListener((obs, o, available) -> {
            gpuAvailableLabel.setText(available ? "Disponible" : "No detectada");
            if (!available) {
                gpuFanLabel.setText("N/D");
            }
        });

        gpuAvailableLabel.setText(dashboardViewModel.gpuAvailableProperty().get() ? "Disponible" : "No detectada");
        if (!dashboardViewModel.gpuAvailableProperty().get()) {
            gpuFanLabel.setText("N/D");
            gpuStatusLabel.setText("N/D");
            gpuMaxLabel.setText("N/D");
        }
    }

    private void updateCpuStats() {
        List<XYChart.Data<Number, Number>> points = dashboardViewModel.getCpuTempHistory().getData();
        if (points.isEmpty()) {
            return;
        }
        double current = dashboardViewModel.cpuTempProperty().get();
        applyStatusLabel(cpuStatusLabel, current);
        applyMinMaxAvg(points, cpuMaxLabel, cpuAvgLabel);
    }

    private void updateGpuStats() {
        List<XYChart.Data<Number, Number>> points = dashboardViewModel.getGpuTempHistory().getData();
        if (points.isEmpty()) {
            return;
        }
        if (!dashboardViewModel.gpuAvailableProperty().get()) {
            gpuStatusLabel.setText("N/D");
            gpuMaxLabel.setText("N/D");
            return;
        }
        double current = dashboardViewModel.gpuTempProperty().get();
        applyStatusLabel(gpuStatusLabel, current);
        applyMinMaxAvg(points, gpuMaxLabel, null);
    }

    /** Mismo criterio que domain/service/ThermalAlertEvaluator: NONE / WARNING / CRITICAL. */
    private void applyStatusLabel(Label label, double temperature) {
        label.getStyleClass().removeAll("status-ok", "status-warning", "status-critical");
        if (temperature > CRITICAL_THRESHOLD) {
            label.setText("Crítico");
            label.getStyleClass().add("status-critical");
        } else if (temperature > WARNING_THRESHOLD) {
            label.setText("Advertencia");
            label.getStyleClass().add("status-warning");
        } else {
            label.setText("Normal");
            label.getStyleClass().add("status-ok");
        }
    }

    private void applyMinMaxAvg(List<XYChart.Data<Number, Number>> points, Label maxLabel, Label avgLabel) {
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0;
        for (XYChart.Data<Number, Number> point : points) {
            double value = point.getYValue().doubleValue();
            max = Math.max(max, value);
            sum += value;
        }
        maxLabel.setText(String.format("%.0f°C", max));
        if (avgLabel != null) {
            avgLabel.setText(String.format("%.0f°C", sum / points.size()));
        }
    }

    // ==================== PROCESOS ====================

    private void configureProcesosTable() {
        procesosViewModel = new ProcessTableViewModel(dashboardViewModel.getProcesses());

        procPidColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleLongProperty(data.getValue().pid()));
        procNombreColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().name()));
        procRutaColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().fullPath()));
        procMemoriaColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleDoubleProperty(data.getValue().workingSetMb()));
        procMemoriaColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : String.format("%.1f MB", value.doubleValue()));
            }
        });

        procAccionColumn.setCellFactory(col -> new TableCell<>() {
            private final Button terminateButton = new Button("Finalizar Proceso");
            {
                terminateButton.getStyleClass().add("danger-button");
                terminateButton.setOnAction(e -> {
                    int index = getIndex();
                    if (index >= 0 && index < getTableView().getItems().size()) {
                        procesosViewModel.requestTerminate(getTableView().getItems().get(index));
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : terminateButton);
            }
        });

        searchProcesos.textProperty().addListener((obs, oldVal, newVal) ->
                procesosViewModel.searchTextProperty().set(newVal));

        tablaProcesos.setItems(procesosViewModel.getSortedProcesses());
        procesosViewModel.getSortedProcesses().comparatorProperty().bind(tablaProcesos.comparatorProperty());
        procesosViewModel.setOnTerminateRequested(this::confirmAndTerminate);
    }

    private void bindProcessSummary() {
        refreshProcessSummary();
        dashboardViewModel.getProcesses().addListener(
                (ListChangeListener<ProcessRow>) change -> refreshProcessSummary());
    }

    private void refreshProcessSummary() {
        memoryPieChart.getData().setAll(
                dashboardViewModel.getProcesses().stream()
                        .map(p -> new PieChart.Data(p.name(), p.workingSetMb()))
                        .toList()
        );

        double totalMb = dashboardViewModel.getProcesses().stream()
                .mapToDouble(ProcessRow::workingSetMb)
                .sum();
        ramTotalLabel.setText(String.format("%.0f MB", totalMb));
        ramCountLabel.setText(String.valueOf(dashboardViewModel.getProcesses().size()));
    }

    /**
     * Regla de seguridad del proyecto: NUNCA terminar un proceso sin confirmacion
     * explicita del usuario (nada de auto-kill). Si el caso de uso real rechaza o
     * falla la terminacion, se informa al usuario en vez de fallar en silencio.
     */
    private void confirmAndTerminate(ProcessRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar finalización");
        confirm.setHeaderText("¿Finalizar el proceso \"" + row.name() + "\" (PID " + row.pid() + ")?");
        confirm.setContentText("Esta acción no se puede deshacer.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                TerminationResult result = dashboardViewModel.terminateProcess(row);
                if (result.status() != TerminationStatus.SUCCESS) {
                    showTerminationError(row, result.status());
                }
            }
        });
    }

    private void showTerminationError(ProcessRow row, TerminationStatus status) {
        Alert error = new Alert(Alert.AlertType.ERROR);
        error.setTitle("No se pudo finalizar el proceso");
        error.setHeaderText(row.name() + " (PID " + row.pid() + ")");
        error.setContentText(status == TerminationStatus.REJECTED_SYSTEM_PROCESS
                ? "MACE bloqueó la operación porque el PID está protegido por seguridad."
                : "Windows rechazó la operación, el proceso ya terminó o no hay permisos suficientes.");
        error.showAndWait();
    }
}