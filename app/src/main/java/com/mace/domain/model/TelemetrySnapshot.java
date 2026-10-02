package com.mace.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Fotografía inmutable del estado térmico y de consumo del hardware
 * en un instante determinado.
 *
 * @param cpuTemp temperatura de CPU en grados Celsius (ignorar si cpuSensorAvailable es false)
 * @param cpuWatts consumo de CPU en vatios (ignorar si cpuSensorAvailable es false)
 * @param cpuUsage uso de CPU de todo el sistema, en porcentaje [0..100] (siempre real)
 * @param cpuSensorAvailable indica si temperatura y consumo de CPU provienen de sensores reales
 * @param gpuTemp temperatura de GPU en grados Celsius (ignorar si gpuAvailable es false)
 * @param gpuWatts consumo de GPU en vatios (ignorar si gpuAvailable es false)
 * @param gpuFanRpm velocidad del ventilador de GPU en RPM (ignorar si gpuAvailable es false)
 * @param gpuAvailable indica si el sensor de GPU está disponible en este sondeo
 * @param timestamp instante exacto de la captura
 */
public record TelemetrySnapshot(
        float cpuTemp,
        float cpuWatts,
        float cpuUsage,
        boolean cpuSensorAvailable,
        float gpuTemp,
        float gpuWatts,
        int gpuFanRpm,
        boolean gpuAvailable,
        Instant timestamp
) {

    public TelemetrySnapshot {
        Objects.requireNonNull(timestamp, "timestamp no puede ser null");
    }

    /** Lectura con sensores de CPU disponibles (temperatura y consumo válidos). */
    public TelemetrySnapshot(float cpuTemp, float cpuWatts, float cpuUsage,
                             float gpuTemp, float gpuWatts, int gpuFanRpm,
                             boolean gpuAvailable, Instant timestamp) {
        this(cpuTemp, cpuWatts, cpuUsage, true, gpuTemp, gpuWatts, gpuFanRpm, gpuAvailable, timestamp);
    }
}
