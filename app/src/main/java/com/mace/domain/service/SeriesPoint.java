package com.mace.domain.service;

/**
 * Punto válido de una serie de memoria.
 *
 * @param monotonicNanos marca monotónica de la muestra
 * @param bytes          valor de la serie en bytes
 */
public record SeriesPoint(long monotonicNanos, long bytes) {
}
