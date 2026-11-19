package com.mace.infrastructure.mock;

import com.mace.domain.model.AlertLevel;
import com.mace.domain.model.CpuVendor;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.PowerScope;
import com.mace.domain.model.SensorProvider;
import com.mace.domain.model.ThermalAlert;
import com.mace.domain.service.ThermalAlertEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockSensorsAdapterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void limpiar() {
        System.clearProperty(MockScenario.PROPERTY);
        System.clearProperty(MockScenario.SEED_PROPERTY);
    }

    private static MockSensorsAdapter adapter(MockScenario scenario) {
        return new MockSensorsAdapter(scenario, 7L, CLOCK, () -> 1L);
    }

    @ParameterizedTest
    @EnumSource(MockScenario.class)
    void conLaMismaSemillaLaSecuenciaEsIdentica(MockScenario scenario) {
        MockSensorsAdapter a = adapter(scenario);
        MockSensorsAdapter b = adapter(scenario);
        for (int i = 0; i < 10; i++) {
            assertEquals(a.fetchHardwareSnapshot(), b.fetchHardwareSnapshot());
        }
    }

    @Test
    void normalTieneTodasLasMetricasValidas() {
        HardwareSnapshot snapshot = adapter(MockScenario.NORMAL).fetchHardwareSnapshot();

        assertEquals(CpuVendor.INTEL, snapshot.cpu().vendor());
        assertEquals(PowerScope.CPU_PACKAGE, snapshot.cpu().powerScope());
        assertTrue(snapshot.cpu().temperatureCelsius().isValid());
        assertEquals(1, snapshot.gpus().size());
        assertEquals(PowerScope.GPU_BOARD, snapshot.gpus().get(0).powerScope());
        assertTrue(snapshot.gpus().get(0).fanRpm().isValid());
    }

    @Test
    void cpuSinTemperaturaConUsoValido() {
        HardwareSnapshot snapshot = adapter(MockScenario.CPU_NO_TEMPERATURE).fetchHardwareSnapshot();

        assertEquals(MetricState.NO_PROVIDER, snapshot.cpu().temperatureCelsius().state());
        assertEquals(MetricState.NO_PROVIDER, snapshot.cpu().powerWatts().state());
        assertTrue(snapshot.cpu().usagePercent().isValid());
    }

    @Test
    void amdSinPotenciaYRadeonPorAdlx() {
        HardwareSnapshot snapshot = adapter(MockScenario.AMD_NO_POWER).fetchHardwareSnapshot();

        assertEquals(CpuVendor.AMD, snapshot.cpu().vendor());
        assertEquals(SensorProvider.AMD_RYZEN_SDK, snapshot.cpu().provider());
        assertTrue(snapshot.cpu().temperatureCelsius().isValid());
        assertEquals(MetricState.NO_PROVIDER, snapshot.cpu().powerWatts().state());
        assertEquals(SensorProvider.AMD_ADLX, snapshot.gpus().get(0).provider());
    }

    @Test
    void accesoDenegadoSeDistingueDeSinProveedor() {
        HardwareSnapshot snapshot = adapter(MockScenario.ACCESS_DENIED).fetchHardwareSnapshot();

        assertEquals(MetricState.ACCESS_DENIED, snapshot.cpu().temperatureCelsius().state());
    }

    @Test
    void igpuConTemperaturaYSinVentilador() {
        GpuReading igpu = adapter(MockScenario.IGPU_NO_FAN).fetchHardwareSnapshot().gpus().get(0);

        assertTrue(igpu.temperatureCelsius().isValid());
        assertEquals(MetricState.UNSUPPORTED, igpu.fanRpm().state());
        assertEquals(MetricState.UNSUPPORTED, igpu.fanPercent().state());
    }

    @Test
    void dosGpuConLuidYDatosDistintos() {
        List<GpuReading> gpus = adapter(MockScenario.DUAL_GPU).fetchHardwareSnapshot().gpus();

        assertEquals(2, gpus.size());
        assertNotEquals(gpus.get(0).adapterId(), gpus.get(1).adapterId());
        assertEquals(0, gpus.get(0).adapter().adapterIndex());
        assertEquals(1, gpus.get(1).adapter().adapterIndex());
        assertFalse(gpus.get(0).fanRpm().isValid());
        assertTrue(gpus.get(1).fanRpm().isValid());
    }

    @Test
    void gpuInactivaEnPortatilHibrido() {
        List<GpuReading> gpus = adapter(MockScenario.GPU_INACTIVE).fetchHardwareSnapshot().gpus();

        assertFalse(gpus.get(0).isInactive());
        assertTrue(gpus.get(1).isInactive());
        assertEquals(MetricState.DEVICE_INACTIVE, gpus.get(1).temperatureCelsius().state());
    }

    @Test
    void gpuDetectadaSinSensor() {
        GpuReading gpu = adapter(MockScenario.GPU_NO_SENSOR).fetchHardwareSnapshot().gpus().get(0);

        assertFalse(gpu.hasAnyValidMetric());
        assertFalse(gpu.isInactive());
        assertEquals(MetricState.NO_PROVIDER, gpu.temperatureCelsius().state());
    }

    @Test
    void calentamientoSoloEnLasPrimerasMuestras() {
        MockSensorsAdapter adapter = adapter(MockScenario.WARMING_UP);
        for (int i = 1; i <= MockScenario.WARM_UP_POLLS; i++) {
            assertEquals(MetricState.WARMING_UP, adapter.fetchHardwareSnapshot().cpu().usagePercent().state());
        }
        assertTrue(adapter.fetchHardwareSnapshot().cpu().usagePercent().isValid());
    }

    @Test
    void perfilesDeAlertaSuperanLosUmbralesDelDominio() {
        ThermalAlertEvaluator evaluator = new ThermalAlertEvaluator();
        MockSensorsAdapter warning = adapter(MockScenario.THERMAL_WARNING);
        MockSensorsAdapter critical = adapter(MockScenario.THERMAL_CRITICAL);
        for (int i = 0; i < 20; i++) {
            warning.fetchHardwareSnapshot();
            critical.fetchHardwareSnapshot();
        }

        List<ThermalAlert> warningAlerts = evaluator.evaluate(warning.fetchHardwareSnapshot());
        List<ThermalAlert> criticalAlerts = evaluator.evaluate(critical.fetchHardwareSnapshot());

        assertFalse(warningAlerts.isEmpty());
        assertTrue(warningAlerts.stream().allMatch(alert -> alert.level() == AlertLevel.WARNING));
        assertTrue(criticalAlerts.stream().anyMatch(alert -> alert.level() == AlertLevel.CRITICAL));
    }

    @Test
    void sinGpuFisica() {
        assertFalse(adapter(MockScenario.NO_GPU).fetchHardwareSnapshot().hasGpus());
    }

    @Test
    void elEscenarioYLaSemillaSeLeenDePropiedades() {
        System.setProperty(MockScenario.PROPERTY, "dual_gpu");
        System.setProperty(MockScenario.SEED_PROPERTY, "123");

        assertEquals(MockScenario.DUAL_GPU, MockScenario.fromSystemProperty());
        assertEquals(123L, MockScenario.seedFromSystemProperty());

        System.setProperty(MockScenario.PROPERTY, "no-existe");
        System.setProperty(MockScenario.SEED_PROPERTY, "x");
        assertEquals(MockScenario.NORMAL, MockScenario.fromSystemProperty());
        assertEquals(MockScenario.DEFAULT_SEED, MockScenario.seedFromSystemProperty());
    }
}
