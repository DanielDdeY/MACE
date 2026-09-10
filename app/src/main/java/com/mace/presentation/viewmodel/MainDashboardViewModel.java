package com.mace.presentation.viewmodel;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ViewModel del dashboard principal (MVVM).
 *
 * Expone Properties de JavaFX para poder bindear directamente en el FXML/vista.
 * Por ahora, los valores se generan con un Timeline local (mock) para poder
 * probar la interfaz sin depender de la capa nativa (Dev 1) ni del puente FFM
 * (Dev 3). Cuando el equipo integre HardwareSensorsPort real (a traves de
 * TelemetryPublisherPort, probablemente vía Flow API), este Timeline se
 * reemplaza por la suscripcion real; el resto de la clase no deberia cambiar.
 *
 * Regla de la arquitectura: cualquier actualizacion visual que en el futuro
 * venga de un hilo de fondo (polling real) DEBE despacharse con
 * Platform.runLater(). El Timeline de JavaFX ya corre en el hilo de UI, asi
 * que hoy no es necesario, pero se deja la nota para quien conecte el sondeo real.
 */
public class MainDashboardViewModel {

    private final DoubleProperty cpuTemp = new SimpleDoubleProperty(0);
    private final DoubleProperty cpuWatts = new SimpleDoubleProperty(0);
    private final DoubleProperty gpuTemp = new SimpleDoubleProperty(0);
    private final DoubleProperty gpuWatts = new SimpleDoubleProperty(0);
    private final IntegerProperty gpuFanRpm = new SimpleIntegerProperty(0);
    private final BooleanProperty gpuAvailable = new SimpleBooleanProperty(true);

    private final ObservableList<ProcessRow> processes = FXCollections.observableArrayList();

    private Timeline mockTimeline;

    public MainDashboardViewModel() {
        loadMockProcesses();
    }

    /** Inicia la simulacion periodica de telemetria (llamar una vez al arrancar la vista). */
    public void startMockPolling() {
        if (mockTimeline != null) {
            return;
        }
        mockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> tickMockTelemetry()));
        mockTimeline.setCycleCount(Timeline.INDEFINITE);
        mockTimeline.play();
    }

    public void stopMockPolling() {
        if (mockTimeline != null) {
            mockTimeline.stop();
        }
    }

    private void tickMockTelemetry() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        cpuTemp.set(45 + r.nextDouble(0, 35));      // 45-80 C
        cpuWatts.set(15 + r.nextDouble(0, 45));      // 15-60 W
        gpuTemp.set(40 + r.nextDouble(0, 45));       // 40-85 C
        gpuWatts.set(20 + r.nextDouble(0, 150));     // 20-170 W
        gpuFanRpm.set(800 + r.nextInt(0, 2200));
    }

    private void loadMockProcesses() {
        processes.setAll(List.of(
                new ProcessRow(4120, "chrome.exe", "C:\\Program Files\\Google\\Chrome\\chrome.exe",
                        512L * 1024 * 1024, 380L * 1024 * 1024),
                new ProcessRow(8891, "javafx-app.exe", "C:\\Users\\USER\\MACE\\app\\target\\javafx-app.exe",
                        180L * 1024 * 1024, 120L * 1024 * 1024),
                new ProcessRow(1023, "discord.exe", "C:\\Users\\USER\\AppData\\Local\\Discord\\discord.exe",
                        260L * 1024 * 1024, 190L * 1024 * 1024),
                new ProcessRow(5544, "steam.exe", "C:\\Program Files (x86)\\Steam\\steam.exe",
                        340L * 1024 * 1024, 210L * 1024 * 1024),
                new ProcessRow(9021, "code.exe", "C:\\Users\\USER\\AppData\\Local\\Programs\\Microsoft VS Code\\Code.exe",
                        420L * 1024 * 1024, 300L * 1024 * 1024)
        ));
    }

    /** Simula el cierre manual de un proceso (reemplazar por TerminateProcessService real luego). */
    public void terminateProcessMock(ProcessRow row) {
        processes.remove(row);
    }

    // ---- Getters de Properties para bindear en la vista ----

    public DoubleProperty cpuTempProperty() { return cpuTemp; }
    public DoubleProperty cpuWattsProperty() { return cpuWatts; }
    public DoubleProperty gpuTempProperty() { return gpuTemp; }
    public DoubleProperty gpuWattsProperty() { return gpuWatts; }
    public IntegerProperty gpuFanRpmProperty() { return gpuFanRpm; }
    public BooleanProperty gpuAvailableProperty() { return gpuAvailable; }

    public ObservableList<ProcessRow> getProcesses() { return processes; }
}