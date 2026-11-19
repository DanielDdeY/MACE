package com.mace.presentation.view.component;

import com.mace.presentation.viewmodel.MetricDisplay;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.Objects;

/** Termómetro vertical para la temperatura real de CPU. */
public final class CpuThermometer extends HBox implements AutoCloseable {

    private static final double MIN_TEMPERATURE = 0.0;
    private static final double MAX_TEMPERATURE = 100.0;

    private final ObservableValue<MetricDisplay> reading;
    private final Label valueLabel = new Label("N/D");
    private final Label rangeLabel = new Label("ESPERANDO SENSOR");
    private final StackPane scale = new StackPane();
    private final Region marker = new Region();
    private final ChangeListener<MetricDisplay> readingListener;
    private Timeline markerAnimation;
    private MetricDisplay currentReading;

    public CpuThermometer(ObservableValue<MetricDisplay> reading) {
        this.reading = Objects.requireNonNull(reading, "reading no puede ser null");

        getStyleClass().add("cpu-thermometer");
        setAlignment(Pos.CENTER);
        setSpacing(12);
        setPadding(new Insets(12));
        setMinWidth(170);
        setPrefWidth(190);
        setMinHeight(150);

        Label heading = new Label("CPU TEMP");
        heading.getStyleClass().add("cpu-thermometer-heading");

        Label scaleTop = new Label("100°");
        scaleTop.getStyleClass().add("cpu-thermometer-scale-label");
        Label scaleBottom = new Label("0°");
        scaleBottom.getStyleClass().add("cpu-thermometer-scale-label");

        VBox bands = new VBox(0);
        bands.getStyleClass().add("cpu-thermometer-bands");
        bands.getChildren().addAll(
                segment("cpu-thermometer-red"),
                segment("cpu-thermometer-orange"),
                segment("cpu-thermometer-yellow"),
                segment("cpu-thermometer-lime"),
                segment("cpu-thermometer-green")
        );
        bands.setMinHeight(0);
        bands.setPrefHeight(116);
        bands.setMaxHeight(116);

        marker.getStyleClass().add("cpu-thermometer-marker");
        marker.setPrefSize(34, 3);
        marker.setMinSize(34, 3);
        marker.setMaxSize(34, 3);
        StackPane.setAlignment(marker, Pos.CENTER);
        scale.getStyleClass().add("cpu-thermometer-track");
        scale.setPrefSize(42, 124);
        scale.setMinSize(42, 124);
        scale.setMaxSize(42, 124);
        scale.getChildren().addAll(bands, marker);
        scale.heightProperty().addListener((obs, oldHeight, newHeight) -> updateMarker(false));

        VBox thermometerColumn = new VBox(3, heading, scaleTop, scale, scaleBottom);
        thermometerColumn.setAlignment(Pos.CENTER);
        thermometerColumn.getStyleClass().add("cpu-thermometer-column");

        valueLabel.getStyleClass().add("cpu-thermometer-value");
        Label description = new Label("TEMPERATURA\nEN TIEMPO REAL");
        description.getStyleClass().add("cpu-thermometer-description");
        description.setWrapText(true);
        rangeLabel.getStyleClass().add("cpu-thermometer-range");
        VBox details = new VBox(7, valueLabel, rangeLabel, description);
        details.setAlignment(Pos.CENTER_LEFT);
        details.setMinWidth(90);
        HBox.setHgrow(details, javafx.scene.layout.Priority.ALWAYS);

        getChildren().addAll(thermometerColumn, details);

        readingListener = (obs, oldValue, nextValue) -> render(nextValue);
        this.reading.addListener(readingListener);
        render(this.reading.getValue());
    }

    private static Region segment(String styleClass) {
        Region segment = new Region();
        segment.getStyleClass().add(styleClass);
        segment.setMinHeight(0);
        VBox.setVgrow(segment, javafx.scene.layout.Priority.ALWAYS);
        return segment;
    }

    private void render(MetricDisplay display) {
        currentReading = display;
        if (display == null || !display.isValid() || display.value().isEmpty()) {
            valueLabel.setText(display == null ? "N/D" : display.text());
            rangeLabel.setText("SIN LECTURA");
            marker.setVisible(false);
            if (markerAnimation != null) markerAnimation.stop();
            return;
        }

        double temperature = display.value().getAsDouble();
        valueLabel.setText(display.text());
        rangeLabel.setText(temperature >= 85 ? "TEMPERATURA ALTA"
                : temperature >= 70 ? "VIGILAR TEMPERATURA" : "LECTURA ACTUAL");
        marker.setVisible(true);
        updateMarker(true);
    }

    private void updateMarker(boolean animated) {
        if (currentReading == null || !currentReading.isValid()
                || currentReading.value().isEmpty() || scale.getHeight() <= 0) {
            return;
        }
        double temperature = currentReading.value().getAsDouble();
        double normalized = (temperature - MIN_TEMPERATURE) / (MAX_TEMPERATURE - MIN_TEMPERATURE);
        normalized = Math.max(0.0, Math.min(1.0, normalized));
        double usableHeight = Math.max(0, scale.getHeight() - marker.getHeight());
        double targetY = (0.5 - normalized) * usableHeight;

        if (markerAnimation != null) markerAnimation.stop();
        if (animated) {
            markerAnimation = new Timeline(new KeyFrame(Duration.millis(280),
                    new KeyValue(marker.translateYProperty(), targetY, Interpolator.EASE_BOTH)));
            markerAnimation.play();
        } else {
            marker.setTranslateY(targetY);
        }
    }

    @Override
    public void close() {
        reading.removeListener(readingListener);
        if (markerAnimation != null) markerAnimation.stop();
    }
}
