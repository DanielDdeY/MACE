package com.corepulse.domain.service;

import com.corepulse.domain.model.AlertLevel;
import com.corepulse.domain.model.TelemetrySnapshot;
import com.corepulse.domain.model.ThermalAlert;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThermalAlertEvaluatorTest {

    private final ThermalAlertEvaluator evaluator = new ThermalAlertEvaluator();
    private final Instant now = Instant.parse("2026-09-08T12:00:00Z");

    @Test
    void noEmiteAlertasCuandoTemperaturasEstanEnRangoSeguro() {
        TelemetrySnapshot snapshot = new TelemetrySnapshot(60f, 65f, 55f, 120f, 1800, true, now);

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        assertTrue(alerts.isEmpty());
    }

    @Test
    void emiteWarningCuandoCpuSuperaSetentaYCincoGrados() {
        TelemetrySnapshot snapshot = new TelemetrySnapshot(76f, 65f, 50f, 100f, 1500, true, now);

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        assertEquals(1, alerts.size());
        assertEquals(AlertLevel.WARNING, alerts.get(0).level());
        assertEquals("CPU", alerts.get(0).source());
    }

    @Test
    void emiteCriticalCuandoGpuSuperaOchentaYCincoGrados() {
        TelemetrySnapshot snapshot = new TelemetrySnapshot(70f, 65f, 86f, 250f, 3000, true, now);

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        assertEquals(1, alerts.size());
        assertEquals(AlertLevel.CRITICAL, alerts.get(0).level());
        assertEquals("GPU", alerts.get(0).source());
    }

    @Test
    void noEvaluaGpuCuandoNoEstaDisponible() {
        TelemetrySnapshot snapshot = new TelemetrySnapshot(70f, 65f, 999f, 999f, 0, false, now);

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        assertTrue(alerts.isEmpty());
    }

    @Test
    void temperaturaExactamenteEnElUmbralNoEsAccionable() {
        // El umbral es estrictamente mayor a 75.0, no >=.
        TelemetrySnapshot snapshot = new TelemetrySnapshot(75.0f, 65f, 50f, 100f, 1500, true, now);

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        assertTrue(alerts.isEmpty());
    }

    @Test
    void emiteAmbasAlertasCuandoCpuYGpuSuperanElUmbral() {
        TelemetrySnapshot snapshot = new TelemetrySnapshot(90f, 200f, 88f, 300f, 3500, true, now);

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        assertEquals(2, alerts.size());
        assertEquals(AlertLevel.CRITICAL, alerts.get(0).level());
        assertEquals(AlertLevel.CRITICAL, alerts.get(1).level());
    }
}
