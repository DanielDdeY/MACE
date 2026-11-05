package com.mace.domain.service;

import com.mace.domain.DomainFixtures;
import com.mace.domain.model.AlertLevel;
import com.mace.domain.model.GpuAdapter;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.MetricReading;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.TelemetrySnapshot;
import com.mace.domain.model.ThermalAlert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThermalAlertEvaluatorTest {

    private final ThermalAlertEvaluator evaluator = new ThermalAlertEvaluator();
    private final Instant now = Instant.parse("2026-09-08T12:00:00Z");

    @Test
    void noEmiteAlertasCuandoTemperaturasEstanEnRangoSeguro() {
        TelemetrySnapshot snapshot = new TelemetrySnapshot(60f, 65f, 45f, 55f, 120f, 1800, true, now);

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        assertTrue(alerts.isEmpty());
    }

    @Test
    void emiteWarningCuandoCpuSuperaSetentaYCincoGrados() {
        TelemetrySnapshot snapshot = new TelemetrySnapshot(76f, 65f, 70f, 50f, 100f, 1500, true, now);

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        assertEquals(1, alerts.size());
        assertEquals(AlertLevel.WARNING, alerts.get(0).level());
        assertEquals("CPU", alerts.get(0).source());
    }

    @Test
    void emiteCriticalCuandoGpuSuperaOchentaYCincoGrados() {
        TelemetrySnapshot snapshot = new TelemetrySnapshot(70f, 65f, 60f, 86f, 250f, 3000, true, now);

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        assertEquals(1, alerts.size());
        assertEquals(AlertLevel.CRITICAL, alerts.get(0).level());
        assertEquals("GPU", alerts.get(0).source());
    }

    @Test
    void noEvaluaGpuCuandoNoEstaDisponible() {
        TelemetrySnapshot snapshot = new TelemetrySnapshot(70f, 65f, 60f, 999f, 999f, 0, false, now);

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        assertTrue(alerts.isEmpty());
    }

    @Test
    void noEvaluaCpuCuandoSuSensorNoEstaDisponible() {
        // Sin sensor real la temperatura de CPU no es una lectura: no debe disparar alertas.
        TelemetrySnapshot snapshot = new TelemetrySnapshot(99f, 0f, 50f, false, 50f, 100f, 1500, true, now);

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        assertTrue(alerts.isEmpty());
    }

    @Test
    void temperaturaExactamenteEnElUmbralNoEsAccionable() {
        // El umbral es estrictamente mayor a 75.0, no >=.
        TelemetrySnapshot snapshot = new TelemetrySnapshot(75.0f, 65f, 68f, 50f, 100f, 1500, true, now);

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        assertTrue(alerts.isEmpty());
    }

    @Test
    void emiteAmbasAlertasCuandoCpuYGpuSuperanElUmbral() {
        TelemetrySnapshot snapshot = new TelemetrySnapshot(90f, 200f, 95f, 88f, 300f, 3500, true, now);

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        assertEquals(2, alerts.size());
        assertEquals(AlertLevel.CRITICAL, alerts.get(0).level());
        assertEquals(AlertLevel.CRITICAL, alerts.get(1).level());
    }

    // ------------------------------------------------------------------
    // Snapshot V2: estados por métrica y varias GPU
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0} °C → {1}")
    @CsvSource({
            "75.0, NONE",
            "75.1, WARNING",
            "85.0, WARNING",
            "85.1, CRITICAL"
    })
    void losUmbralesSonEstrictamenteMayoresQue75Y85(double temperature, AlertLevel expected) {
        HardwareSnapshot snapshot = DomainFixtures.snapshot(DomainFixtures.cpu(temperature));

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        if (expected == AlertLevel.NONE) {
            assertTrue(alerts.isEmpty());
        } else {
            assertEquals(1, alerts.size());
            assertEquals(expected, alerts.get(0).level());
            assertEquals("CPU", alerts.get(0).source());
            assertTrue(alerts.get(0).gpu().isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(value = MetricState.class, mode = EnumSource.Mode.EXCLUDE, names = "VALID")
    void unaTemperaturaNoValidaNuncaGeneraAlerta(MetricState state) {
        GpuAdapter adapter = DomainFixtures.gpuAdapter(7, 0, "GPU sin sensor");
        HardwareSnapshot snapshot = DomainFixtures.snapshot(
                DomainFixtures.cpu(MetricReading.unavailable(state)),
                DomainFixtures.gpu(adapter, MetricReading.unavailable(state)));

        assertTrue(evaluator.evaluate(snapshot).isEmpty());
    }

    @Test
    void cadaGpuIdentificadaSeEvaluaPorSeparado() {
        GpuAdapter integrada = DomainFixtures.gpuAdapter(0x100, 0, "Intel Iris Xe");
        GpuAdapter dedicada = DomainFixtures.gpuAdapter(0x200, 1, "NVIDIA RTX 3060");
        GpuAdapter fria = DomainFixtures.gpuAdapter(0x300, 2, "Otra GPU");
        HardwareSnapshot snapshot = DomainFixtures.snapshot(DomainFixtures.cpu(50),
                DomainFixtures.gpu(integrada, 78), DomainFixtures.gpu(dedicada, 90), DomainFixtures.gpu(fria, 40));

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        assertEquals(2, alerts.size());
        assertEquals(AlertLevel.WARNING, alerts.get(0).level());
        assertEquals(integrada.id(), alerts.get(0).gpu().orElseThrow());
        assertTrue(alerts.get(0).source().contains("Intel Iris Xe"));
        assertEquals(AlertLevel.CRITICAL, alerts.get(1).level());
        assertEquals(dedicada.id(), alerts.get(1).gpu().orElseThrow());
    }

    @Test
    void unaGpuDetectadaSinSensorNoAlertaAunqueOtraSi() {
        GpuAdapter sinSensor = DomainFixtures.gpuAdapter(1, 0, "Sin proveedor");
        GpuAdapter caliente = DomainFixtures.gpuAdapter(2, 1, "Caliente");
        HardwareSnapshot snapshot = DomainFixtures.snapshot(DomainFixtures.cpu(MetricReading.unavailable(MetricState.WARMING_UP)),
                DomainFixtures.gpu(sinSensor, MetricReading.unavailable(MetricState.NO_PROVIDER)),
                DomainFixtures.gpu(caliente, 86));

        List<ThermalAlert> alerts = evaluator.evaluate(snapshot);

        assertEquals(1, alerts.size());
        assertEquals(caliente.id(), alerts.get(0).gpu().orElseThrow());
    }
}
