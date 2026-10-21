package com.mace.domain.service;

import com.mace.domain.model.LeakAlert;
import com.mace.domain.model.LongMetricReading;
import com.mace.domain.model.MemorySeries;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessSample;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static com.mace.domain.DomainFixtures.MIB;
import static com.mace.domain.DomainFixtures.NOW;
import static com.mace.domain.DomainFixtures.SECOND;
import static com.mace.domain.DomainFixtures.entry;
import static com.mace.domain.DomainFixtures.identity;
import static com.mace.domain.DomainFixtures.inventory;
import static com.mace.domain.DomainFixtures.sample;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Datos simulados a 1 Hz con los que se ajustaron los valores por defecto de
 * {@link LeakDetectionPolicy#defaults()}.
 */
class MemoryLeakDetectorTest {

    private static final ProcessIdentity APP = identity(5150, 1000);
    private final MemoryLeakDetector detector = new MemoryLeakDetector();

    @Test
    void unCrecimientoSostenidoGeneraAlertaParaAmbasSeries() {
        Random noise = new Random(7);
        for (int i = 0; i < 20; i++) {
            long jitter = (long) (noise.nextGaussian() * 64 * 1024);
            detector.observe(sample(APP, i * SECOND, 200 * MIB + i * 3 * MIB / 2 + jitter,
                    150 * MIB + i * 3 * MIB / 2 + jitter), "leaky.exe");
        }

        List<LeakAlert> alerts = detector.evaluateAll(NOW);

        assertEquals(2, alerts.size());
        assertTrue(alerts.stream().allMatch(alert -> alert.identity().equals(APP)));
        assertEquals(1.5 * MIB, alerts.get(0).slopeBytesPerSecond(), 0.1 * MIB);
        assertTrue(alerts.get(0).rSquared() > 0.99);
        assertEquals("leaky.exe", alerts.get(0).processName());
    }

    @Test
    void noEvaluaAntesDelMinimoDeMuestras() {
        int minimum = detector.policy().minSamples();
        for (int i = 0; i < minimum - 1; i++) {
            detector.observe(sample(APP, i * SECOND, 100 * MIB + i * 5 * MIB, 100 * MIB + i * 5 * MIB), "a.exe");
        }
        assertTrue(detector.evaluateAll(NOW).isEmpty());

        detector.observe(sample(APP, minimum * SECOND, 100 * MIB + minimum * 5 * MIB, 100 * MIB + minimum * 5 * MIB), "a.exe");
        assertFalse(detector.evaluateAll(NOW).isEmpty());
    }

    @Test
    void unaSubidaAisladaNoEsUnaFuga() {
        for (int i = 0; i < 30; i++) {
            long spike = i == 15 ? 400 * MIB : 0;
            detector.observe(sample(APP, i * SECOND, 200 * MIB + spike, 200 * MIB + spike), "spike.exe");
        }
        assertTrue(detector.evaluateAll(NOW).isEmpty());
    }

    @Test
    void unEscalonUnicoNoEsUnaFuga() {
        // Carga de un archivo grande: un salto y después estable.
        for (int i = 0; i < 30; i++) {
            long level = i < 15 ? 200 * MIB : 600 * MIB;
            detector.observe(sample(APP, i * SECOND, level, level), "step.exe");
        }
        assertTrue(detector.evaluateAll(NOW).isEmpty(), "pocos incrementos positivos: no es crecimiento sostenido");
    }

    @Test
    void unaSerieEstableConRuidoNoAlerta() {
        Random noise = new Random(11);
        for (int i = 0; i < 30; i++) {
            long value = 300 * MIB + (long) (noise.nextGaussian() * 8 * MIB);
            detector.observe(sample(APP, i * SECOND, value, value), "stable.exe");
        }
        assertTrue(detector.evaluateAll(NOW).isEmpty());
    }

    @Test
    void soloLaSerieQueCreceGeneraAlerta() {
        for (int i = 0; i < 20; i++) {
            detector.observe(sample(APP, i * SECOND, 200 * MIB, 100 * MIB + i * 2 * MIB), "private.exe");
        }

        List<LeakAlert> alerts = detector.evaluateAll(NOW);

        assertEquals(1, alerts.size());
        assertEquals(MemorySeries.PRIVATE_BYTES, alerts.get(0).series());
    }

    @Test
    void descartaMuestrasNoValidasDeCadaSerie() {
        for (int i = 0; i < 20; i++) {
            LongMetricReading workingSet = i % 2 == 0
                    ? LongMetricReading.unavailable(MetricState.ACCESS_DENIED)
                    : LongMetricReading.valid(100 * MIB + i * 2 * MIB);
            detector.observe(new ProcessSample(APP, i * SECOND, workingSet, LongMetricReading.valid(80 * MIB)), "x.exe");
        }

        List<LeakAlert> alerts = detector.evaluateAll(NOW);

        assertEquals(1, alerts.size());
        assertEquals(MemorySeries.WORKING_SET, alerts.get(0).series());
        assertEquals(10, alerts.get(0).sampleCount(), "solo las 10 lecturas válidas");
        assertFalse(detector.observe(new ProcessSample(APP, 99 * SECOND,
                LongMetricReading.unavailable(MetricState.READ_FAILED),
                LongMetricReading.unavailable(MetricState.READ_FAILED)), "x.exe"),
                "una muestra sin ninguna serie válida se descarta");
    }

    @Test
    void retieneComoMaximoTreintaMuestrasPorIdentidad() {
        for (int i = 0; i < 50; i++) {
            detector.observe(sample(APP, i * SECOND, MIB, MIB), "x.exe");
        }
        List<ProcessSample> samples = detector.samplesOf(APP);
        assertEquals(30, samples.size());
        assertEquals(20 * SECOND, samples.get(0).monotonicNanos(), "se descartan las más antiguas");
    }

    @Test
    void unPidReutilizadoEmpiezaUnaSerieNueva() {
        ProcessIdentity original = identity(6400, 1000);
        ProcessIdentity reutilizado = identity(6400, 2000);
        for (int i = 0; i < 15; i++) {
            detector.observe(sample(original, i * SECOND, 100 * MIB + i * 2 * MIB, 100 * MIB + i * 2 * MIB), "old.exe");
        }
        // El proceso nuevo usa mucha menos memoria: unirlo a la serie anterior ocultaría o
        // inventaría una tendencia.
        for (int i = 15; i < 20; i++) {
            detector.observe(sample(reutilizado, i * SECOND, 20 * MIB, 20 * MIB), "new.exe");
        }

        assertEquals(15, detector.samplesOf(original).size());
        assertEquals(5, detector.samplesOf(reutilizado).size());
        assertTrue(detector.evaluate(reutilizado, NOW).isEmpty());
        assertEquals(2, detector.evaluate(original, NOW).size());
    }

    @Test
    void elInventarioOlvidaProcesosQueDesaparecen() {
        ProcessIdentity otro = identity(9000, 5);
        detector.observe(inventory(0, entry(APP, "a.exe", MIB, MIB), entry(otro, "b.exe", MIB, MIB)));
        assertEquals(2, detector.trackedCount());

        detector.observe(inventory(SECOND, entry(APP, "a.exe", MIB, MIB)));

        assertEquals(1, detector.trackedCount());
        assertTrue(detector.samplesOf(otro).isEmpty());
    }

    @Test
    void ignoraMuestrasDemasiadoSeguidasODesordenadas() {
        assertTrue(detector.observe(sample(APP, 10 * SECOND, MIB, MIB), "a.exe"));
        assertFalse(detector.observe(sample(APP, 10 * SECOND + SECOND / 10, MIB, MIB), "a.exe"));
        assertFalse(detector.observe(sample(APP, 5 * SECOND, MIB, MIB), "a.exe"));
        assertEquals(1, detector.samplesOf(APP).size());
    }
}
