package com.corepulse.domain.model;

/**
 * Identidad única e inmutable de un proceso del sistema operativo.
 *
 * @param value identificador de proceso (PID) tal como lo reporta el SO
 */
public record ProcessId(long value) {

    public ProcessId {
        if (value < 0) {
            throw new IllegalArgumentException("El PID no puede ser negativo: " + value);
        }
    }

    @Override
    public String toString() {
        return "PID(" + value + ")";
    }
}
