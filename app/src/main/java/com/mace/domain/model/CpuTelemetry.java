package com.mace.domain.model;

import java.util.Objects;

/**
 * Telemetría de CPU con estado independiente por métrica.
 *
 * <p>Cada métrica (temperatura, consumo, uso) lleva su propio {@link MetricState},
 * por lo que es posible tener temperatura válida pero consumo no disponible, por ejemplo.</p>
 *
 * @param temperatureCelsius temperatura de CPU en grados Celsius
 * @param watts consumo de CPU en vatios
 * @param usagePercent uso de CPU de todo el sistema, en porcentaje [0..100]
 */
public record CpuTelemetry(
        MetricReading<Float> temperatureCelsius,
        MetricReading<Float> watts,
        MetricReading<Float> usagePercent
) {

    public CpuTelemetry {
        Objects.requireNonNull(temperatureCelsius, "temperatureCelsius no puede ser null");
        Objects.requireNonNull(watts, "watts no puede ser null");
        Objects.requireNonNull(usagePercent, "usagePercent no puede ser null");
    }
}
