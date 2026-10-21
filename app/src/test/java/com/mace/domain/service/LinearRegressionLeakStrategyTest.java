package com.mace.domain.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.mace.domain.DomainFixtures.MIB;
import static com.mace.domain.DomainFixtures.SECOND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinearRegressionLeakStrategyTest {

    private final LinearRegressionLeakStrategy strategy = new LinearRegressionLeakStrategy();

    @Test
    void unaSerieLinealDaPendienteExactaYAjustePerfecto() {
        List<SeriesPoint> points = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            points.add(new SeriesPoint(i * SECOND, 100 * MIB + i * MIB));
        }

        TrendEstimate estimate = strategy.estimate(points).orElseThrow();

        assertEquals(MIB, estimate.slopeBytesPerSecond(), 1e-3);
        assertEquals(1.0, estimate.rSquared(), 1e-12);
        assertEquals(1.0, estimate.positiveDeltaRatio());
        assertEquals(19 * MIB, estimate.netGrowthBytes());
        assertEquals(19 * SECOND, estimate.spanNanos());
    }

    @Test
    void usaLosTiemposRealesAunqueFaltenMuestras() {
        // Huecos en el muestreo: la pendiente sigue siendo 2 MiB/s.
        long[] seconds = {0, 1, 2, 5, 6, 10, 11, 15};
        List<SeriesPoint> points = new ArrayList<>();
        for (long s : seconds) {
            points.add(new SeriesPoint(s * SECOND, s * 2 * MIB));
        }

        assertEquals(2.0 * MIB, strategy.estimate(points).orElseThrow().slopeBytesPerSecond(), 1e-3);
    }

    @Test
    void unaSeriePlanaNoTieneTendencia() {
        List<SeriesPoint> points = List.of(new SeriesPoint(0, 5), new SeriesPoint(SECOND, 5), new SeriesPoint(2 * SECOND, 5));

        TrendEstimate estimate = strategy.estimate(points).orElseThrow();

        assertEquals(0.0, estimate.slopeBytesPerSecond());
        assertEquals(0.0, estimate.rSquared());
    }

    @Test
    void sinDatosSuficientesNoEstima() {
        assertTrue(strategy.estimate(List.of()).isEmpty());
        assertTrue(strategy.estimate(List.of(new SeriesPoint(0, 1))).isEmpty());
        assertTrue(strategy.estimate(List.of(new SeriesPoint(5, 1), new SeriesPoint(5, 9))).isEmpty(),
                "sin variación temporal no hay pendiente");
    }
}
