package com.corepulse.domain.model;

/**
 * Métricas de memoria de un proceso, expresadas en bytes.
 *
 * @param workingSetBytes memoria física residente (Working Set / RSS)
 * @param privateBytes memoria privada exclusiva del proceso (no compartida)
 */
public record MemoryMetrics(long workingSetBytes, long privateBytes) {

    public MemoryMetrics {
        if (workingSetBytes < 0) {
            throw new IllegalArgumentException("workingSetBytes no puede ser negativo: " + workingSetBytes);
        }
        if (privateBytes < 0) {
            throw new IllegalArgumentException("privateBytes no puede ser negativo: " + privateBytes);
        }
    }

    public static MemoryMetrics zero() {
        return new MemoryMetrics(0L, 0L);
    }
}
