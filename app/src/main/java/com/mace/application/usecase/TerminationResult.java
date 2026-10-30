package com.mace.application.usecase;

import com.mace.domain.model.ProcessId;
import com.mace.domain.model.ProcessIdentity;

import java.util.Objects;
import java.util.Optional;

/**
 * Resultado inmutable del caso de uso {@link TerminateProcessService}.
 *
 * @param pid        PID objetivo de la solicitud
 * @param identity   identidad completa usada; {@code null} solo en {@link TerminationStatus#IDENTITY_REQUIRED}
 * @param status     desenlace de la operación
 * @param win32Error código Win32 de diagnóstico (0 si no aplica); no se usa para reintentar
 */
public record TerminationResult(ProcessId pid, ProcessIdentity identity, TerminationStatus status, int win32Error) {

    public TerminationResult {
        Objects.requireNonNull(pid, "pid no puede ser null");
        Objects.requireNonNull(status, "status no puede ser null");
        if (identity == null && status != TerminationStatus.IDENTITY_REQUIRED) {
            throw new IllegalArgumentException("Solo IDENTITY_REQUIRED puede carecer de identidad");
        }
        if (identity != null && !identity.pid().equals(pid)) {
            throw new IllegalArgumentException("pid e identity no coinciden");
        }
    }

    public static TerminationResult of(ProcessIdentity identity, TerminationStatus status, int win32Error) {
        return new TerminationResult(identity.pid(), identity, status, win32Error);
    }

    public static TerminationResult success(ProcessIdentity identity) {
        return of(identity, TerminationStatus.SUCCESS, 0);
    }

    public static TerminationResult rejectedSystemProcess(ProcessIdentity identity) {
        return of(identity, TerminationStatus.REJECTED_SYSTEM_PROCESS, 0);
    }

    public static TerminationResult identityRequired(ProcessId pid) {
        return new TerminationResult(pid, null, TerminationStatus.IDENTITY_REQUIRED, 0);
    }

    public Optional<ProcessIdentity> identityOptional() {
        return Optional.ofNullable(identity);
    }

    public boolean isSuccess() {
        return status == TerminationStatus.SUCCESS;
    }
}
