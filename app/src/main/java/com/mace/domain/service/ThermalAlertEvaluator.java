package com.mace.domain.service;

import com.mace.domain.model.AlertLevel;
import com.mace.domain.model.GpuAdapterId;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.MetricReading;
import com.mace.domain.model.TelemetrySnapshot;
import com.mace.domain.model.ThermalAlert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Servicio de dominio puro (sin dependencias de puertos) que evalúa temperaturas contra los
 * umbrales térmicos de CPU y de <strong>cada</strong> GPU identificada.
 *
 * Umbrales:
 * <ul>
 *   <li>&gt; 75.0°C → {@link AlertLevel#WARNING}</li>
 *   <li>&gt; 85.0°C → {@link AlertLevel#CRITICAL}</li>
 * </ul>
 *
 * <p>Solo se evalúan temperaturas {@code VALID}: {@code NO_PROVIDER}, {@code WARMING_UP} o una GPU
 * detectada sin sensor nunca generan alertas. Esta clase NO decide ni ejecuta ninguna acción
 * correctiva: solo informa.</p>
 */
public final class ThermalAlertEvaluator {

    public static final float WARNING_THRESHOLD_CELSIUS = 75.0f;
    public static final float CRITICAL_THRESHOLD_CELSIUS = 85.0f;

    private static final String SOURCE_CPU = "CPU";
    private static final String SOURCE_GPU = "GPU";

    /**
     * Evalúa el snapshot V2: CPU primero y luego cada GPU en orden DXGI. Cada alerta de GPU lleva
     * el LUID del adaptador que la originó.
     *
     * @return alertas accionables (WARNING o CRITICAL); vacía si ninguna fuente supera el umbral
     */
    public List<ThermalAlert> evaluate(HardwareSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot no puede ser null");

        List<ThermalAlert> alerts = new ArrayList<>(1 + snapshot.gpus().size());
        evaluateReading(SOURCE_CPU, null, snapshot.cpu().temperatureCelsius(), snapshot.timestamp(), alerts);
        for (GpuReading gpu : snapshot.gpus()) {
            evaluateReading(gpu.adapter().displayName(), gpu.adapterId(), gpu.temperatureCelsius(),
                    snapshot.timestamp(), alerts);
        }
        return List.copyOf(alerts);
    }

    /**
     * Evalúa un snapshot heredado (una sola GPU, booleanos generales). Se mantiene durante la
     * migración de la presentación.
     *
     * @return alertas activas, en el mismo orden CPU → GPU
     */
    public List<ThermalAlert> evaluate(TelemetrySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot no puede ser null");

        List<ThermalAlert> alerts = new ArrayList<>(2);

        if (snapshot.cpuSensorAvailable()) {
            ThermalAlert cpuAlert = evaluateSource(SOURCE_CPU, null, snapshot.cpuTemp(), snapshot.timestamp());
            if (cpuAlert.isActionable()) {
                alerts.add(cpuAlert);
            }
        }

        if (snapshot.gpuAvailable()) {
            ThermalAlert gpuAlert = evaluateSource(SOURCE_GPU, null, snapshot.gpuTemp(), snapshot.timestamp());
            if (gpuAlert.isActionable()) {
                alerts.add(gpuAlert);
            }
        }

        return List.copyOf(alerts);
    }

    /** Nivel que corresponde a una temperatura válida. */
    public AlertLevel levelFor(double temperature) {
        if (temperature > CRITICAL_THRESHOLD_CELSIUS) {
            return AlertLevel.CRITICAL;
        }
        if (temperature > WARNING_THRESHOLD_CELSIUS) {
            return AlertLevel.WARNING;
        }
        return AlertLevel.NONE;
    }

    private void evaluateReading(String source, GpuAdapterId gpu, MetricReading temperature,
                                 Instant timestamp, List<ThermalAlert> alerts) {
        if (!temperature.isValid()) {
            return;
        }
        ThermalAlert alert = evaluateSource(source, gpu, (float) temperature.value(), timestamp);
        if (alert.isActionable()) {
            alerts.add(alert);
        }
    }

    private ThermalAlert evaluateSource(String source, GpuAdapterId gpu, float temperature, Instant timestamp) {
        return new ThermalAlert(levelFor(temperature), source, gpu, temperature, timestamp);
    }
}
