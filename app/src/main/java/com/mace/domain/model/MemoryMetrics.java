package com.mace.domain.model;

import java.util.Objects;

/**
 * Métricas de memoria de un proceso, con estado independiente por serie.
 *
 * <p>Working Set y Private Bytes son series distintas: {@code privateBytes} puede superar al
 * Working Set (memoria comprometida no residente), por lo que no se impone ninguna relación.</p>
 *
 * @param workingSetReading   memoria física residente (Working Set)
 * @param privateBytesReading memoria privada comprometida (Private Bytes / PrivateUsage)
 */
public record MemoryMetrics(LongMetricReading workingSetReading, LongMetricReading privateBytesReading) {

    public MemoryMetrics {
        Objects.requireNonNull(workingSetReading, "workingSetReading no puede ser null");
        Objects.requireNonNull(privateBytesReading, "privateBytesReading no puede ser null");
    }

    /** Ambas series válidas, en bytes. */
    public MemoryMetrics(long workingSetBytes, long privateBytes) {
        this(LongMetricReading.valid(workingSetBytes), LongMetricReading.valid(privateBytes));
    }

    /** Ninguna serie disponible por {@code state}. */
    public static MemoryMetrics unavailable(MetricState state) {
        LongMetricReading missing = LongMetricReading.unavailable(state);
        return new MemoryMetrics(missing, missing);
    }

    /**
     * Compatibilidad: Working Set en bytes o 0 si no es válido. Preferir
     * {@link #workingSetReading()} para distinguir «0» de «no disponible».
     */
    public long workingSetBytes() {
        return workingSetReading.orElse(0L);
    }

    /**
     * Compatibilidad: Private Bytes o 0 si no es válido. Preferir {@link #privateBytesReading()}.
     */
    public long privateBytes() {
        return privateBytesReading.orElse(0L);
    }
}
