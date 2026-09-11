package com.corepulse.domain.service;

import com.corepulse.domain.model.AlertLevel;
import com.corepulse.domain.model.TelemetrySnapshot;
import com.corepulse.domain.model.ThermalAlert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Servicio de dominio puro (sin dependencias de puertos) que evalúa
 * un {@link TelemetrySnapshot} contra los umbrales térmicos definidos
 * para CPU y GPU.
 *
 * Umbrales:
 * <ul>
 *   <li>&gt; 75.0°C → {@link AlertLevel#WARNING}</li>
 *   <li>&gt; 85.0°C → {@link AlertLevel#CRITICAL}</li>
 * </ul>
 *
 * Esta clase NO decide ni ejecuta ninguna acción correctiva: solo informa.
 */
public final class ThermalAlertEvaluator {

    public static final float WARNING_THRESHOLD_CELSIUS = 75.0f;
    public static final float CRITICAL_THRESHOLD_CELSIUS = 85.0f;

    private static final String SOURCE_CPU = "CPU";
    private static final String SOURCE_GPU = "GPU";

    /**
     * Evalúa un snapshot y devuelve únicamente las alertas accionables
     * (nivel WARNING o CRITICAL). Si ninguna fuente supera el umbral,
     * devuelve una lista vacía.
     *
     * @param snapshot lectura a evaluar
     * @return alertas activas, en el mismo orden CPU → GPU
     */
    public List<ThermalAlert> evaluate(TelemetrySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot no puede ser null");

        List<ThermalAlert> alerts = new ArrayList<>(2);

        ThermalAlert cpuAlert = evaluateSource(SOURCE_CPU, snapshot.cpuTemp(), snapshot.timestamp());
        if (cpuAlert.isActionable()) {
            alerts.add(cpuAlert);
        }

        if (snapshot.gpuAvailable()) {
            ThermalAlert gpuAlert = evaluateSource(SOURCE_GPU, snapshot.gpuTemp(), snapshot.timestamp());
            if (gpuAlert.isActionable()) {
                alerts.add(gpuAlert);
            }
        }

        return List.copyOf(alerts);
    }

    private ThermalAlert evaluateSource(String source, float temperature, Instant timestamp) {
        AlertLevel level = levelFor(temperature);
        return new ThermalAlert(level, source, temperature, timestamp);
    }

    private AlertLevel levelFor(float temperature) {
        if (temperature > CRITICAL_THRESHOLD_CELSIUS) {
            return AlertLevel.CRITICAL;
        }
        if (temperature > WARNING_THRESHOLD_CELSIUS) {
            return AlertLevel.WARNING;
        }
        return AlertLevel.NONE;
    }
}
