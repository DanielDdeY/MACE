package com.mace.domain.model;

import com.mace.domain.DomainFixtures;
import org.junit.jupiter.api.Test;

import static com.mace.domain.DomainFixtures.cpu;
import static com.mace.domain.DomainFixtures.gpu;
import static com.mace.domain.DomainFixtures.gpuAdapter;
import static com.mace.domain.DomainFixtures.missing;
import static com.mace.domain.DomainFixtures.snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas documentadas del adaptador de compatibilidad V2 → {@link TelemetrySnapshot}. */
class TelemetrySnapshotCompatibilityTest {

    @Test
    void eligeLaPrimeraGpuConTemperaturaValida() {
        GpuAdapter sinSensor = gpuAdapter(1, 0, "iGPU");
        GpuAdapter dedicada = gpuAdapter(2, 1, "dGPU");
        HardwareSnapshot v2 = snapshot(cpu(50),
                gpu(sinSensor, missing(MetricState.NO_PROVIDER)), gpu(dedicada, 66.5));

        TelemetrySnapshot legacy = TelemetrySnapshot.fromHardwareSnapshot(v2);

        assertTrue(legacy.gpuAvailable());
        assertEquals(66.5f, legacy.gpuTemp());
        assertEquals(1500, legacy.gpuFanRpm());
    }

    @Test
    void sinTemperaturaDeCpuElSensorSeMarcaNoDisponibleAunqueHayaUso() {
        CpuReading cpu = new CpuReading(CpuVendor.AMD, SensorProvider.WINDOWS, PowerScope.UNKNOWN,
                missing(MetricState.NO_PROVIDER), missing(MetricState.NO_PROVIDER), MetricReading.valid(33), 1L);

        TelemetrySnapshot legacy = TelemetrySnapshot.fromHardwareSnapshot(snapshot(cpu));

        assertFalse(legacy.cpuSensorAvailable());
        assertEquals(0f, legacy.cpuTemp());
        assertEquals(0f, legacy.cpuWatts());
        assertEquals(33f, legacy.cpuUsage());
        assertFalse(legacy.gpuAvailable());
    }

    @Test
    void elUsoCalentandoSeTraduceACeroEnLaVistaHeredada() {
        CpuReading cpu = new CpuReading(CpuVendor.INTEL, SensorProvider.INTEL_MSR, PowerScope.CPU_PACKAGE,
                MetricReading.valid(40), missing(MetricState.WARMING_UP), missing(MetricState.WARMING_UP), 1L);

        TelemetrySnapshot legacy = TelemetrySnapshot.fromHardwareSnapshot(DomainFixtures.snapshot(cpu));

        assertTrue(legacy.cpuSensorAvailable());
        assertEquals(0f, legacy.cpuUsage());
        assertEquals(0f, legacy.cpuWatts());
    }
}
