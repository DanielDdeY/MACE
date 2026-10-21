package com.mace.domain.model;

import java.util.Objects;

/**
 * Resultado de consultar si un proceso responde ({@code MaceResponsivenessV2}).
 *
 * @param identity        proceso consultado
 * @param outcome         resultado explícito
 * @param windowsExamined ventanas superiores visibles examinadas
 * @param windowsHung     ventanas que aparecen colgadas
 * @param win32Error      código Win32 de diagnóstico (0 si no aplica)
 */
public record ResponsivenessResult(ProcessIdentity identity, ResponsivenessOutcome outcome,
                                   int windowsExamined, int windowsHung, int win32Error) {

    public ResponsivenessResult {
        Objects.requireNonNull(identity, "identity no puede ser null");
        Objects.requireNonNull(outcome, "outcome no puede ser null");
        if (windowsExamined < 0 || windowsHung < 0 || windowsHung > windowsExamined) {
            throw new IllegalArgumentException("Conteo de ventanas inválido: " + windowsHung + "/" + windowsExamined);
        }
    }

    public static ResponsivenessResult of(ProcessIdentity identity, ResponsivenessOutcome outcome) {
        return new ResponsivenessResult(identity, outcome, 0, 0, 0);
    }

    public boolean isHung() {
        return outcome == ResponsivenessOutcome.HUNG;
    }
}
