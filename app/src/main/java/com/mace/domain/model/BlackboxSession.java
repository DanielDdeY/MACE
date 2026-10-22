package com.mace.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Sesión de la caja negra leída completa.
 *
 * @param summary resumen del archivo
 * @param entries registros recuperables, del más antiguo al más reciente
 */
public record BlackboxSession(BlackboxSessionSummary summary, List<BlackboxEntry> entries) {

    public BlackboxSession {
        Objects.requireNonNull(summary, "summary no puede ser null");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries no puede ser null"));
    }

    public BlackboxSession withSummary(BlackboxSessionSummary newSummary) {
        return new BlackboxSession(newSummary, entries);
    }
}
