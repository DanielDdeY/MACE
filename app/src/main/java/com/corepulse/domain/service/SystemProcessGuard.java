package com.corepulse.domain.service;

import com.corepulse.domain.model.ProcessId;

import java.util.Set;

/**
 * Servicio de dominio que identifica procesos protegidos del sistema
 * operativo que NUNCA deben poder terminarse desde la aplicación,
 * incluso ante una acción manual explícita del usuario.
 *
 * En Windows, los PID 0 (System Idle Process) y 4 (System) son reservados
 * por el kernel. Esta guarda evita siquiera intentar la operación sobre ellos.
 */
public final class SystemProcessGuard {

    private static final Set<Long> PROTECTED_PIDS = Set.of(0L, 4L);

    /**
     * @param pid identificador a validar
     * @return true si el PID corresponde a un proceso protegido del sistema
     */
    public boolean isSystemProcess(ProcessId pid) {
        return PROTECTED_PIDS.contains(pid.value());
    }
}
