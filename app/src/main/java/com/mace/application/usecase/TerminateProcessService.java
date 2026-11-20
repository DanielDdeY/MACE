package com.mace.application.usecase;

import com.mace.domain.model.BlackboxEvent;
import com.mace.domain.model.BlackboxEventType;
import com.mace.domain.model.ProcessId;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.TerminationAttempt;
import com.mace.domain.port.out.BlackboxSinkPort;
import com.mace.domain.port.out.ProcessLifecyclePort;
import com.mace.domain.service.SystemProcessGuard;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Caso de uso: cierre manual de un proceso solicitado explícitamente por el usuario.
 *
 * Principio no negociable: esta clase NUNCA debe invocarse automáticamente
 * (sin auto-kill, sin suspensión automática por parte del sistema). Recibe la
 * <strong>identidad completa</strong> de la fila confirmada por el usuario; si el proceso ya
 * terminó o su PID se reutilizó, devuelve ese resultado y no reintenta con el PID solo.
 */
public final class TerminateProcessService {

    private static final Logger LOG = System.getLogger(TerminateProcessService.class.getName());

    private final ProcessLifecyclePort processLifecyclePort;
    private final SystemProcessGuard systemProcessGuard;
    private final BlackboxSinkPort blackbox;
    private final Clock clock;

    public TerminateProcessService(ProcessLifecyclePort processLifecyclePort, SystemProcessGuard systemProcessGuard) {
        this(processLifecyclePort, systemProcessGuard, null, Clock.systemUTC());
    }

    /**
     * @param blackbox sink donde registrar solicitud y resultado; {@code null} para no registrar
     */
    public TerminateProcessService(ProcessLifecyclePort processLifecyclePort, SystemProcessGuard systemProcessGuard,
                                   BlackboxSinkPort blackbox, Clock clock) {
        this.processLifecyclePort = Objects.requireNonNull(processLifecyclePort, "processLifecyclePort no puede ser null");
        this.systemProcessGuard = Objects.requireNonNull(systemProcessGuard, "systemProcessGuard no puede ser null");
        this.blackbox = blackbox;
        this.clock = Objects.requireNonNull(clock, "clock no puede ser null");
    }

    /**
     * Ejecuta el cierre manual del proceso exacto que el usuario confirmó.
     *
     * @param identity identidad (PID + fecha de creación) de la fila seleccionada en la UI
     * @return resultado específico de la operación
     */
    public TerminationResult terminate(ProcessIdentity identity) {
        Objects.requireNonNull(identity, "identity no puede ser null");

        if (systemProcessGuard.isProtected(identity)) {
            return TerminationResult.rejectedSystemProcess(identity);
        }

        record(BlackboxEventType.TERMINATION_REQUEST, identity.toString());
        TerminationAttempt attempt = processLifecyclePort.terminate(identity);
        TerminationResult result = TerminationResult.of(identity, map(attempt), attempt.win32Error());
        record(BlackboxEventType.TERMINATION_RESULT, identity + " " + result.status()
                + (result.win32Error() != 0 ? " win32=" + result.win32Error() : ""));
        return result;
    }

    /**
     * Ruta heredada por PID: ya <strong>no</strong> termina procesos. Sin la fecha de creación no
     * se puede detectar la reutilización del PID, así que se devuelve
     * {@link TerminationStatus#IDENTITY_REQUIRED} sin tocar el puerto.
     *
     * @deprecated usar {@link #terminate(ProcessIdentity)} con la identidad de la fila
     */
    @Deprecated
    public TerminationResult terminate(ProcessId pid) {
        Objects.requireNonNull(pid, "pid no puede ser null");
        return TerminationResult.identityRequired(pid);
    }

    private static TerminationStatus map(TerminationAttempt attempt) {
        return switch (attempt.outcome()) {
            case TERMINATED -> TerminationStatus.SUCCESS;
            case PROTECTED_PROCESS -> TerminationStatus.REJECTED_PROTECTED_PROCESS;
            case ACCESS_DENIED -> TerminationStatus.ACCESS_DENIED;
            case PROCESS_EXITED -> TerminationStatus.PROCESS_EXITED;
            case PID_REUSED -> TerminationStatus.PID_REUSED;
            case INVALID_IDENTITY, FAILED -> TerminationStatus.FAILED;
        };
    }

    /** Registro best-effort: un fallo de la caja negra no bloquea la acción del usuario. */
    private void record(BlackboxEventType type, String detail) {
        try {
            Optional.ofNullable(blackbox).ifPresent(sink -> sink.recordEvent(new BlackboxEvent(type, clock.instant(), detail)));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo registrar la terminación en la caja negra", e);
        }
    }
}
