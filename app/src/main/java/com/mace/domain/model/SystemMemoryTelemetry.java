package com.mace.domain.model;

import java.util.Objects;

/**
 * Telemetría de memoria del sistema con estado independiente por métrica.
 *
 * @param totalBytes memoria física total del sistema en bytes
 * @param availableBytes memoria disponible en bytes
 * @param usedBytes memoria en uso en bytes
 */
public record SystemMemoryTelemetry(
        MetricReading<Long> totalBytes,
        MetricReading<Long> availableBytes,
        MetricReading<Long> usedBytes
) {

    public SystemMemoryTelemetry {
        Objects.requireNonNull(totalBytes, "totalBytes no puede ser null");
        Objects.requireNonNull(availableBytes, "availableBytes no puede ser null");
        Objects.requireNonNull(usedBytes, "usedBytes no puede ser null");
    }
}
