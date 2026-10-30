package com.mace.domain.service;

import java.util.List;
import java.util.Optional;

/**
 * Estrategia para estimar la tendencia de una serie de memoria (patrón Strategy): permite
 * sustituir la regresión lineal por otro estimador sin tocar el detector.
 */
public interface LeakCalculationStrategy {

    /**
     * @param points puntos válidos en orden temporal estrictamente creciente
     * @return la tendencia, o vacío si no hay datos suficientes para estimarla
     */
    Optional<TrendEstimate> estimate(List<SeriesPoint> points);
}
