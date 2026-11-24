package com.mace.domain.model;

import java.util.Objects;

/**
 * Proceso de espacio de usuario ("userland") detectado en el inventario V2.
 *
 * <p>La identidad completa ({@link ProcessIdentity}) es la clave de la fila: la UI debe
 * conservarla y entregarla tal cual al pedir una terminación manual.</p>
 *
 * @param identity  PID + fecha de creación
 * @param name      nombre corto del ejecutable (p. ej. "chrome.exe")
 * @param imagePath ruta completa, o el motivo por el que no está disponible
 * @param memory    Working Set y Private Bytes con estado individual
 */
public record ProcessEntry(ProcessIdentity identity, String name, ImagePath imagePath, MemoryMetrics memory) {

    public ProcessEntry {
        Objects.requireNonNull(identity, "identity no puede ser null");
        Objects.requireNonNull(imagePath, "imagePath no puede ser null");
        Objects.requireNonNull(memory, "memory no puede ser null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name no puede estar vacío");
        }
    }

    /** Atajo para el PID de la identidad. */
    public ProcessId pid() {
        return identity.pid();
    }

    /**
     * Compatibilidad con la tabla actual: ruta completa o cadena vacía si no está disponible.
     * Preferir {@link #imagePath()} para mostrar el motivo.
     */
    public String fullPath() {
        return imagePath.asOptional().orElse("");
    }
}
