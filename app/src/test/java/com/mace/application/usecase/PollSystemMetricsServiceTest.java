package com.mace.application.usecase;

import com.mace.domain.DomainFixtures;
import com.mace.domain.model.AlertLevel;
import com.mace.domain.model.BlackboxEvent;
import com.mace.domain.model.BlackboxEventType;
import com.mace.domain.model.GpuAdapter;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.MetricReading;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.TelemetrySnapshot;
import com.mace.domain.model.ThermalAlert;
import com.mace.domain.port.out.BlackboxSinkPort;
import com.mace.domain.port.out.HardwareSensorsPort;
import com.mace.domain.port.out.TelemetryPublisherPort;
import com.mace.domain.service.ThermalAlertEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollSystemMetricsServiceTest {

    private static final GpuAdapter GPU = DomainFixtures.gpuAdapter(0x10, 0, "RTX");

    @Mock
    private HardwareSensorsPort hardwareSensorsPort;
    @Mock
    private TelemetryPublisherPort telemetryPublisherPort;
    @Mock
    private BlackboxSinkPort blackboxSinkPort;

    private PollSystemMetricsService service;

    @BeforeEach
    void setUp() {
        service = new PollSystemMetricsService(hardwareSensorsPort, telemetryPublisherPort, blackboxSinkPort,
                new ThermalAlertEvaluator());
    }

    @Test
    void unCicloHaceUnSoloSondeoFisicoYPublicaLaVistaHeredada() {
        HardwareSnapshot snapshot = DomainFixtures.snapshot(DomainFixtures.cpu(60), DomainFixtures.gpu(GPU, 55));
        when(hardwareSensorsPort.fetchHardwareSnapshot()).thenReturn(snapshot);

        HardwarePoll poll = service.pollHardware();

        assertSame(snapshot, poll.snapshot());
        assertTrue(poll.thermalAlerts().isEmpty());
        verify(hardwareSensorsPort, times(1)).fetchHardwareSnapshot();
        verify(hardwareSensorsPort, never()).fetchCurrentTelemetry();
        ArgumentCaptor<TelemetrySnapshot> legacy = ArgumentCaptor.forClass(TelemetrySnapshot.class);
        verify(telemetryPublisherPort).publish(legacy.capture());
        assertEquals(60f, legacy.getValue().cpuTemp());
        assertEquals(55f, legacy.getValue().gpuTemp());
        verify(blackboxSinkPort).record(snapshot);
    }

    @Test
    void lasAlertasSalenDelDominioPorCadaGpu() {
        when(hardwareSensorsPort.fetchHardwareSnapshot()).thenReturn(
                DomainFixtures.snapshot(DomainFixtures.cpu(50), DomainFixtures.gpu(GPU, 90)));

        List<ThermalAlert> alerts = service.pollOnce();

        assertEquals(1, alerts.size());
        assertEquals(AlertLevel.CRITICAL, alerts.get(0).level());
        assertEquals(GPU.id(), alerts.get(0).gpu().orElseThrow());
    }

    @Test
    void sensoresAusentesNoGeneranAlertasNiValoresFalsos() {
        when(hardwareSensorsPort.fetchHardwareSnapshot()).thenReturn(DomainFixtures.snapshot(
                DomainFixtures.cpu(MetricReading.unavailable(MetricState.NO_PROVIDER)),
                DomainFixtures.gpu(GPU, MetricReading.unavailable(MetricState.WARMING_UP))));

        assertTrue(service.pollOnce().isEmpty());
        ArgumentCaptor<TelemetrySnapshot> legacy = ArgumentCaptor.forClass(TelemetrySnapshot.class);
        verify(telemetryPublisherPort).publish(legacy.capture());
        assertFalse(legacy.getValue().cpuSensorAvailable());
        assertFalse(legacy.getValue().gpuAvailable());
    }

    @Test
    void laCajaNegraRegistraSoloLosCambiosDeNivel() {
        when(hardwareSensorsPort.fetchHardwareSnapshot()).thenReturn(
                DomainFixtures.snapshot(DomainFixtures.cpu(80)),
                DomainFixtures.snapshot(DomainFixtures.cpu(81)),
                DomainFixtures.snapshot(DomainFixtures.cpu(90)),
                DomainFixtures.snapshot(DomainFixtures.cpu(50)),
                DomainFixtures.snapshot(DomainFixtures.cpu(80)));

        for (int i = 0; i < 5; i++) {
            service.pollOnce();
        }

        ArgumentCaptor<BlackboxEvent> events = ArgumentCaptor.forClass(BlackboxEvent.class);
        verify(blackboxSinkPort, times(3)).recordEvent(events.capture());
        assertTrue(events.getAllValues().stream().allMatch(e -> e.type() == BlackboxEventType.THERMAL_ALERT));
        assertTrue(events.getAllValues().get(0).detail().startsWith("WARNING"));
        assertTrue(events.getAllValues().get(1).detail().startsWith("CRITICAL"));
        verify(blackboxSinkPort, times(5)).record(any(HardwareSnapshot.class));
    }
}
