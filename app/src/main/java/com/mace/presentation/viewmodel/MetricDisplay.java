package com.mace.presentation.viewmodel;

import com.mace.domain.model.LongMetricReading;
import com.mace.domain.model.MetricReading;
import com.mace.domain.model.MetricState;

import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;

/** Valor listo para la vista; una lectura ausente nunca se convierte en cero. */
public record MetricDisplay(MetricState state, String text, OptionalDouble value) {

    public MetricDisplay {
        Objects.requireNonNull(state, "state no puede ser null");
        Objects.requireNonNull(text, "text no puede ser null");
        Objects.requireNonNull(value, "value no puede ser null");
        if (state.isValid() != value.isPresent()) {
            throw new IllegalArgumentException("El valor opcional debe coincidir con el estado");
        }
    }

    public static MetricDisplay decimal(MetricReading reading, String unit, int decimals) {
        Objects.requireNonNull(reading, "reading no puede ser null");
        checkFormat(unit, decimals);
        if (!reading.isValid()) {
            return unavailable(reading.state());
        }
        return valid(reading.value(), unit, decimals);
    }

    public static MetricDisplay whole(LongMetricReading reading, String unit) {
        Objects.requireNonNull(reading, "reading no puede ser null");
        checkFormat(unit, 0);
        if (!reading.isValid()) {
            return unavailable(reading.state());
        }
        return valid(reading.value(), unit, 0);
    }

    /** Escala bytes a la unidad elegida (por ejemplo, GiB = 1024³). */
    public static MetricDisplay scaled(LongMetricReading reading, double divisor, String unit, int decimals) {
        Objects.requireNonNull(reading, "reading no puede ser null");
        checkFormat(unit, decimals);
        if (!Double.isFinite(divisor) || divisor <= 0) {
            throw new IllegalArgumentException("divisor debe ser positivo y finito");
        }
        if (!reading.isValid()) {
            return unavailable(reading.state());
        }
        return valid(reading.value() / divisor, unit, decimals);
    }

    public static MetricDisplay unavailable(MetricState state) {
        Objects.requireNonNull(state, "state no puede ser null");
        if (state.isValid()) {
            throw new IllegalArgumentException("VALID requiere una lectura");
        }
        return new MetricDisplay(state, "N/D: " + state.label(), OptionalDouble.empty());
    }

    public boolean isValid() {
        return state.isValid();
    }

    private static MetricDisplay valid(double value, String unit, int decimals) {
        String number = String.format(Locale.ROOT, "%." + decimals + "f", value);
        return new MetricDisplay(MetricState.VALID, number + (unit.isBlank() ? "" : " " + unit),
                OptionalDouble.of(value));
    }

    private static void checkFormat(String unit, int decimals) {
        Objects.requireNonNull(unit, "unit no puede ser null");
        if (decimals < 0 || decimals > 3) {
            throw new IllegalArgumentException("decimals debe estar en [0, 3]");
        }
    }
}
