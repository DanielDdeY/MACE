package com.mace.presentation.view.component;

import com.mace.presentation.viewmodel.BlackboxHistoryViewModel;
import com.mace.presentation.viewmodel.BlackboxHistoryViewModel.RecordRow;
import com.mace.presentation.viewmodel.BlackboxHistoryViewModel.SessionFile;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Objects;

/** Lista de sesiones y últimos registros; toda la E/S la delega al ViewModel. */
public final class BlackboxHistoryPane extends VBox {

    public BlackboxHistoryPane(BlackboxHistoryViewModel model) {
        Objects.requireNonNull(model, "model");
        setSpacing(10);
        setPadding(new Insets(12, 0, 0, 0));

        Label heading = new Label("Sesiones guardadas");
        heading.getStyleClass().add("chart-card-title");
        Label explanation = new Label("La caja negra guarda lecturas y avisos para revisar qué ocurrió. "
                + "Un archivo sin cierre registrado puede seguir abierto en otra ventana de MACE.");
        explanation.getStyleClass().add("section-subtitle");
        explanation.setWrapText(true);
        Button refresh = new Button("Actualizar archivos");
        refresh.setOnAction(event -> model.refresh());
        CheckBox eventsOnly = new CheckBox("Solo eventos y pérdidas");
        eventsOnly.selectedProperty().addListener((obs, previous, next) -> model.setOnlyEventsAndGaps(next));
        HBox tools = new HBox(12, refresh, eventsOnly);
        tools.getStyleClass().add("blackbox-tools");

        Label status = new Label();
        status.getStyleClass().add("section-subtitle");
        status.setWrapText(true);
        status.textProperty().bind(model.statusProperty());

        ListView<SessionFile> sessionList = new ListView<>(model.sessions());
        sessionList.setPrefHeight(160);
        sessionList.setPlaceholder(new Label("No hay archivos de sesión"));
        sessionList.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(SessionFile file, boolean empty) {
                super.updateItem(file, empty);
                if (empty || file == null) {
                    setText(null);
                    return;
                }
                setText(file.label() + "\n" + file.records() + " registros recuperables · "
                        + file.gaps() + " huecos · " + file.lostRecords() + " perdidos");
                setWrapText(true);
            }
        });
        sessionList.getSelectionModel().selectedItemProperty().addListener((obs, previous, next) -> {
            if (next != null && !next.equals(model.selectedSessionProperty().get())) model.select(next);
        });
        model.selectedSessionProperty().addListener((obs, previous, next) -> {
            if (next == null) sessionList.getSelectionModel().clearSelection();
            else sessionList.getSelectionModel().select(next);
        });

        Label selected = new Label();
        selected.getStyleClass().add("device-provider");
        selected.setWrapText(true);
        selected.textProperty().bind(model.selectedSummaryProperty());

        TableView<RecordRow> table = new TableView<>(model.records());
        table.setPrefHeight(300);
        table.setPlaceholder(new Label("Sin registros para mostrar"));
        table.getStyleClass().add("blackbox-table");
        TableColumn<RecordRow, String> sequence = column("#", 60, row -> Long.toString(row.sequence()));
        TableColumn<RecordRow, String> time = column("Hora", 150, RecordRow::time);
        TableColumn<RecordRow, String> type = column("Tipo", 170, RecordRow::type);
        TableColumn<RecordRow, String> detail = column("Detalle", 360, RecordRow::detail);
        TableColumn<RecordRow, String> lost = column("Perdidos", 80,
                row -> row.gap() ? Long.toString(row.lostRecords()) : "");
        table.getColumns().addAll(sequence, time, type, detail, lost);
        table.setRowFactory(view -> new TableRow<>() {
            @Override protected void updateItem(RecordRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("blackbox-gap-row");
                if (!empty && item != null && item.gap()) getStyleClass().add("blackbox-gap-row");
            }
        });

        Label note = new Label("Las muestras CPU, GPU, RAM y proceso contienen datos binarios; "
                + "solo los eventos de MACE se muestran como texto. La tabla presenta hasta "
                + BlackboxHistoryViewModel.MAX_VISIBLE_RECORDS + " registros recientes.");
        note.getStyleClass().add("section-subtitle");
        note.setWrapText(true);
        getChildren().addAll(heading, explanation, tools, status, sessionList, selected, table, note);
    }

    private static TableColumn<RecordRow, String> column(String title, double width,
                                                          java.util.function.Function<RecordRow, String> value) {
        TableColumn<RecordRow, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        return column;
    }
}
