package com.mace.presentation.view;

import com.mace.application.usecase.TerminationResult;
import com.mace.application.usecase.TerminationStatus;
import com.mace.presentation.view.component.MetricCard;
import com.mace.presentation.viewmodel.MainDashboardViewModel;
import com.mace.presentation.viewmodel.ProcessRow;
import com.mace.presentation.viewmodel.ProcessTableViewModel;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.util.Objects;

public final class MainDashboardController {

    @FXML private HBox metricsBar;
    @FXML private TextField searchField;
    @FXML private TableView<ProcessRow> processTable;
    @FXML private TableColumn<ProcessRow, Number> pidColumn;
    @FXML private TableColumn<ProcessRow, String> nameColumn;
    @FXML private TableColumn<ProcessRow, Number> memoryColumn;
    @FXML private TableColumn<ProcessRow, Void> actionColumn;

    private final MainDashboardViewModel dashboardViewModel;
    private ProcessTableViewModel tableViewModel;

    private MetricCard cpuTempCard;
    private MetricCard cpuWattsCard;
    private MetricCard gpuTempCard;
    private MetricCard gpuWattsCard;

    public MainDashboardController(MainDashboardViewModel dashboardViewModel) {
        this.dashboardViewModel = Objects.requireNonNull(dashboardViewModel, "dashboardViewModel no puede ser null");
    }

    @FXML
    public void initialize() {
        buildMetricCards();
        configureTable();
        bindSearch();
        bindTelemetry();
        tableViewModel.setOnTerminateRequested(this::confirmAndTerminate);
        dashboardViewModel.start();
    }

    private void buildMetricCards() {
        cpuTempCard = new MetricCard("Temp. CPU", "°C");
        cpuWattsCard = new MetricCard("Consumo CPU", "W");
        gpuTempCard = new MetricCard("Temp. GPU", "°C");
        gpuWattsCard = new MetricCard("Consumo GPU", "W");
        metricsBar.getChildren().addAll(cpuTempCard, cpuWattsCard, gpuTempCard, gpuWattsCard);
    }

    private void configureTable() {
        tableViewModel = new ProcessTableViewModel(dashboardViewModel.getProcesses());

        pidColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleLongProperty(data.getValue().pid()));
        nameColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().name()));
        memoryColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleDoubleProperty(data.getValue().workingSetMb()));
        memoryColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : String.format("%.1f MB", value.doubleValue()));
            }
        });

        actionColumn.setCellFactory(col -> new TableCell<>() {
            private final Button terminateButton = new Button("Finalizar Proceso");
            {
                terminateButton.getStyleClass().add("danger-button");
                terminateButton.setOnAction(e -> {
                    int index = getIndex();
                    if (index >= 0 && index < getTableView().getItems().size()) {
                        tableViewModel.requestTerminate(getTableView().getItems().get(index));
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : terminateButton);
            }
        });

        processTable.setItems(tableViewModel.getSortedProcesses());
        tableViewModel.getSortedProcesses().comparatorProperty().bind(processTable.comparatorProperty());
    }

    private void bindSearch() {
        ChangeListener<String> listener = (obs, oldVal, newVal) ->
                tableViewModel.searchTextProperty().set(newVal);
        searchField.textProperty().addListener(listener);
    }

    private void bindTelemetry() {
        dashboardViewModel.cpuTempProperty().addListener((obs, o, n) ->
                cpuTempCard.updateValue(n.doubleValue(), 100));
        dashboardViewModel.cpuWattsProperty().addListener((obs, o, n) ->
                cpuWattsCard.updateValue(n.doubleValue(), 180));
        dashboardViewModel.gpuTempProperty().addListener((obs, o, n) ->
                gpuTempCard.updateValue(n.doubleValue(), 100));
        dashboardViewModel.gpuWattsProperty().addListener((obs, o, n) ->
                gpuWattsCard.updateValue(n.doubleValue(), 350));
        dashboardViewModel.gpuAvailableProperty().addListener((obs, oldValue, available) -> {
            if (!available) {
                gpuTempCard.setUnavailable();
                gpuWattsCard.setUnavailable();
            }
        });
    }

    /** La terminación solo ocurre después de una confirmación explícita del usuario. */
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
