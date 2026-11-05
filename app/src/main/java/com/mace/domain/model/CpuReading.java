package com.mace.domain.model;

import java.util.Objects;

/**
 * Lectura de CPU de un sondeo ({@code MaceCpuTelemetryV2}) con estado independiente por
 * métrica. En CPU AMD la temperatura depende del Ryzen Master Monitoring SDK instalado y de
 * elevación; la potencia AMD no tiene proveedor. Intel depende del acceso opcional a MSR.
 *
 * @param vendor             fabricante detectado
 * @param provider           proveedor de temperatura/potencia ({@link SensorProvider#WINDOWS} si solo hay uso)
 * @param powerScope         ámbito de {@code power}; nunca «consumo total del equipo»
 * @param temperatureCelsius temperatura en °C
 * @param powerWatts         potencia en W en el ámbito {@code powerScope}
 * @param usagePercent       uso global [0, 100] calculado por Windows
 * @param monotonicNanos     marca monotónica nativa; sirve para intervalos, no es fecha UTC
 */
public record CpuReading(
        CpuVendor vendor,
        SensorProvider provider,
        PowerScope powerScope,
        MetricReading temperatureCelsius,
        MetricReading powerWatts,
        MetricReading usagePercent,
        long monotonicNanos
) {

    public CpuReading {
        Objects.requireNonNull(vendor, "vendor no puede ser null");
        Objects.requireNonNull(provider, "provider no puede ser null");
        Objects.requireNonNull(powerScope, "powerScope no puede ser null");
        Objects.requireNonNull(temperatureCelsius, "temperatureCelsius no puede ser null");
        Objects.requireNonNull(powerWatts, "powerWatts no puede ser null");
        Objects.requireNonNull(usagePercent, "usagePercent no puede ser null");
    }

    /** CPU sin ninguna métrica disponible (p. ej. telemetría no inicializada). */
    public static CpuReading unavailable(MetricState state, long monotonicNanos) {
        MetricReading missing = MetricReading.unavailable(state);
        return new CpuReading(CpuVendor.UNKNOWN, SensorProvider.NONE, PowerScope.UNKNOWN,
                missing, missing, missing, monotonicNanos);
    }
}
