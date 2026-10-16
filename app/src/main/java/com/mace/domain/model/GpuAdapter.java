package com.mace.domain.model;

import java.util.Objects;

/**
 * Entrada del inventario de GPU detectadas por la capa nativa V2.
 *
 * <p>Representa una GPU presente en el equipo, independientemente de si sus sensores
 * individuales están disponibles. Permite distinguir "GPU detectada" de "GPU con
 * telemetría válida".</p>
 *
 * @param sessionId identificador de sesión de la GPU en el sondeo actual (único dentro
 *                  de una ejecución; puede cambiar entre sondeos si la enumeración varía)
 * @param name nombre comercial del adaptador (p. ej. "NVIDIA GeForce RTX 3060")
 * @param pciBusId identificador de bus PCI de la GPU, o {@code null} si no está disponible
 */
public record GpuAdapter(String sessionId, String name, String pciBusId) {

    public GpuAdapter {
        Objects.requireNonNull(sessionId, "sessionId no puede ser null");
        Objects.requireNonNull(name, "name no puede ser null");
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId no puede estar vacío");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name no puede estar vacío");
        }
    }
}
