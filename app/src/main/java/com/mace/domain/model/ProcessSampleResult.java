package com.mace.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Resultado de muestrear un proceso por identidad.
 *
 * @param identity   identidad solicitada
 * @param status     {@link ProcessQueryStatus#OK} si hay muestra
 * @param sample     muestra si {@code status == OK}; {@code null} en otro caso
 * @param win32Error código Win32 de diagnóstico (0 si no aplica); no se usa para decidir
 */
public record ProcessSampleResult(ProcessIdentity identity, ProcessQueryStatus status,
                                  ProcessSample sample, int win32Error) {

    public ProcessSampleResult {
        Objects.requireNonNull(identity, "identity no puede ser null");
        Objects.requireNonNull(status, "status no puede ser null");
        if ((status == ProcessQueryStatus.OK) != (sample != null)) {
            throw new IllegalArgumentException("Debe haber muestra si y solo si el estado es OK");
        }
    }

    public static ProcessSampleResult of(ProcessSample sample) {
        return new ProcessSampleResult(sample.identity(), ProcessQueryStatus.OK, sample, 0);
    }

    public static ProcessSampleResult failed(ProcessIdentity identity, ProcessQueryStatus status, int win32Error) {
        return new ProcessSampleResult(identity, status, null, win32Error);
    }

    public Optional<ProcessSample> asOptional() {
        return Optional.ofNullable(sample);
    }
}
