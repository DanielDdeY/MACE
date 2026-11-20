package com.mace.domain.service;

import java.time.Duration;
import java.util.Objects;

/**
 * Parámetros del detector de fugas. Una alerta exige <strong>todas</strong> las condiciones:
 * suficientes muestras válidas, pendiente y crecimiento neto mínimos, ajuste lineal fuerte y
 * mayoría de incrementos positivos. Así una subida aislada no se toma como fuga.
 *
 * @param maxSamples              muestras retenidas por identidad (30 según el acuerdo)
 * @param minSamples              muestras válidas mínimas para evaluar
 * @param minSampleInterval       intervalo mínimo entre muestras aceptadas
 * @param minSlopeBytesPerSecond  pendiente mínima
 * @param minRSquared             R² mínimo del ajuste
 * @param minPositiveDeltaRatio   fracción mínima de incrementos positivos
 * @param minNetGrowthBytes       crecimiento neto mínimo dentro de la ventana
 */
public record LeakDetectionPolicy(int maxSamples, int minSamples, Duration minSampleInterval,
                                  double minSlopeBytesPerSecond, double minRSquared,
                                  double minPositiveDeltaRatio, long minNetGrowthBytes) {

    private static final long KIB = 1024L;
    private static final long MIB = 1024L * KIB;

    public LeakDetectionPolicy {
        Objects.requireNonNull(minSampleInterval, "minSampleInterval no puede ser null");
        if (maxSamples < 2 || minSamples < 2 || minSamples > maxSamples) {
            throw new IllegalArgumentException("Se requiere 2 <= minSamples <= maxSamples");
        }
        if (minSampleInterval.isNegative()) {
            throw new IllegalArgumentException("minSampleInterval no puede ser negativo");
        }
        if (minRSquared < 0 || minRSquared > 1 || minPositiveDeltaRatio < 0 || minPositiveDeltaRatio > 1) {
            throw new IllegalArgumentException("minRSquared y minPositiveDeltaRatio deben estar en [0, 1]");
        }
    }

    /**
     * Valores por defecto, ajustados con las series simuladas de las pruebas a 1 Hz:
     * 30 muestras, mínimo 10, pendiente &ge; 100 KiB/s, R² &ge; 0,8, &ge; 60 % de incrementos
     * positivos y &ge; 4 MiB de crecimiento neto.
     */
    public static LeakDetectionPolicy defaults() {
        return new LeakDetectionPolicy(30, 10, Duration.ofMillis(900),
                100.0 * KIB, 0.80, 0.60, 4L * MIB);
    }

    public boolean indicatesLeak(TrendEstimate estimate) {
        return estimate.sampleCount() >= minSamples
                && estimate.slopeBytesPerSecond() >= minSlopeBytesPerSecond
                && estimate.rSquared() >= minRSquared
                && estimate.positiveDeltaRatio() >= minPositiveDeltaRatio
                && estimate.netGrowthBytes() >= minNetGrowthBytes;
    }
}
