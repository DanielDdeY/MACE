package com.mace.application.usecase;

import com.mace.domain.model.CpuTelemetry;
import com.mace.domain.model.GpuAdapter;
import com.mace.domain.model.GpuTelemetry;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.MetricReading;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.SystemMemoryTelemetry;
import com.mace.domain.port.out.HardwareSensorsV2Port;
import com.mace.domain.port.out.TelemetryPublisherV2Port;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollSystemMetricsServiceV2Test {

    @Mock
    private HardwareSensorsV2Port hardwareSensorsV2Port;
    @Mock
    private TelemetryPublisherV2Port telemetryPublisherV2Port;

    private final Instant now = Instant.parse("2026-09-08T12:00:00Z");

    private PollSystemMetricsV2Service service;

    @BeforeEach
    void setUp() {
        service = new PollSystemMetricsV2Service(hardwareSensorsV2Port, telemetryPublisherV2Port);
    }

    @Test
    void publicaElSnapshotV2CuandoElPuertoLoProporciona() {
        HardwareSnapshot snapshot = snapshotConGpuValida();
        when(hardwareSensorsV2Port.fetchCurrentTelemetryV2()).thenReturn(snapshot);

        HardwareSnapshot resultado = service.pollOnce();

        assertSame(snapshot, resultado);
        verify(hardwareSensorsV2Port).fetchCurrentTelemetryV2();
        verify(telemetryPublisherV2Port).publish(snapshot);
        assertFalse(snapshot.gpus().isEmpty());
        assertTrue(snapshot.gpus().get(0).temperatureCelsius().isValid());
    }

    @Test
    void metricaNoValidaNoSeInterpretaComoMedicionCero() {
        HardwareSnapshot snapshot = snapshotConGpuDetectadaSinSensor();
        when(hardwareSensorsV2Port.fetchCurrentTelemetryV2()).thenReturn(snapshot);

        service.pollOnce();

        verify(telemetryPublisherV2Port).publish(snapshot);
        // La temperatura de GPU es NO_PROVIDER: no debe usarse como 0 °C válido.
        assertFalse(snapshot.gpus().get(0).temperatureCelsius().isValid());
        // El fan RPM es 0 pero es VALID: sí es una medición real.
        assertTrue(snapshot.gpus().get(0).fanRpm().isValid());
        assertEquals(0, snapshot.gpus().get(0).fanRpm().value().intValue());
    }

    @Test
    void falloDelPuertoSePropagaYNoPublica() {
        when(hardwareSensorsV2Port.fetchCurrentTelemetryV2())
                .thenThrow(new RuntimeException("DLL V2 no disponible"));

        assertThrows(RuntimeException.class, () -> service.pollOnce());
    }

    private HardwareSnapshot snapshotConGpuValida() {
        return new HardwareSnapshot(
                now, "1.2",
                new CpuTelemetry(
                        new MetricReading<>(60f, MetricState.VALID, "°C"),
                        new MetricReading<>(45f, MetricState.VALID, "W"),
                        new MetricReading<>(30f, MetricState.VALID, "%")
                ),
                List.of(new GpuTelemetry(
                        new GpuAdapter("GPU-0", "NVIDIA GPU", "PCI-01-00-0"),
                        new MetricReading<>(55f, MetricState.VALID, "°C"),
                        new MetricReading<>(100f, MetricState.VALID, "W"),
                        new MetricReading<>(1500, MetricState.VALID, "RPM")
                )),
                new SystemMemoryTelemetry(
                        new MetricReading<>(16L * 1024 * 1024 * 1024, MetricState.VALID, "bytes"),
                        new MetricReading<>(8L * 1024 * 1024 * 1024, MetricState.VALID, "bytes"),
                        new MetricReading<>(8L * 1024 * 1024 * 1024, MetricState.VALID, "bytes")
                )
        );
    }

    private HardwareSnapshot snapshotConGpuDetectadaSinSensor() {
        return new HardwareSnapshot(
                now, "1.2",
                new CpuTelemetry(
                        new MetricReading<>(60f, MetricState.VALID, "°C"),
                        new MetricReading<>(null, MetricState.NO_PROVIDER, "W"),
                        new MetricReading<>(30f, MetricState.VALID, "%")
                ),
                List.of(new GpuTelemetry(
                        new GpuAdapter("GPU-0", "NVIDIA GPU", "PCI-01-00-0"),
                        new MetricReading<>(null, MetricState.NO_PROVIDER, "°C"),
                        new MetricReading<>(null, MetricState.NO_PROVIDER, "W"),
                        new MetricReading<>(0, MetricState.VALID, "RPM")
                )),
                new SystemMemoryTelemetry(
                        new MetricReading<>(16L * 1024 * 1024 * 1024, MetricState.VALID, "bytes"),
                        new MetricReading<>(null, MetricState.NO_PROVIDER, "bytes"),
                        new MetricReading<>(4L * 1024 * 1024 * 1024, MetricState.VALID, "bytes")
                )
        );
    }
}
