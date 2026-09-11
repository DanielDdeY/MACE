package com.corepulse.application.usecase;

import com.corepulse.domain.model.ProcessId;

import java.util.Objects;

/**
 * Resultado inmutable del caso de uso {@link TerminateProcessService}.
 *
 * @param pid proceso objetivo de la solicitud
 * @param status desenlace de la operación
 */
public record TerminationResult(ProcessId pid, TerminationStatus status) {

    public TerminationResult {
        Objects.requireNonNull(pid, "pid no puede ser null");
        Objects.requireNonNull(status, "status no puede ser null");
    }

    public static TerminationResult success(ProcessId pid) {
        return new TerminationResult(pid, TerminationStatus.SUCCESS);
    }

    public static TerminationResult failed(ProcessId pid) {
        return new TerminationResult(pid, TerminationStatus.FAILED);
    }

    public static TerminationResult rejectedSystemProcess(ProcessId pid) {
        return new TerminationResult(pid, TerminationStatus.REJECTED_SYSTEM_PROCESS);
    }

    public boolean isSuccess() {
        return status == TerminationStatus.SUCCESS;
    }
}
