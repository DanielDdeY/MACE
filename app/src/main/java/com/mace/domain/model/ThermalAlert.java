package com.mace.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Alerta emitida por {@link com.mace.domain.service.ThermalAlertEvaluator}
 * cuando una fuente de calor supera un umbral configurado.
 *
 * @param level severidad de la alerta
 * @param source origen legible (p. ej. "CPU" o "GPU 1 · NVIDIA GeForce RTX 3060")
 * @param gpuAdapterId LUID de la GPU que originó la alerta; {@code null} para la CPU
 * @param temperatureCelsius temperatura que originó la alerta
 * @param timestamp instante de la lectura evaluada
 */
public record ThermalAlert(AlertLevel level, String source, GpuAdapterId gpuAdapterId,
                           float temperatureCelsius, Instant timestamp) {

    public ThermalAlert {
        Objects.requireNonNull(level, "level no puede ser null");
        Objects.requireNonNull(timestamp, "timestamp no puede ser null");
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source no puede estar vacío");
        }
    }

    /** Alerta sin GPU asociada (CPU o snapshot heredado). */
    public ThermalAlert(AlertLevel level, String source, float temperatureCelsius, Instant timestamp) {
        this(level, source, null, temperatureCelsius, timestamp);
    }

    /** GPU que originó la alerta, si la fuente es una GPU identificada. */
    public Optional<GpuAdapterId> gpu() {
        return Optional.ofNullable(gpuAdapterId);
    }

    public boolean isActionable() {
        return level != AlertLevel.NONE;
    }
}
