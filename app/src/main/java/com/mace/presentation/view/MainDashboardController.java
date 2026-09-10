package com.mace.presentation.view;

import com.mace.presentation.view.component.MetricCard;
import com.mace.presentation.viewmodel.MainDashboardViewModel;
import com.mace.presentation.viewmodel.ProcessRow;
import com.mace.presentation.viewmodel.ProcessTableViewModel;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

public class MainDashboardController {

    @FXML private HBox metricsBar;
    @FXML private TextField searchField;
    @FXML private TableView<ProcessRow> processTable;
    @FXML private TableColumn<ProcessRow, Number> pidColumn;
    @FXML private TableColumn<ProcessRow, String> nameColumn;
    @FXML private TableColumn<ProcessRow, Number> memoryColumn;
    @FXML private TableColumn<ProcessRow, Void> actionColumn;

    private final MainDashboardViewModel dashboardViewModel = new MainDashboardViewModel();
    private ProcessTableViewModel tableViewModel;

    private MetricCard cpuTempCard;
    private MetricCard cpuWattsCard;
    private MetricCard gpuTempCard;
    private MetricCard gpuWattsCard;

    @FXML
    public void initialize() {
        buildMetricCards();
        configureTable();
        bindSearch();
        bindTelemetry();

        tableViewModel.setOnTerminateRequested(this::confirmAndTerminate);

        dashboardViewModel.startMockPolling();
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
                    ProcessRow row = getTableView().getItems().get(getIndex());
                    tableViewModel.requestTerminate(row);
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
                cpuWattsCard.updateValue(n.doubleValue(), 65));
        dashboardViewModel.gpuTempProperty().addListener((obs, o, n) ->
                gpuTempCard.updateValue(n.doubleValue(), 90));
        dashboardViewModel.gpuWattsProperty().addListener((obs, o, n) ->
                gpuWattsCard.updateValue(n.doubleValue(), 200));
    }

    /**
     * Regla de seguridad del proyecto: NUNCA terminar un proceso sin confirmacion
     * explicita del usuario (nada de auto-kill).
     */
    private void confirmAndTerminate(ProcessRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar finalizacion");
        confirm.setHeaderText("¿Finalizar el proceso \"" + row.name() + "\" (PID " + row.pid() + ")?");
        confirm.setContentText("Esta accion no se puede deshacer.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // TODO: reemplazar por TerminateProcessService real cuando Dev2/Dev3 esten integrados.
                dashboardViewModel.terminateProcessMock(row);
            }
        });
    }
}