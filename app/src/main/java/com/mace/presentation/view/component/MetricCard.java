package com.mace.presentation.view.component;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;

/**
 * Tarjeta reutilizable para mostrar una metrica de hardware (ej: Temp. CPU, Watts GPU).
 * Uso:
 *   MetricCard card = new MetricCard("Temp. CPU", "°C");
 *   card.updateValue(67.4, 100.0); // valor actual, valor maximo esperado (para la barra)
 */
public class MetricCard extends VBox {

    private final Label titleLabel;
    private final Label valueLabel;
    private final ProgressBar progressBar;
    private final String unit;

    public MetricCard(String title, String unit) {
        this.unit = unit;
        getStyleClass().add("metric-card");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(6);

        titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-card-title");

        valueLabel = new Label("--" + unit);
        valueLabel.getStyleClass().add("metric-card-value");

        progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("metric-card-bar");
        progressBar.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(titleLabel, valueLabel, progressBar);
    }

    public void updateValue(double currentValue, double maxExpected) {
        valueLabel.setText(String.format("%.1f%s", currentValue, unit));
        double ratio = maxExpected <= 0 ? 0 : Math.min(1.0, currentValue / maxExpected);
        progressBar.setProgress(ratio);

        progressBar.getStyleClass().removeAll("metric-warning", "metric-critical");
        if (ratio >= 0.85) {
            progressBar.getStyleClass().add("metric-critical");
        } else if (ratio >= 0.7) {
            progressBar.getStyleClass().add("metric-warning");
        }
    }

    public void setUnavailable() {
        valueLabel.setText("N/D");
        progressBar.setProgress(0);
    }
}