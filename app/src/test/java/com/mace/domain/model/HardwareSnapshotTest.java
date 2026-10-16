package com.mace.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareSnapshotTest {

    private final Instant now = Instant.parse("2026-09-08T12:00:00Z");
    private final GpuAdapter adapter = new GpuAdapter("GPU-0", "NVIDIA GPU", "PCI-01-00-0");

    @Test
    void snapshotV2ContieneAbiVersionYListaDeGpus() {
        CpuTelemetry cpu = new CpuTelemetry(
                new MetricReading<>(60f, MetricState.VALID, "°C"),
                new MetricReading<>(45f, MetricState.VALID, "W"),
                new MetricReading<>(30f, MetricState.VALID, "%")
        );
        GpuTelemetry gpu = new GpuTelemetry(
                adapter,
                new MetricReading<>(55f, MetricState.VALID, "°C"),
                new MetricReading<>(100f, MetricState.VALID, "W"),
                new MetricReading<>(1500, MetricState.VALID, "RPM")
        );
        SystemMemoryTelemetry memory = new SystemMemoryTelemetry(
                new MetricReading<>(16L * 1024 * 1024 * 1024, MetricState.VALID, "bytes"),
                new MetricReading<>(8L * 1024 * 1024 * 1024, MetricState.VALID, "bytes"),
                new MetricReading<>(8L * 1024 * 1024 * 1024, MetricState.VALID, "bytes")
        );

        HardwareSnapshot snapshot = new HardwareSnapshot(now, "1.2", cpu, List.of(gpu), memory);

        assertSame(now, snapshot.timestamp());
        assertEquals("1.2", snapshot.abiVersion());
        assertSame(cpu, snapshot.cpu());
        assertEquals(1, snapshot.gpus().size());
        assertSame(gpu, snapshot.gpus().get(0));
        assertSame(memory, snapshot.systemMemory());
    }

    @Test
    void listaDeGpusVaciaCuandoNoHayGpu() {
        CpuTelemetry cpu = new CpuTelemetry(
                new MetricReading<>(60f, MetricState.VALID, "°C"),
                new MetricReading<>(45f, MetricState.VALID, "W"),
                new MetricReading<>(30f, MetricState.VALID, "%")
        );
        SystemMemoryTelemetry memory = new SystemMemoryTelemetry(
                new MetricReading<>(16L * 1024 * 1024 * 1024, MetricState.VALID, "bytes"),
                new MetricReading<>(8L * 1024 * 1024 * 1024, MetricState.VALID, "bytes"),
                new MetricReading<>(8L * 1024 * 1024 * 1024, MetricState.VALID, "bytes")
        );

        HardwareSnapshot snapshot = new HardwareSnapshot(now, "1.2", cpu, List.of(), memory);

        assertTrue(snapshot.gpus().isEmpty());
    }

    @Test
    void timestampNuloLanzaExcepcion() {
        CpuTelemetry cpu = new CpuTelemetry(
                new MetricReading<>(60f, MetricState.VALID, "°C"),
                new MetricReading<>(45f, MetricState.VALID, "W"),
                new MetricReading<>(30f, MetricState.VALID, "%")
        );
        SystemMemoryTelemetry memory = new SystemMemoryTelemetry(
                new MetricReading<>(16L * 1024 * 1024 * 1024, MetricState.VALID, "bytes"),
                new MetricReading<>(8L * 1024 * 1024 * 1024, MetricState.VALID, "bytes"),
                new MetricReading<>(8L * 1024 * 1024 * 1024, MetricState.VALID, "bytes")
        );

        assertThrows(NullPointerException.class,
                () -> new HardwareSnapshot(null, "1.2", cpu, List.of(), memory));
    }
}
