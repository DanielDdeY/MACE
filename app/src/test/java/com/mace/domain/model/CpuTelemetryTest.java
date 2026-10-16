package com.mace.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpuTelemetryTest {

    @Test
    void cadaMetricaTieneEstadoIndependiente() {
        CpuTelemetry cpu = new CpuTelemetry(
                new MetricReading<>(70f, MetricState.VALID, "°C"),
                new MetricReading<>(null, MetricState.NO_PROVIDER, "W"),
                new MetricReading<>(45f, MetricState.VALID, "%")
        );

        assertTrue(cpu.temperatureCelsius().isValid());
        assertFalse(cpu.watts().isValid());
        assertTrue(cpu.usagePercent().isValid());
        assertNullWatts(cpu);
    }

    private void assertNullWatts(CpuTelemetry cpu) {
        assertEquals(null, cpu.watts().value());
    }

    @Test
    void valorCeroConEstadoNoValidoNoSeTrataComoMedicion() {
        CpuTelemetry cpu = new CpuTelemetry(
                new MetricReading<>(0f, MetricState.ERROR, "°C"),
                new MetricReading<>(0f, MetricState.NO_PROVIDER, "W"),
                new MetricReading<>(0f, MetricState.VALID, "%")
        );

        assertFalse(cpu.temperatureCelsius().isValid());
        assertFalse(cpu.watts().isValid());
        assertTrue(cpu.usagePercent().isValid());
    }
}
