package com.corepulse.application.usecase;

import com.corepulse.domain.model.ProcessId;
import com.corepulse.domain.port.out.ProcessLifecyclePort;
import com.corepulse.domain.service.SystemProcessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminateProcessServiceTest {

    @Mock
    private ProcessLifecyclePort processLifecyclePort;

    private TerminateProcessService service;

    @BeforeEach
    void setUp() {
        service = new TerminateProcessService(processLifecyclePort, new SystemProcessGuard());
    }

    @Test
    void terminaUnProcesoDeUsuarioExitosamente() {
        ProcessId pid = new ProcessId(4321L);
        when(processLifecyclePort.terminateProcess(pid)).thenReturn(true);

        TerminationResult result = service.terminate(pid);

        assertEquals(TerminationStatus.SUCCESS, result.status());
        verify(processLifecyclePort).terminateProcess(pid);
    }

    @Test
    void devuelveFailedCuandoElPuertoNoLogroTerminarElProceso() {
        ProcessId pid = new ProcessId(4321L);
        when(processLifecyclePort.terminateProcess(pid)).thenReturn(false);

        TerminationResult result = service.terminate(pid);

        assertEquals(TerminationStatus.FAILED, result.status());
    }

    @Test
    void rechazaTerminarElProcesoSystemIdleSinLlamarAlPuerto() {
        ProcessId systemIdlePid = new ProcessId(0L);

        TerminationResult result = service.terminate(systemIdlePid);

        assertEquals(TerminationStatus.REJECTED_SYSTEM_PROCESS, result.status());
        verifyNoInteractions(processLifecyclePort);
    }

    @Test
    void rechazaTerminarElProcesoSystemSinLlamarAlPuerto() {
        ProcessId systemPid = new ProcessId(4L);

        TerminationResult result = service.terminate(systemPid);

        assertEquals(TerminationStatus.REJECTED_SYSTEM_PROCESS, result.status());
        verifyNoInteractions(processLifecyclePort);
    }
}
