package com.mace.domain.service;

import com.mace.domain.model.ProcessId;
import com.mace.domain.model.ProcessIdentity;

import java.util.Set;

/**
 * Servicio de dominio que identifica procesos que la aplicación NUNCA debe intentar terminar,
 * incluso ante una acción manual explícita del usuario.
 *
 * <p>Controles propios del dominio: PID reservados por el kernel de Windows (0 = System Idle,
 * 4 = System) y el propio proceso de MACE. La DLL es la defensa final: además rechaza procesos
 * críticos, nombres protegidos y rutas del sistema, y vuelve a comprobar la identidad.</p>
 */
public final class SystemProcessGuard {

    private static final Set<Long> PROTECTED_PIDS = Set.of(0L, 4L);

    private final long ownPid;

    /** Guarda para el proceso JVM actual. */
    public SystemProcessGuard() {
        this(ProcessHandle.current().pid());
    }

    /** @param ownPid PID de MACE (inyectable para pruebas) */
    public SystemProcessGuard(long ownPid) {
        this.ownPid = ownPid;
    }

    /**
     * @param pid identificador a validar
     * @return true si el PID corresponde a un proceso protegido del sistema
     */
    public boolean isSystemProcess(ProcessId pid) {
        return PROTECTED_PIDS.contains(pid.value());
    }

    /** @return true si el PID es el de la propia aplicación */
    public boolean isOwnProcess(ProcessId pid) {
        return pid.value() == ownPid;
    }

    /** @return true si la identidad no debe terminarse desde MACE */
    public boolean isProtected(ProcessIdentity identity) {
        return isSystemProcess(identity.pid()) || isOwnProcess(identity.pid());
    }
}
