package com.mace.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Fotografía inmutable del estado térmico y de consumo del hardware
 * en un instante determinado.
 *
 * @param cpuTemp temperatura de CPU en grados Celsius
 * @param cpuWatts consumo de CPU en vatios
 * @param cpuUsage uso de CPU de todo el sistema, en porcentaje [0..100]
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
        float gpuTemp,
        float gpuWatts,
        int gpuFanRpm,
        boolean gpuAvailable,
        Instant timestamp
) {

    public TelemetrySnapshot {
        Objects.requireNonNull(timestamp, "timestamp no puede ser null");
    }
}
