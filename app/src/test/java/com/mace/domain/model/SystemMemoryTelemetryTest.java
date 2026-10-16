package com.mace.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemMemoryTelemetryTest {

    @Test
    void cadaMetricaDeMemoriaTieneEstadoIndependiente() {
        SystemMemoryTelemetry memory = new SystemMemoryTelemetry(
                new MetricReading<>(16L * 1024 * 1024 * 1024, MetricState.VALID, "bytes"),
                new MetricReading<>(null, MetricState.NO_PROVIDER, "bytes"),
                new MetricReading<>(4L * 1024 * 1024 * 1024, MetricState.VALID, "bytes")
        );

        assertTrue(memory.totalBytes().isValid());
        assertFalse(memory.availableBytes().isValid());
        assertTrue(memory.usedBytes().isValid());
    }
}
