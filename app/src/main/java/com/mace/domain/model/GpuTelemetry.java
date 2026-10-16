package com.mace.domain.model;

import java.util.Objects;
import java.util.List;

/**
 * Telemetría de una única GPU con estado independiente por métrica.
 *
 * @param adapter identificador de sesión y datos del adaptador de GPU
 * @param temperatureCelsius temperatura de GPU en grados Celsius
 * @param watts consumo de GPU en vatios
 * @param fanRpm velocidad del ventilador de GPU en RPM
 */
public record GpuTelemetry(
        GpuAdapter adapter,
        MetricReading<Float> temperatureCelsius,
        MetricReading<Float> watts,
        MetricReading<Integer> fanRpm
) {

    public GpuTelemetry {
        Objects.requireNonNull(adapter, "adapter no puede ser null");
        Objects.requireNonNull(temperatureCelsius, "temperatureCelsius no puede ser null");
        Objects.requireNonNull(watts, "watts no puede ser null");
        Objects.requireNonNull(fanRpm, "fanRpm no puede ser null");
    }

    /**
     * Indica si al menos una métrica de esta GPU tiene estado válido.
     *
     * @return {@code true} si alguna métrica es {@link MetricState#VALID}
     */
    public boolean hasAnyValidMetric() {
        return temperatureCelsius.isValid()
                || watts.isValid()
                || fanRpm.isValid();
    }
}
