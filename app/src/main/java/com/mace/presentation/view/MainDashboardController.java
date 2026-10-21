package com.mace.presentation.view;

import com.mace.application.usecase.TerminationResult;
import com.mace.application.usecase.TerminationStatus;
import com.mace.presentation.view.component.GaugeDial;
import com.mace.presentation.view.component.NavIcon;
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
    @FXML private Label dataSourceLabel;

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
    @FXML private LineChart<Number, Number> cpuUsageChart;
    @FXML private LineChart<Number, Number> gpuTempChart;
    @FXML private Label blackboxStatusLabel;
    @FXML private Label blackboxCountsLabel;
    @FXML private Label blackboxLocationLabel;

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
    private GaugeDial cpuUsageGauge;
    private GaugeDial gpuTempGauge;
    private GaugeDial gpuWattsGauge;

    public MainDashboardController(MainDashboardViewModel dashboardViewModel) {
        this.dashboardViewModel = Objects.requireNonNull(dashboardViewModel, "dashboardViewModel no puede ser null");
    }

    @FXML
    public void initialize() {
        buildGauges();
        buildNavIcons();
        configureNavigation();
        configureHistoryCharts();
        configureProcesosTable();
        bindTelemetry();
        bindProcessSummary();
        bindStatsPanels();
        bindBlackbox();

        dashboardViewModel.start();
    }

    // ==================== NAVEGACION ====================

    private void buildNavIcons() {
        navCpu.setGraphic(NavIcon.cpu());
        navGpu.setGraphic(NavIcon.gpu());
        navHistorial.setGraphic(NavIcon.historial());
        navProcesos.setGraphic(NavIcon.procesos());
    }

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
        cpuUsageGauge = new GaugeDial("Uso CPU", "%", 100);
        cpuGaugesBox.getChildren().addAll(cpuUsageGauge, cpuTempGauge, cpuWattsGauge);

        gpuTempGauge = new GaugeDial("Temp. GPU", "°C", 100);
        gpuWattsGauge = new GaugeDial("Consumo GPU", "W", 200);
        gpuGaugesBox.getChildren().addAll(gpuTempGauge, gpuWattsGauge);
    }

    private void bindTelemetry() {
        dashboardViewModel.cpuTempProperty().addListener((obs, o, n) -> {
            if (dashboardViewModel.cpuSensorAvailableProperty().get()) {
                cpuTempGauge.setValue(n.doubleValue());
            }
        });
        dashboardViewModel.cpuWattsProperty().addListener((obs, o, n) -> {
            if (dashboardViewModel.cpuPowerAvailableProperty().get()) {
                cpuWattsGauge.setValue(n.doubleValue());
            }
        });
        dashboardViewModel.cpuUsageProperty().addListener((obs, o, n) -> {
            if (dashboardViewModel.cpuUsageAvailableProperty().get()) {
                cpuUsageGauge.setValue(n.doubleValue());
            }
        });
        dashboardViewModel.gpuTempProperty().addListener((obs, o, n) -> {
            if (dashboardViewModel.gpuAvailableProperty().get()) {
                gpuTempGauge.setValue(n.doubleValue());
            }
        });
        dashboardViewModel.gpuWattsProperty().addListener((obs, o, n) -> {
            if (dashboardViewModel.gpuPowerAvailableProperty().get()) {
                gpuWattsGauge.setValue(n.doubleValue());
            }
        });

        dashboardViewModel.cpuUsageAvailableProperty().addListener((obs, o, available) -> {
            if (!available) cpuUsageGauge.setUnavailable();
            else cpuUsageGauge.setValue(dashboardViewModel.cpuUsageProperty().get());
        });
        dashboardViewModel.cpuPowerAvailableProperty().addListener((obs, o, available) -> {
            if (!available) cpuWattsGauge.setUnavailable();
            else cpuWattsGauge.setValue(dashboardViewModel.cpuWattsProperty().get());
        });
        dashboardViewModel.gpuPowerAvailableProperty().addListener((obs, o, available) -> {
            if (!available) gpuWattsGauge.setUnavailable();
            else gpuWattsGauge.setValue(dashboardViewModel.gpuWattsProperty().get());
        });

        dashboardViewModel.gpuAvailableProperty().addListener((obs, o, available) -> {
            if (!available) {
                gpuTempGauge.setUnavailable();
            } else {
                gpuTempGauge.setValue(dashboardViewModel.gpuTempProperty().get());
            }
        });

        // Cada sensor informa su disponibilidad por separado. Un valor no leído se
        // muestra como N/D en vez de un cero que parecería una lectura real.
        dashboardViewModel.cpuSensorAvailableProperty().addListener((obs, o, available) -> {
            if (!available) {
                cpuTempGauge.setUnavailable();
            } else {
                cpuTempGauge.setValue(dashboardViewModel.cpuTempProperty().get());
            }
            updateCpuStats();
        });

        // Hasta el primer sondeo no hay lectura válida de ningún fabricante.
        cpuTempGauge.setUnavailable();
        cpuWattsGauge.setUnavailable();
        cpuUsageGauge.setUnavailable();
        gpuTempGauge.setUnavailable();
        gpuWattsGauge.setUnavailable();
    }

    // ==================== HISTORIAL (graficos separados) ====================

    private void configureHistoryCharts() {
        cpuTempChart.getData().add(dashboardViewModel.getCpuTempHistory());
        cpuUsageChart.getData().add(dashboardViewModel.getCpuUsageHistory());
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
                gpuFanLabel.setText(dashboardViewModel.gpuFanAvailableProperty().get()
                        ? n.intValue() + " RPM" : "N/D"));
        dashboardViewModel.gpuFanAvailableProperty().addListener((obs, o, available) ->
                gpuFanLabel.setText(available ? dashboardViewModel.gpuFanRpmProperty().get() + " RPM" : "N/D"));

        dashboardViewModel.gpuDetectedProperty().addListener((obs, o, detected) -> updateGpuAvailability());
        dashboardViewModel.gpuAvailableProperty().addListener((obs, o, available) -> {
            updateGpuAvailability();
            updateGpuStats();
        });

        updateGpuAvailability();
        gpuFanLabel.setText("N/D");
        gpuStatusLabel.setText("N/D");
        gpuMaxLabel.setText("N/D");
    }

    private void updateGpuAvailability() {
        gpuAvailableLabel.setText(!dashboardViewModel.gpuDetectedProperty().get() ? "No detectada"
                : dashboardViewModel.gpuAvailableProperty().get() ? "Temperatura disponible" : "Sensor N/D");
    }

    private void bindBlackbox() {
        dataSourceLabel.textProperty().bind(dashboardViewModel.dataSourceDescriptionProperty());
        blackboxStatusLabel.textProperty().bind(dashboardViewModel.blackboxStatusProperty());
        blackboxCountsLabel.textProperty().bind(dashboardViewModel.blackboxCountsProperty());
        blackboxLocationLabel.textProperty().bind(dashboardViewModel.blackboxLocationProperty());
    }

    private void updateCpuStats() {
        List<XYChart.Data<Number, Number>> points = dashboardViewModel.getCpuTempHistory().getData();
        if (!dashboardViewModel.cpuSensorAvailableProperty().get()) {
            cpuStatusLabel.getStyleClass().removeAll("status-ok", "status-warning", "status-critical");
            cpuStatusLabel.setText("N/D");
            cpuMaxLabel.setText("N/D");
            cpuAvgLabel.setText("N/D");
            return;
        }
        if (points.isEmpty()) {
            return;
        }
        double current = dashboardViewModel.cpuTempProperty().get();
        applyStatusLabel(cpuStatusLabel, current);
        applyMinMaxAvg(points, cpuMaxLabel, cpuAvgLabel);
    }

    private void updateGpuStats() {
        List<XYChart.Data<Number, Number>> points = dashboardViewModel.getGpuTempHistory().getData();
        if (!dashboardViewModel.gpuAvailableProperty().get()) {
            gpuStatusLabel.getStyleClass().removeAll("status-ok", "status-warning", "status-critical");
            gpuStatusLabel.setText("N/D");
            gpuMaxLabel.setText("N/D");
            return;
        }
        if (points.isEmpty()) {
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
                setText(empty || value == null ? null : Double.isFinite(value.doubleValue())
                        ? String.format("%.1f MB", value.doubleValue()) : "N/D");
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
                        .filter(ProcessRow::hasWorkingSet)
                        .map(p -> new PieChart.Data(p.name(), p.workingSetMb()))
                        .toList()
        );

        double totalMb = dashboardViewModel.getProcesses().stream()
                .filter(ProcessRow::hasWorkingSet)
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
                    showTerminationError(row, result);
                }
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
}
