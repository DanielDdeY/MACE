package com.mace.application.usecase;

import com.mace.domain.model.TelemetrySnapshot;
import com.mace.domain.model.ThermalAlert;
import com.mace.domain.port.out.BlackboxSinkPort;
import com.mace.domain.port.out.HardwareSensorsPort;
import com.mace.domain.port.out.TelemetryPublisherPort;
import com.mace.domain.service.ThermalAlertEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollSystemMetricsServiceTest {

    @Mock
    private HardwareSensorsPort hardwareSensorsPort;
    @Mock
    private TelemetryPublisherPort telemetryPublisherPort;
    @Mock
    private BlackboxSinkPort blackboxSinkPort;

    private PollSystemMetricsService service;

    @BeforeEach
    void setUp() {
        service = new PollSystemMetricsService(
                hardwareSensorsPort,
                telemetryPublisherPort,
                blackboxSinkPort,
                new ThermalAlertEvaluator()
        );
    }

    @Test
    void publicaElSnapshotYVuelcaACajaNegraCuandoHayAlertaCritica() {
        TelemetrySnapshot criticalSnapshot = new TelemetrySnapshot(
                90f, 200f, 50f, 100f, 1500, true, Instant.now());
        when(hardwareSensorsPort.fetchCurrentTelemetry()).thenReturn(criticalSnapshot);

        List<ThermalAlert> alerts = service.pollOnce();

        assertTrue(alerts.stream().anyMatch(a -> a.source().equals("CPU")));
        verify(telemetryPublisherPort).publish(criticalSnapshot);
        verify(blackboxSinkPort).dump(criticalSnapshot);
    }

    @Test
    void noVuelcaACajaNegraCuandoNoHayAlertaCritica() {
        TelemetrySnapshot safeSnapshot = new TelemetrySnapshot(
                60f, 100f, 55f, 120f, 1500, true, Instant.now());
        when(hardwareSensorsPort.fetchCurrentTelemetry()).thenReturn(safeSnapshot);

        List<ThermalAlert> alerts = service.pollOnce();

        assertTrue(alerts.isEmpty());
        verify(telemetryPublisherPort).publish(safeSnapshot);
        verify(blackboxSinkPort, never()).dump(safeSnapshot);
    }
}
