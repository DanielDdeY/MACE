package com.corepulse.application.usecase;

import com.corepulse.domain.model.ProcessId;
import com.corepulse.domain.port.out.ProcessLifecyclePort;
import com.corepulse.domain.service.SystemProcessGuard;

import java.util.Objects;

/**
 * Caso de uso: cierre manual de un proceso solicitado explícitamente por el usuario.
 *
 * Principio no negociable: esta clase NUNCA debe invocarse automáticamente
 * (sin auto-kill, sin suspensión automática por parte del sistema). Su única
 * responsabilidad es validar la solicitud puntual del usuario y delegarla
 * al puerto de infraestructura correspondiente.
 */
public final class TerminateProcessService {

    private final ProcessLifecyclePort processLifecyclePort;
    private final SystemProcessGuard systemProcessGuard;

    public TerminateProcessService(ProcessLifecyclePort processLifecyclePort, SystemProcessGuard systemProcessGuard) {
        this.processLifecyclePort = Objects.requireNonNull(processLifecyclePort, "processLifecyclePort no puede ser null");
        this.systemProcessGuard = Objects.requireNonNull(systemProcessGuard, "systemProcessGuard no puede ser null");
    }

    /**
     * Ejecuta el cierre manual de un proceso.
     *
     * @param pid proceso objetivo, tal como fue seleccionado explícitamente por el usuario en la UI
     * @return resultado de la operación
     */
    public TerminationResult terminate(ProcessId pid) {
        Objects.requireNonNull(pid, "pid no puede ser null");

        if (systemProcessGuard.isSystemProcess(pid)) {
            return TerminationResult.rejectedSystemProcess(pid);
        }

        boolean terminated = processLifecyclePort.terminateProcess(pid);
        return terminated ? TerminationResult.success(pid) : TerminationResult.failed(pid);
    }
}
