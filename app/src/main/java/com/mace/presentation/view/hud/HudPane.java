package com.mace.presentation.view.hud;

import com.mace.domain.model.GpuReading;
import com.mace.domain.model.GpuAdapterId;
import com.mace.domain.model.MetricReading;
import com.mace.domain.model.MonitoringSnapshot;
import com.mace.presentation.viewmodel.MainDashboardViewModel;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;

/** Configuración y overlay de escritorio; reutiliza el snapshot ya recibido por el dashboard. */
public final class HudPane extends VBox implements AutoCloseable {
    private enum Field { CPU_USAGE, CPU_TEMP, CPU_POWER, GPU_USAGE, GPU_TEMP, GPU_POWER,
        GPU_MEMORY, RAM_USED, RAM_PERCENT }

    private final Preferences preferences;
    private final MainDashboardViewModel dashboard;
    private final Stage overlay = new Stage(StageStyle.TRANSPARENT);
    private final VBox overlayBody = new VBox(4);
    private final VBox previewBody = new VBox(4);
    private final Map<Field, CheckBox> choices = new EnumMap<>(Field.class);
    private final ComboBox<String> gpuSelector = new ComboBox<>();
    private final CheckBox enabled = new CheckBox("Mostrar Micro-HUD sobre el escritorio");
    private final Slider opacity = new Slider(0.2, 1, 0.85);
    private final ChangeListener<MonitoringSnapshot> snapshotListener = (obs, old, current) -> render();
    private MonitoringSnapshot snapshot;
    private double dragX;
    private double dragY;
    private boolean updatingGpuSelector;
    private boolean closing;
    private GpuAdapterId selectedGpuId;

    public HudPane(MainDashboardViewModel dashboard) {
        this(dashboard, Preferences.userNodeForPackage(HudPane.class));
    }

    HudPane(MainDashboardViewModel dashboard, Preferences preferences) {
        this.dashboard = dashboard;
        this.preferences = preferences;
        getStyleClass().add("hud-settings");
        getStylesheets().add(getClass().getResource("/styles/hud.css").toExternalForm());
        setSpacing(14);
        setPadding(new Insets(18));
        setFillWidth(true);

        Label title = new Label("Configuración de Micro-HUD");
        title.getStyleClass().add("section-title");
        Label note = new Label("Selecciona las métricas que aparecerán sobre tus ventanas. Arrastra el HUD para moverlo.");
        note.getStyleClass().add("section-subtitle");
        enabled.setSelected(preferences.getBoolean("enabled", true));
        enabled.setOnAction(e -> {
            preferences.putBoolean("enabled", enabled.isSelected());
            if (enabled.isSelected()) overlay.show(); else overlay.hide();
        });

        VBox options = new VBox(8);
        options.getStyleClass().add("hud-options");
        options.getChildren().addAll(group("CPU", new Field[][] {
            {Field.CPU_USAGE, Field.CPU_TEMP, Field.CPU_POWER}
        }, new String[] {"Uso (%)", "Temperatura (°C)", "Potencia (W)"}),
            group("GPU", new Field[][] {{Field.GPU_USAGE, Field.GPU_TEMP, Field.GPU_POWER, Field.GPU_MEMORY}},
                new String[] {"Uso (%)", "Temperatura (°C)", "Potencia (W)", "VRAM usada"}),
            group("Memoria RAM", new Field[][] {{Field.RAM_USED, Field.RAM_PERCENT}},
                new String[] {"Memoria usada", "Uso (%)"}));
        gpuSelector.setPromptText("GPU");
        gpuSelector.setMaxWidth(Double.MAX_VALUE);
        gpuSelector.setOnAction(e -> {
            if (!updatingGpuSelector) {
                int index = gpuSelector.getSelectionModel().getSelectedIndex();
                preferences.putInt("gpuIndex", index);
                if (snapshot != null && index >= 0 && index < snapshot.hardware().gpus().size())
                    selectedGpuId = snapshot.hardware().gpus().get(index).adapterId();
                render();
            }
        });
        options.getChildren().add(gpuSelector);

        opacity.setValue(preferences.getDouble("opacity", 0.85));
        opacity.valueProperty().addListener((obs, old, value) -> {
            preferences.putDouble("opacity", value.doubleValue());
            styleBodies();
        });
        Label opacityLabel = new Label("Opacidad del fondo");
        opacityLabel.getStyleClass().add("section-subtitle");
        VBox settings = new VBox(10, enabled, options, opacityLabel, opacity);
        settings.getStyleClass().add("hud-settings-card");
        settings.setPadding(new Insets(16));

        Label previewTitle = new Label("Previsualización en vivo");
        previewTitle.getStyleClass().add("hud-heading");
        Label previewNote = new Label("La ventana real se puede arrastrar por su borde superior.");
        previewNote.getStyleClass().add("section-subtitle");
        previewBody.setPadding(new Insets(12));
        previewBody.setMaxWidth(380);
        VBox preview = new VBox(14, previewTitle, previewNote, previewBody);
        preview.getStyleClass().add("hud-settings-card");
        preview.setPadding(new Insets(16));
        VBox.setVgrow(preview, Priority.ALWAYS);
        HBox columns = new HBox(16, settings, preview);
        HBox.setHgrow(settings, Priority.ALWAYS);
        HBox.setHgrow(preview, Priority.ALWAYS);
        settings.setMaxWidth(Double.MAX_VALUE);
        preview.setMaxWidth(Double.MAX_VALUE);
        getChildren().addAll(title, note, columns);

        setupOverlay();
        dashboard.latestSnapshotProperty().addListener(snapshotListener);
        snapshot = dashboard.latestSnapshotProperty().get();
        styleBodies();
        render();
        if (enabled.isSelected()) overlay.show();
    }

    private VBox group(String heading, Field[][] fields, String[] captions) {
        VBox box = new VBox(6);
        Label label = new Label(heading);
        label.getStyleClass().add("hud-heading");
        box.getChildren().add(label);
        int i = 0;
        for (Field[] row : fields) for (Field field : row) {
            CheckBox check = new CheckBox(captions[i++]);
            check.setSelected(preferences.getBoolean(field.name(), defaultEnabled(field)));
            check.setOnAction(e -> { preferences.putBoolean(field.name(), check.isSelected()); render(); });
            choices.put(field, check);
            box.getChildren().add(check);
        }
        return box;
    }

    private static boolean defaultEnabled(Field field) {
        return switch (field) {
            case CPU_USAGE, CPU_TEMP, GPU_USAGE, GPU_TEMP, RAM_USED -> true;
            default -> false;
        };
    }

    private void setupOverlay() {
        overlayBody.setPadding(new Insets(10, 14, 12, 14));
        overlayBody.setMinWidth(220);
        Label handle = new Label("MACE  •  MICRO-HUD");
        handle.getStyleClass().add("hud-handle");
        Button close = new Button("×");
        close.getStyleClass().add("hud-close");
        close.setOnAction(e -> { enabled.setSelected(false); preferences.putBoolean("enabled", false); overlay.hide(); });
        HBox header = new HBox(handle, close);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(handle, Priority.ALWAYS);
        overlayBody.getChildren().add(header);
        overlayBody.setOnMousePressed(e -> { dragX = e.getScreenX() - overlay.getX(); dragY = e.getScreenY() - overlay.getY(); });
        overlayBody.setOnMouseDragged(e -> {
            overlay.setX(e.getScreenX() - dragX);
            overlay.setY(e.getScreenY() - dragY);
        });
        overlay.setOnHidden(e -> {
            preferences.putDouble("x", overlay.getX());
            preferences.putDouble("y", overlay.getY());
            if (!closing) {
                enabled.setSelected(false);
                preferences.putBoolean("enabled", false);
            }
        });
        Scene scene = new Scene(overlayBody);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/styles/hud.css").toExternalForm());
        overlay.setScene(scene);
        overlay.setAlwaysOnTop(true);
        overlay.setTitle("MACE Micro-HUD");
        var bounds = Screen.getPrimary().getVisualBounds();
        overlay.setX(preferences.getDouble("x", bounds.getMaxX() - 290));
        overlay.setY(preferences.getDouble("y", bounds.getMinY() + 40));
    }

    private void styleBodies() {
        double alpha = opacity.getValue();
        String background = String.format(Locale.ROOT,
            "-fx-background-color: rgba(8, 13, 20, %.2f); -fx-border-color: #35dce9; -fx-border-width: 1;", alpha);
        overlayBody.setStyle(background);
        previewBody.setStyle(background);
    }

    private void render() {
        snapshot = dashboard.latestSnapshotProperty().get();
        int selected = gpuSelector.getSelectionModel().getSelectedIndex();
        int count = snapshot == null ? 0 : snapshot.hardware().gpus().size();
        var names = snapshot == null ? java.util.List.<String>of() :
            snapshot.hardware().gpus().stream().map(gpu -> gpu.adapter().displayName()).toList();
        if (!gpuSelector.getItems().equals(names)) {
            updatingGpuSelector = true;
            try {
                gpuSelector.getItems().setAll(names);
                if (count > 0) {
                    int matching = -1;
                    for (int i = 0; i < count; i++)
                        if (hardwareGpuId(i).equals(selectedGpuId)) { matching = i; break; }
                    if (selectedGpuId == null) matching = Math.max(0, Math.min(count - 1,
                        selected < 0 ? preferences.getInt("gpuIndex", 0) : selected));
                    if (matching >= 0) gpuSelector.getSelectionModel().select(matching);
                    else gpuSelector.setPromptText("GPU seleccionada desconectada");
                }
            } finally {
                updatingGpuSelector = false;
            }
        }
        if (selectedGpuId == null && gpuSelector.getSelectionModel().getSelectedIndex() >= 0)
            selectedGpuId = hardwareGpuId(gpuSelector.getSelectionModel().getSelectedIndex());
        if (selectedGpuId != null) {
            int match = -1;
            for (int i = 0; i < count; i++) if (hardwareGpuId(i).equals(selectedGpuId)) { match = i; break; }
            if (match != gpuSelector.getSelectionModel().getSelectedIndex()) {
                updatingGpuSelector = true;
                try { gpuSelector.getSelectionModel().select(match); }
                finally { updatingGpuSelector = false; }
            }
        }
        overlayBody.getChildren().removeIf(node -> node instanceof Label);
        previewBody.getChildren().clear();
        if (snapshot == null) { addLine("Esperando datos…"); return; }
        var hardware = snapshot.hardware();
        var cpu = hardware.cpu();
        add(Field.CPU_USAGE, "CPU uso", cpu.usagePercent(), "%");
        add(Field.CPU_TEMP, "CPU temp", cpu.temperatureCelsius(), "°C");
        add(Field.CPU_POWER, "CPU potencia", cpu.powerWatts(), "W");
        int index = gpuSelector.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < count) {
            var gpu = hardware.gpus().get(index);
            add(Field.GPU_USAGE, "GPU uso", gpu.usagePercent(), "%");
            add(Field.GPU_TEMP, "GPU temp", gpu.temperatureCelsius(), "°C");
            add(Field.GPU_POWER, "GPU potencia", gpu.powerWatts(), "W");
            if (choices.get(Field.GPU_MEMORY).isSelected())
                addLine("GPU VRAM  " + (gpu.videoMemoryUsedBytes().isValid()
                    ? String.format(Locale.ROOT, "%.0f MiB", gpu.videoMemoryUsedBytes().value() / 1048576.0)
                    : "N/D"));
        } else if (choices.get(Field.GPU_USAGE).isSelected())
            addLine(selectedGpuId == null ? "GPU  No detectada" : "GPU  Desconectada");
        var ram = hardware.memory();
        if (choices.get(Field.RAM_USED).isSelected())
            addLine("RAM usada  " + (ram.usedBytes().isValid()
                ? String.format(Locale.ROOT, "%.1f GiB", ram.usedBytes().value() / 1073741824.0)
                : "N/D"));
        add(Field.RAM_PERCENT, "RAM uso", ram.usedPercent(), "%");
        if (previewBody.getChildren().isEmpty()) addLine("Selecciona una métrica");
        if (overlay.isShowing()) overlay.sizeToScene();
    }

    private GpuAdapterId hardwareGpuId(int index) {
        return snapshot.hardware().gpus().get(index).adapterId();
    }

    private void add(Field field, String name, MetricReading reading, String unit) {
        if (choices.get(field).isSelected()) addLine(name + "  " +
            (reading.isValid() ? String.format(Locale.ROOT, "%.1f %s", reading.value(), unit) : "N/D"));
    }

    private void addLine(String text) {
        Label real = new Label(text);
        real.getStyleClass().add("hud-line");
        Label preview = new Label(text);
        preview.getStyleClass().add("hud-line");
        overlayBody.getChildren().add(real);
        previewBody.getChildren().add(preview);
    }

    @Override public void close() {
        dashboard.latestSnapshotProperty().removeListener(snapshotListener);
        closing = true;
        overlay.hide();
    }
}
