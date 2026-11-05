package com.mace.domain.model;

import java.util.Objects;

/**
 * Respuesta del puerto de procesos a una terminación por identidad.
 *
 * @param identity   identidad exacta enviada
 * @param outcome    desenlace
 * @param win32Error código Win32 de diagnóstico (0 si no aplica); no habilita reintentos
 */
public record TerminationAttempt(ProcessIdentity identity, TerminationOutcome outcome, int win32Error) {

    public TerminationAttempt {
        Objects.requireNonNull(identity, "identity no puede ser null");
        Objects.requireNonNull(outcome, "outcome no puede ser null");
    }
}
