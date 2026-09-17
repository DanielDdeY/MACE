package com.mace.presentation.view.component;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

/**
 * Gauge circular (velocimetro) animado, dibujado a mano con Canvas para no
 * depender de ninguna libreria externa de graficos.
 *
 * Uso:
 *   GaugeDial gauge = new GaugeDial("CPU", "°C", 100);
 *   gauge.setValue(67.4); // anima suavemente hacia el nuevo valor
 */
public class GaugeDial extends VBox {

    private static final double START_ANGLE = 210;   // grados, arranca abajo-izquierda
    private static final double SWEEP_ANGLE = 240;    // recorre 240° hasta abajo-derecha

    private final Canvas canvas = new Canvas(150, 150);
    private final Label valueLabel = new Label("--");
    private final Label titleLabel;
    private final String unit;
    private final double maxValue;

    private final DoubleProperty animatedValue = new SimpleDoubleProperty(0);
    private Timeline animation;

    private Color trackColor = Color.web("#30363d");
    private Color fillColor = Color.web("#3fb950");
    private boolean available = true;

    public GaugeDial(String title, String unit, double maxValue) {
        this.unit = unit;
        this.maxValue = maxValue;

        getStyleClass().add("gauge-dial");
        setAlignment(Pos.CENTER);
        setSpacing(4);

        titleLabel = new Label(title);
        titleLabel.getStyleClass().add("gauge-title");

        valueLabel.getStyleClass().add("gauge-value");

        StackPane canvasStack = new StackPane(canvas, valueLabel);
        canvasStack.setAlignment(Pos.CENTER);

        getChildren().addAll(titleLabel, canvasStack);

        animatedValue.addListener((obs, oldV, newV) -> draw(newV.doubleValue()));
        draw(0);
    }

    /** Actualiza el valor con una animacion suave (evita saltos bruscos visualmente). */
    public void setValue(double newValue) {
        available = true;
        double clamped = Math.max(0, Math.min(maxValue, newValue));

        updateFillColorForRatio(clamped / maxValue);

        if (animation != null) {
            animation.stop();
        }
        animation = new Timeline(new KeyFrame(Duration.millis(400),
                new KeyValue(animatedValue, clamped, javafx.animation.Interpolator.EASE_BOTH)));
        animation.play();
    }

    public void setUnavailable() {
        available = false;
        if (animation != null) animation.stop();
        animatedValue.set(0);
        draw(0);
    }

    private void updateFillColorForRatio(double ratio) {
        if (ratio >= 0.85) {
            fillColor = Color.web("#f85149"); // critico
        } else if (ratio >= 0.7) {
            fillColor = Color.web("#d29922"); // warning
        } else {
            fillColor = Color.web("#3fb950"); // ok
        }
    }

    private void draw(double value) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        double pad = 14;

        gc.clearRect(0, 0, w, h);

        // Pista de fondo (arco completo)
        gc.setStroke(trackColor);
        gc.setLineWidth(10);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.strokeArc(pad, pad, w - pad * 2, h - pad * 2,
                -START_ANGLE + 180, -SWEEP_ANGLE, javafx.scene.shape.ArcType.OPEN);

        // Arco de progreso
        double ratio = maxValue <= 0 ? 0 : value / maxValue;
        double sweep = -SWEEP_ANGLE * ratio;
        gc.setStroke(fillColor);
        gc.setLineWidth(10);
        gc.strokeArc(pad, pad, w - pad * 2, h - pad * 2,
                -START_ANGLE + 180, sweep, javafx.scene.shape.ArcType.OPEN);

        valueLabel.setText(available ? String.format("%.0f%s", value, unit) : "N/D");
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
    }
}