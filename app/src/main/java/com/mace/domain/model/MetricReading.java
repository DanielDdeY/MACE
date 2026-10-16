package com.mace.domain.model;

import java.util.Objects;

/**
 * Lectura de una única métrica con su estado de fiabilidad y unidad de medida.
 *
 * @param <T> tipo del valor medido (p. ej. {@link Float}, {@link Integer}, {@link Long})
 * @param value valor de la métrica; puede ser {@code null} cuando el estado no es
 *              {@link MetricState#VALID} (no se debe interpretar un sustituto como medida)
 * @param state estado de fiabilidad de la lectura
 * @param unit unidad de medida (p. ej. "°C", "W", "%", "RPM", "bytes"); puede ser {@code null}
 */
public record MetricReading<T>(T value, MetricState state, String unit) {

    public MetricReading {
        Objects.requireNonNull(state, "state no puede ser null");
    }

    /**
     * Indica si esta lectura representa una medición válida y usable.
     *
     * @return {@code true} si el estado es {@link MetricState#VALID}
     */
    public boolean isValid() {
        return state == MetricState.VALID;
    }
}
