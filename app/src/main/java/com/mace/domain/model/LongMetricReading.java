package com.mace.domain.model;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Lectura entera de una métrica (bytes, RPM, porcentaje entero de ventilador) con su estado.
 *
 * <p>Los {@code uint32_t}/{@code uint64_t} de C llegan ya convertidos a {@code long} sin signo
 * saturado. Si el estado no es {@link MetricState#VALID} el valor se normaliza a {@code 0}
 * <em>junto con</em> el estado: ese cero no es una lectura. Consumir con
 * {@link #asOptional()} o {@link #orElse(long)}.</p>
 *
 * @param value valor físico (&ge; 0) si {@code state == VALID}; {@code 0} en otro caso
 * @param state estado de la métrica en este sondeo
 */
public record LongMetricReading(long value, MetricState state) {

    public LongMetricReading {
        Objects.requireNonNull(state, "state no puede ser null");
        if (state.isValid()) {
            if (value < 0) {
                throw new IllegalArgumentException("Una lectura VALID no puede ser negativa: " + value);
            }
        } else {
            value = 0L;
        }
    }

    public static LongMetricReading valid(long value) {
        return new LongMetricReading(value, MetricState.VALID);
    }

    public static LongMetricReading unavailable(MetricState state) {
        if (Objects.requireNonNull(state, "state no puede ser null").isValid()) {
            throw new IllegalArgumentException("unavailable() requiere un estado distinto de VALID");
        }
        return new LongMetricReading(0L, state);
    }

    public boolean isValid() {
        return state.isValid();
    }

    public OptionalLong asOptional() {
        return isValid() ? OptionalLong.of(value) : OptionalLong.empty();
    }

    public long orElse(long fallback) {
        return isValid() ? value : fallback;
    }
}
