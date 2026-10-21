package com.mace.presentation.view.component;

import com.mace.presentation.viewmodel.MetricDisplay;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.Objects;

/** Tarjeta que muestra el valor V2 o el motivo exacto por el que no está disponible. */
public final class MetricCard extends VBox implements AutoCloseable {

    private final Label value = new Label();
    private final Label caption = new Label();

    public MetricCard(String title, ObservableValue<MetricDisplay> reading) {
        this(title, reading, null);
    }

    public MetricCard(String title, ObservableValue<MetricDisplay> reading,
                      ObservableValue<String> captionText) {
        Objects.requireNonNull(title, "title no puede ser null");
        Objects.requireNonNull(reading, "reading no puede ser null");
        getStyleClass().add("metric-card");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(6);
        setMinWidth(190);
        setPrefWidth(210);

        Label name = new Label(title);
        name.getStyleClass().add("metric-card-title");
        value.getStyleClass().add("metric-card-value");
        value.setWrapText(true);
        value.textProperty().bind(Bindings.createStringBinding(
                () -> reading.getValue() == null ? "N/D" : reading.getValue().text(), reading));
        if (captionText != null) {
            caption.getStyleClass().add("metric-card-caption");
            caption.setWrapText(true);
            caption.textProperty().bind(captionText);
            getChildren().addAll(name, value, caption);
        } else {
            getChildren().addAll(name, value);
        }
    }

    @Override
    public void close() {
        value.textProperty().unbind();
        caption.textProperty().unbind();
    }
}
