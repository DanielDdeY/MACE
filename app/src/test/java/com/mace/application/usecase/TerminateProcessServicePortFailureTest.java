package com.mace.application.usecase;

import com.mace.domain.model.ProcessId;
import com.mace.domain.port.out.ProcessLifecyclePort;
import com.mace.domain.service.SystemProcessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminateProcessServicePortFailureTest {

    @Mock
    private ProcessLifecyclePort processLifecyclePort;

    private TerminateProcessService service;

    @BeforeEach
    void setUp() {
        service = new TerminateProcessService(processLifecyclePort, new SystemProcessGuard());
    }

    @Test
    void falloDelPuertoSePropagaComoResultadoFailed() {
        ProcessId pid = new ProcessId(4321L);
        when(processLifecyclePort.terminateProcess(pid)).thenReturn(false);

        TerminationResult result = service.terminate(pid);

        assertEquals(TerminationStatus.FAILED, result.status());
        assertFalse(result.isSuccess());
        verify(processLifecyclePort).terminateProcess(pid);
    }

    @Test
    void pidProteccionSystemIdleNoLlamaAlPuerto() {
        ProcessId systemIdle = new ProcessId(0L);

        TerminationResult result = service.terminate(systemIdle);

        assertEquals(TerminationStatus.REJECTED_SYSTEM_PROCESS, result.status());
        verifyNoInteractions(processLifecyclePort);
    }

    @Test
    void pidProteccionSystemNoLlamaAlPuerto() {
        ProcessId system = new ProcessId(4L);

        TerminationResult result = service.terminate(system);

        assertEquals(TerminationStatus.REJECTED_SYSTEM_PROCESS, result.status());
        verifyNoInteractions(processLifecyclePort);
    }

    @Test
    void pidNullLanzaExcepcionSinLlamarAlPuerto() {
        assertThrows(NullPointerException.class, () -> service.terminate(null));
        verifyNoInteractions(processLifecyclePort);
    }

    @Test
    void cierreSoloOcurrePorLlamadaManualExplicita() {
        // El servicio no programa ningún cierre automático; solo responde a terminate(pid).
        ProcessId pid = new ProcessId(9999L);
        when(processLifecyclePort.terminateProcess(pid)).thenReturn(true);

        TerminationResult first = service.terminate(pid);
        TerminationResult second = service.terminate(pid);

        assertEquals(TerminationStatus.SUCCESS, first.status());
        assertEquals(TerminationStatus.SUCCESS, second.status());
        verify(processLifecyclePort, times(2)).terminateProcess(pid);
    }
}
