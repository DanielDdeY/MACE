package com.mace.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Ruta completa del ejecutable de un proceso. Puede no estar disponible (acceso denegado,
 * proceso finalizado, PID reutilizado): en ese caso se conserva el motivo y <strong>no</strong>
 * se inventa una ruta.
 *
 * @param value  ruta UTF-16 completa si {@code status == OK}; {@code null} en otro caso
 * @param status motivo de disponibilidad
 */
public record ImagePath(String value, ProcessQueryStatus status) {

    public ImagePath {
        Objects.requireNonNull(status, "status no puede ser null");
        if (status == ProcessQueryStatus.OK) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Una ruta disponible no puede estar vacía");
            }
        } else {
            value = null;
        }
    }

    public static ImagePath of(String path) {
        return new ImagePath(path, ProcessQueryStatus.OK);
    }

    public static ImagePath unavailable(ProcessQueryStatus status) {
        if (Objects.requireNonNull(status, "status no puede ser null") == ProcessQueryStatus.OK) {
            throw new IllegalArgumentException("unavailable() requiere un estado distinto de OK");
        }
        return new ImagePath(null, status);
    }

    public boolean isAvailable() {
        return status == ProcessQueryStatus.OK;
    }

    public Optional<String> asOptional() {
        return Optional.ofNullable(value);
    }
}
