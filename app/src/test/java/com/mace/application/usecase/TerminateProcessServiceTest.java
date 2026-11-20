package com.mace.application.usecase;

import com.mace.domain.model.BlackboxEvent;
import com.mace.domain.model.BlackboxEventType;
import com.mace.domain.model.ProcessId;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.TerminationAttempt;
import com.mace.domain.model.TerminationOutcome;
import com.mace.domain.port.out.BlackboxSinkPort;
import com.mace.domain.port.out.ProcessLifecyclePort;
import com.mace.domain.service.SystemProcessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminateProcessServiceTest {

    private static final long OWN_PID = 4242L;
    private static final ProcessIdentity CHROME = ProcessIdentity.of(4120, 133_000_000_000_000_000L);

    @Mock
    private ProcessLifecyclePort processLifecyclePort;
    @Mock
    private BlackboxSinkPort blackbox;

    private TerminateProcessService service;

    @BeforeEach
    void setUp() {
        service = new TerminateProcessService(processLifecyclePort, new SystemProcessGuard(OWN_PID), blackbox,
                Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void entregaAlPuertoLaIdentidadExactaDeLaFila() {
        when(processLifecyclePort.terminate(CHROME)).thenReturn(new TerminationAttempt(CHROME, TerminationOutcome.TERMINATED, 0));

        TerminationResult result = service.terminate(CHROME);

        assertTrue(result.isSuccess());
        assertEquals(CHROME, result.identity());
        verify(processLifecyclePort).terminate(CHROME);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "PROTECTED_PROCESS, REJECTED_PROTECTED_PROCESS",
            "ACCESS_DENIED, ACCESS_DENIED",
            "PROCESS_EXITED, PROCESS_EXITED",
            "PID_REUSED, PID_REUSED",
            "INVALID_IDENTITY, FAILED",
            "FAILED, FAILED"
    })
    void cadaRechazoSePropagaComoUnResultadoDistinto(TerminationOutcome outcome, TerminationStatus expected) {
        when(processLifecyclePort.terminate(CHROME)).thenReturn(new TerminationAttempt(CHROME, outcome, 87));

        TerminationResult result = service.terminate(CHROME);

        assertEquals(expected, result.status());
        assertEquals(87, result.win32Error(), "el código Win32 se conserva para diagnóstico");
        assertFalse(result.isSuccess());
        verify(processLifecyclePort, times(1)).terminate(any());
    }

    @Test
    void unPidReutilizadoNoSeReintentaConElPidSolo() {
        when(processLifecyclePort.terminate(CHROME)).thenReturn(new TerminationAttempt(CHROME, TerminationOutcome.PID_REUSED, 0));

        TerminationResult result = service.terminate(CHROME);

        assertEquals(TerminationStatus.PID_REUSED, result.status());
        assertTrue(result.status().requiresRefresh());
        verify(processLifecyclePort, times(1)).terminate(any());
    }

    @Test
    void rechazaPidReservadosSinTocarLaInfraestructura() {
        TerminationResult idle = service.terminate(ProcessIdentity.of(0, 1));
        TerminationResult system = service.terminate(ProcessIdentity.of(4, 1));

        assertEquals(TerminationStatus.REJECTED_SYSTEM_PROCESS, idle.status());
        assertEquals(TerminationStatus.REJECTED_SYSTEM_PROCESS, system.status());
        verifyNoInteractions(processLifecyclePort);
    }

    @Test
    void rechazaElPropioProcesoDeMace() {
        TerminationResult result = service.terminate(ProcessIdentity.of(OWN_PID, 1));

        assertEquals(TerminationStatus.REJECTED_SYSTEM_PROCESS, result.status());
        verifyNoInteractions(processLifecyclePort);
    }

    @Test
    @SuppressWarnings("deprecation")
    void laRutaHeredadaPorPidNuncaTerminaProcesos() {
        TerminationResult result = service.terminate(new ProcessId(4120));

        assertEquals(TerminationStatus.IDENTITY_REQUIRED, result.status());
        assertTrue(result.identityOptional().isEmpty());
        verifyNoInteractions(processLifecyclePort);
    }

    @Test
    void registraSolicitudYResultadoEnLaCajaNegra() {
        when(processLifecyclePort.terminate(CHROME)).thenReturn(new TerminationAttempt(CHROME, TerminationOutcome.ACCESS_DENIED, 5));

        service.terminate(CHROME);

        ArgumentCaptor<BlackboxEvent> events = ArgumentCaptor.forClass(BlackboxEvent.class);
        verify(blackbox, times(2)).recordEvent(events.capture());
        List<BlackboxEvent> recorded = events.getAllValues();
        assertEquals(BlackboxEventType.TERMINATION_REQUEST, recorded.get(0).type());
        assertEquals(BlackboxEventType.TERMINATION_RESULT, recorded.get(1).type());
        assertTrue(recorded.get(1).detail().contains("ACCESS_DENIED"));
        assertTrue(recorded.get(1).detail().contains("win32=5"));
    }

    @Test
    void funcionaSinCajaNegra() {
        TerminateProcessService sinCaja = new TerminateProcessService(processLifecyclePort, new SystemProcessGuard(OWN_PID));
        when(processLifecyclePort.terminate(CHROME)).thenReturn(new TerminationAttempt(CHROME, TerminationOutcome.TERMINATED, 0));

        assertTrue(sinCaja.terminate(CHROME).isSuccess());
    }
}
