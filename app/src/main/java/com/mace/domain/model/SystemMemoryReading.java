package com.mace.domain.model;

import java.util.Objects;

/**
 * Memoria física global de Windows ({@code MaceSystemMemoryV2}).
 *
 * @param totalBytes     memoria física total
 * @param availableBytes memoria física disponible
 * @param monotonicNanos marca monotónica nativa
 */
public record SystemMemoryReading(LongMetricReading totalBytes, LongMetricReading availableBytes, long monotonicNanos) {

    public SystemMemoryReading {
        Objects.requireNonNull(totalBytes, "totalBytes no puede ser null");
        Objects.requireNonNull(availableBytes, "availableBytes no puede ser null");
    }

    public static SystemMemoryReading unavailable(MetricState state, long monotonicNanos) {
        LongMetricReading missing = LongMetricReading.unavailable(state);
        return new SystemMemoryReading(missing, missing, monotonicNanos);
    }

    /** Memoria en uso; válida solo si total y disponible lo son. */
    public LongMetricReading usedBytes() {
        if (!totalBytes.isValid()) {
            return LongMetricReading.unavailable(totalBytes.state());
        }
        if (!availableBytes.isValid()) {
            return LongMetricReading.unavailable(availableBytes.state());
        }
        return LongMetricReading.valid(Math.max(0L, totalBytes.value() - availableBytes.value()));
    }

    /** Porcentaje de memoria en uso [0, 100]; válido solo si hay total &gt; 0 y disponible. */
    public MetricReading usedPercent() {
        LongMetricReading used = usedBytes();
        if (!used.isValid()) {
            return MetricReading.unavailable(used.state());
        }
        if (totalBytes.value() == 0L) {
            return MetricReading.unavailable(MetricState.READ_FAILED);
        }
        return MetricReading.valid(used.value() * 100.0 / totalBytes.value());
    }
}
