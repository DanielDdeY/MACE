package com.mace.domain.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Regresión lineal por mínimos cuadrados sobre (segundos, bytes). Calcula también R² y la
 * fracción de incrementos positivos, que el detector usa para no confundir una subida aislada
 * (escalón o pico) con un crecimiento sostenido.
 */
public final class LinearRegressionLeakStrategy implements LeakCalculationStrategy {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    @Override
    public Optional<TrendEstimate> estimate(List<SeriesPoint> points) {
        Objects.requireNonNull(points, "points no puede ser null");
        int n = points.size();
        if (n < 2) {
            return Optional.empty();
        }
        long origin = points.get(0).monotonicNanos();
        double meanX = 0;
        double meanY = 0;
        for (SeriesPoint point : points) {
            meanX += (point.monotonicNanos() - origin) / NANOS_PER_SECOND;
            meanY += point.bytes();
        }
        meanX /= n;
        meanY /= n;

        double sxx = 0;
        double sxy = 0;
        double syy = 0;
        int positiveDeltas = 0;
        for (int i = 0; i < n; i++) {
            SeriesPoint point = points.get(i);
            double dx = (point.monotonicNanos() - origin) / NANOS_PER_SECOND - meanX;
            double dy = point.bytes() - meanY;
            sxx += dx * dx;
            sxy += dx * dy;
            syy += dy * dy;
            if (i > 0 && point.bytes() > points.get(i - 1).bytes()) {
                positiveDeltas++;
            }
        }
        if (sxx == 0) {
            return Optional.empty();
        }
        double slope = sxy / sxx;
        double rSquared = syy == 0 ? 0.0 : Math.min(1.0, (sxy * sxy) / (sxx * syy));
        long span = points.get(n - 1).monotonicNanos() - origin;
        long netGrowth = points.get(n - 1).bytes() - points.get(0).bytes();
        double positiveRatio = positiveDeltas / (double) (n - 1);
        return Optional.of(new TrendEstimate(slope, rSquared, n, span, positiveRatio, netGrowth));
    }
}
