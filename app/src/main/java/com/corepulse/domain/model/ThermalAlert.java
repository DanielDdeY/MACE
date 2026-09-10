package com.corepulse.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Alerta emitida por {@link com.corepulse.domain.service.ThermalAlertEvaluator}
 * cuando una fuente de calor supera un umbral configurado.
 *
 * @param level severidad de la alerta
 * @param source origen de la lectura (p. ej. "CPU" o "GPU")
 * @param temperatureCelsius temperatura que originó la alerta
 * @param timestamp instante de la lectura evaluada
 */
public record ThermalAlert(AlertLevel level, String source, float temperatureCelsius, Instant timestamp) {

    public ThermalAlert {
        Objects.requireNonNull(level, "level no puede ser null");
        Objects.requireNonNull(timestamp, "timestamp no puede ser null");
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source no puede estar vacío");
        }
    }

    public boolean isActionable() {
        return level != AlertLevel.NONE;
    }
}
