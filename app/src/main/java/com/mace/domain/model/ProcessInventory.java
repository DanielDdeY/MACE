package com.mace.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultado de una enumeración de procesos.
 *
 * @param processes      procesos visibles (la capa nativa ya excluye los críticos del sistema)
 * @param skippedCount   procesos que no pudieron consultarse por permisos o error (no incluye
 *                       las exclusiones deliberadas por política de seguridad)
 * @param timestamp      instante de pared de la enumeración
 * @param monotonicNanos marca monotónica Java ({@link System#nanoTime()}) de la enumeración,
 *                       usada para construir series temporales por proceso
 */
public record ProcessInventory(List<ProcessEntry> processes, int skippedCount, Instant timestamp, long monotonicNanos) {

    public ProcessInventory {
        processes = List.copyOf(Objects.requireNonNull(processes, "processes no puede ser null"));
        Objects.requireNonNull(timestamp, "timestamp no puede ser null");
        if (skippedCount < 0) {
            throw new IllegalArgumentException("skippedCount no puede ser negativo: " + skippedCount);
        }
    }

    public Optional<ProcessEntry> find(ProcessIdentity identity) {
        return processes.stream().filter(entry -> entry.identity().equals(identity)).findFirst();
    }
}
