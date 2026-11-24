package com.mace.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricReadingTest {

    @ParameterizedTest
    @EnumSource(value = MetricState.class, mode = EnumSource.Mode.EXCLUDE, names = "VALID")
    void unCeroConEstadoNoValidoNoEsUnaLectura(MetricState state) {
        MetricReading reading = new MetricReading(0.0, state);

        assertFalse(reading.isValid());
        assertTrue(Double.isNaN(reading.value()), "el valor se normaliza a NaN");
        assertTrue(reading.asOptional().isEmpty());
        assertEquals(-1.0, reading.orElse(-1.0));
        assertFalse(reading.value() > 75.0, "NaN nunca supera un umbral");
    }

    @Test
    void unaLecturaValidaConservaSuValorIncluidoElCero() {
        MetricReading cero = MetricReading.valid(0.0);

        assertTrue(cero.isValid());
        assertEquals(0.0, cero.value());
        assertEquals(0.0, cero.asOptional().orElseThrow());
    }

    @Test
    void unaLecturaValidaNoPuedeSerNoFinita() {
        assertThrows(IllegalArgumentException.class, () -> MetricReading.valid(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> MetricReading.valid(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> MetricReading.unavailable(MetricState.VALID));
    }

    @Test
    void lasLecturasEnterasNormalizanACeroConSuEstado() {
        LongMetricReading missing = new LongMetricReading(1234, MetricState.NO_PROVIDER);

        assertEquals(0L, missing.value());
        assertEquals(MetricState.NO_PROVIDER, missing.state());
        assertTrue(missing.asOptional().isEmpty());
        assertEquals(7L, missing.orElse(7L));
        assertThrows(IllegalArgumentException.class, () -> LongMetricReading.valid(-1));
    }

    @Test
    void laMemoriaDelSistemaSoloCalculaUsoConAmbasLecturasValidas() {
        long gib = 1024L * 1024L * 1024L;
        SystemMemoryReading ok = new SystemMemoryReading(LongMetricReading.valid(16 * gib),
                LongMetricReading.valid(4 * gib), 1L);
        SystemMemoryReading sinDisponible = new SystemMemoryReading(LongMetricReading.valid(16 * gib),
                LongMetricReading.unavailable(MetricState.READ_FAILED), 1L);

        assertEquals(12 * gib, ok.usedBytes().value());
        assertEquals(75.0, ok.usedPercent().value(), 1e-9);
        assertEquals(MetricState.READ_FAILED, sinDisponible.usedBytes().state());
        assertFalse(sinDisponible.usedPercent().isValid());
    }

    @Test
    void privateBytesPuedeSuperarAlWorkingSet() {
        MemoryMetrics memory = new MemoryMetrics(100L, 500L);

        assertEquals(100L, memory.workingSetBytes());
        assertEquals(500L, memory.privateBytes());
    }
}
