package com.mace.domain.model;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Lectura decimal de una métrica (temperatura, potencia, uso) con su estado individual.
 *
 * <p>Cuando el estado no es {@link MetricState#VALID} el valor se normaliza a
 * {@link Double#NaN}: así nunca se confunde con una lectura de cero y cualquier comparación
 * contra un umbral ({@code NaN > 75}) resulta falsa. Para consumir el valor usar
 * {@link #asOptional()} o {@link #orElse(double)}.</p>
 *
 * @param value valor físico si {@code state == VALID}; {@code NaN} en otro caso
 * @param state estado de la métrica en este sondeo
 */
public record MetricReading(double value, MetricState state) {

    public MetricReading {
        Objects.requireNonNull(state, "state no puede ser null");
        if (state.isValid()) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Una lectura VALID debe ser finita: " + value);
            }
        } else {
            value = Double.NaN;
        }
    }

    public static MetricReading valid(double value) {
        return new MetricReading(value, MetricState.VALID);
    }

    public static MetricReading unavailable(MetricState state) {
        if (Objects.requireNonNull(state, "state no puede ser null").isValid()) {
            throw new IllegalArgumentException("unavailable() requiere un estado distinto de VALID");
        }
        return new MetricReading(Double.NaN, state);
    }

    public boolean isValid() {
        return state.isValid();
    }

    /** @return el valor solo si es válido */
    public OptionalDouble asOptional() {
        return isValid() ? OptionalDouble.of(value) : OptionalDouble.empty();
    }

    public double orElse(double fallback) {
        return isValid() ? value : fallback;
    }
}
