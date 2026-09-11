package com.corepulse.domain.model;

import java.util.Objects;

/**
 * Representa un proceso de espacio de usuario ("userland") detectado en el sistema.
 *
 * @param pid identificador único del proceso
 * @param name nombre corto del ejecutable (p. ej. "chrome.exe")
 * @param fullPath ruta completa al ejecutable en disco
 * @param memory métricas de memoria asociadas al proceso
 */
public record ProcessEntry(ProcessId pid, String name, String fullPath, MemoryMetrics memory) {

    public ProcessEntry {
        Objects.requireNonNull(pid, "pid no puede ser null");
        Objects.requireNonNull(memory, "memory no puede ser null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name no puede estar vacío");
        }
        if (fullPath == null || fullPath.isBlank()) {
            throw new IllegalArgumentException("fullPath no puede estar vacío");
        }
    }
}
