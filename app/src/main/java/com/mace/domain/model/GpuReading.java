package com.mace.domain.model;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Lectura de una GPU concreta ({@code MaceGpuTelemetryV2}), siempre asociada al adaptador
 * consultado: nunca se mezclan valores de varias GPU.
 *
 * <p>Que la GPU aparezca en la lista no implica que tenga sensores: «GPU detectada, sensor no
 * disponible» se expresa con estados como {@link MetricState#NO_PROVIDER}. Una iGPU puede tener
 * temperatura sin ventilador propio ({@link MetricState#UNSUPPORTED} en {@code fanRpm}).</p>
 *
 * @param adapter              GPU a la que pertenecen las lecturas
 * @param provider             proveedor (NVML, ADLX, IGCL o ninguno)
 * @param powerScope           ámbito de {@code powerWatts} (GPU board / GPU chip)
 * @param temperatureCelsius   temperatura en °C
 * @param powerWatts           potencia en W
 * @param usagePercent         uso [0, 100]
 * @param fanRpm               ventilador en RPM reales
 * @param fanPercent           ventilador en porcentaje entero
 * @param videoMemoryUsedBytes VRAM usada
 * @param videoMemoryTotalBytes VRAM total
 * @param monotonicNanos       marca monotónica nativa
 */
public record GpuReading(
        GpuAdapter adapter,
        SensorProvider provider,
        PowerScope powerScope,
        MetricReading temperatureCelsius,
        MetricReading powerWatts,
        MetricReading usagePercent,
        LongMetricReading fanRpm,
        LongMetricReading fanPercent,
        LongMetricReading videoMemoryUsedBytes,
        LongMetricReading videoMemoryTotalBytes,
        long monotonicNanos
) {

    public GpuReading {
        Objects.requireNonNull(adapter, "adapter no puede ser null");
        Objects.requireNonNull(provider, "provider no puede ser null");
        Objects.requireNonNull(powerScope, "powerScope no puede ser null");
        Objects.requireNonNull(temperatureCelsius, "temperatureCelsius no puede ser null");
        Objects.requireNonNull(powerWatts, "powerWatts no puede ser null");
        Objects.requireNonNull(usagePercent, "usagePercent no puede ser null");
        Objects.requireNonNull(fanRpm, "fanRpm no puede ser null");
        Objects.requireNonNull(fanPercent, "fanPercent no puede ser null");
        Objects.requireNonNull(videoMemoryUsedBytes, "videoMemoryUsedBytes no puede ser null");
        Objects.requireNonNull(videoMemoryTotalBytes, "videoMemoryTotalBytes no puede ser null");
    }

    /**
     * GPU enumerada cuyo sondeo no produjo lecturas (adaptador desaparecido, telemetría no
     * inicializada, error de lectura): todas las métricas llevan {@code state}.
     */
    public static GpuReading unavailable(GpuAdapter adapter, MetricState state, long monotonicNanos) {
        MetricReading missing = MetricReading.unavailable(state);
        LongMetricReading missingLong = LongMetricReading.unavailable(state);
        return new GpuReading(adapter, SensorProvider.NONE, PowerScope.UNKNOWN,
                missing, missing, missing, missingLong, missingLong, missingLong, missingLong, monotonicNanos);
    }

    public GpuAdapterId adapterId() {
        return adapter.id();
    }

    /** @return {@code true} si al menos una métrica de esta GPU es válida */
    public boolean hasAnyValidMetric() {
        return states().anyMatch(MetricState::isValid);
    }

    /**
     * @return {@code true} si la GPU está apagada o en reposo: ninguna métrica válida y al menos
     *         una marcada {@link MetricState#DEVICE_INACTIVE}
     */
    public boolean isInactive() {
        return !hasAnyValidMetric() && states().anyMatch(state -> state == MetricState.DEVICE_INACTIVE);
    }

    private Stream<MetricState> states() {
        return Stream.of(temperatureCelsius.state(), powerWatts.state(), usagePercent.state(),
                fanRpm.state(), fanPercent.state(), videoMemoryUsedBytes.state(), videoMemoryTotalBytes.state());
    }
}
