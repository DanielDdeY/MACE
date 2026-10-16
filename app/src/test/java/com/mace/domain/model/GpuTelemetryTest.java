package com.mace.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuTelemetryTest {

    @Test
    void distingueGpuDetectadaSinTemperaturaDeGpuAusente() {
        GpuAdapter adapter = new GpuAdapter("GPU-0", "NVIDIA GPU", "PCI-01-00-0");

        // GPU detectada pero sin sensor de temperatura: NO_PROVIDER, no "0 °C válido"
        GpuTelemetry telemetry = new GpuTelemetry(
                adapter,
                new MetricReading<>(null, MetricState.NO_PROVIDER, "°C"),
                new MetricReading<>(null, MetricState.NO_PROVIDER, "W"),
                new MetricReading<>(0, MetricState.VALID, "RPM")
        );

        assertEquals(adapter, telemetry.adapter());
        assertFalse(telemetry.temperatureCelsius().isValid());
        assertFalse(telemetry.watts().isValid());
        assertTrue(telemetry.fanRpm().isValid());
        assertTrue(telemetry.hasAnyValidMetric());
    }

    @Test
    void hasAnyValidMetricEsFalsoCuandoNadaEsValido() {
        GpuAdapter adapter = new GpuAdapter("GPU-1", "Otra", null);
        GpuTelemetry telemetry = new GpuTelemetry(
                adapter,
                new MetricReading<>(null, MetricState.ERROR, "°C"),
                new MetricReading<>(null, MetricState.ERROR, "W"),
                new MetricReading<>(null, MetricState.ERROR, "RPM")
        );

        assertFalse(telemetry.hasAnyValidMetric());
    }
}
