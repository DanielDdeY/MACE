package com.mace.domain.service;

/**
 * Tendencia estimada de una serie de memoria.
 *
 * @param slopeBytesPerSecond pendiente de la recta de ajuste
 * @param rSquared            coeficiente de determinación [0, 1]; 0 si la serie es plana
 * @param sampleCount         puntos usados
 * @param spanNanos           intervalo entre el primer y el último punto
 * @param positiveDeltaRatio  fracción de incrementos consecutivos positivos [0, 1]
 * @param netGrowthBytes      último valor menos el primero
 */
public record TrendEstimate(double slopeBytesPerSecond, double rSquared, int sampleCount,
                            long spanNanos, double positiveDeltaRatio, long netGrowthBytes) {
}
