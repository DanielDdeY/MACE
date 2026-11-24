package com.mace.presentation.view.component;

import com.mace.domain.model.AlertLevel;
import com.mace.presentation.viewmodel.GpuCardViewModel;
import com.mace.presentation.viewmodel.MetricDisplay;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;

/** Una tarjeta por LUID; no consulta hardware y no reemplaza la identidad del adaptador. */
public final class GpuCard extends VBox implements AutoCloseable {

    private final GpuCardViewModel model;
    private final Label name = new Label();
    private final Label provider = new Label();
    private final Label status = new Label();
    private final List<MetricCard> metrics;
    private AlertLevel thermalLevel = AlertLevel.NONE;
    private final ChangeListener<MetricDisplay> temperatureListener = (obs, previous, next) -> refreshStatus();
    private final ChangeListener<Boolean> inactiveListener = (obs, previous, next) -> refreshStatus();

    public GpuCard(GpuCardViewModel model) {
        this.model = Objects.requireNonNull(model, "model no puede ser null");
        getStyleClass().add("gpu-card");
        setSpacing(10);
        setPadding(new Insets(16));
        setMaxWidth(Double.MAX_VALUE);

        name.getStyleClass().add("gpu-name");
        name.textProperty().bind(model.nameProperty());
        provider.getStyleClass().add("device-provider");
        provider.textProperty().bind(Bindings.concat(model.vendorProperty(), " · ", model.providerProperty()));
        status.getStyleClass().add("gpu-status");
        HBox header = new HBox(12, name, status);
        getChildren().addAll(header, provider);

        metrics = List.of(
                new MetricCard("Temperatura", model.temperatureProperty()),
                new MetricCard("Uso", model.usageProperty()),
                new MetricCard("Ventilador", model.fanRpmProperty()),
                new MetricCard("Velocidad del ventilador", model.fanPercentProperty()),
                new MetricCard("VRAM usada", model.vramUsedProperty()),
                new MetricCard("VRAM total", model.vramTotalProperty()));
        FlowPane grid = new FlowPane(10, 10);
        grid.setPrefWrapLength(760);
        grid.getChildren().addAll(metrics);
        getChildren().add(grid);
        model.temperatureProperty().addListener(temperatureListener);
        model.inactiveProperty().addListener(inactiveListener);
        setThermalStatus(AlertLevel.NONE);
    }

    public GpuCardViewModel model() { return model; }

    public void setThermalStatus(AlertLevel level) {
        thermalLevel = Objects.requireNonNull(level, "level no puede ser null");
        refreshStatus();
    }

    private void refreshStatus() {
        status.getStyleClass().removeAll("status-ok", "status-warning", "status-critical", "status-muted");
        if (model.inactiveProperty().get()) {
            status.setText("Inactiva");
            status.getStyleClass().add("status-muted");
        } else if (!model.temperatureProperty().get().isValid()) {
            status.setText(model.temperatureProperty().get().text());
            status.getStyleClass().add("status-muted");
        } else if (thermalLevel == AlertLevel.CRITICAL) {
            status.setText("Crítico");
            status.getStyleClass().add("status-critical");
        } else if (thermalLevel == AlertLevel.WARNING) {
            status.setText("Advertencia");
            status.getStyleClass().add("status-warning");
        } else {
            status.setText("Normal");
            status.getStyleClass().add("status-ok");
        }
    }

    @Override
    public void close() {
        model.temperatureProperty().removeListener(temperatureListener);
        model.inactiveProperty().removeListener(inactiveListener);
        name.textProperty().unbind();
        provider.textProperty().unbind();
        metrics.forEach(MetricCard::close);
    }
}
