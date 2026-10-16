package com.mace.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricReadingTest {

    @Test
    void lecturaValidaTieneValorYEstadoValido() {
        MetricReading<Float> reading = new MetricReading<>(75.5f, MetricState.VALID, "°C");

        assertEquals(75.5f, reading.value());
        assertEquals(MetricState.VALID, reading.state());
        assertEquals("°C", reading.unit());
        assertTrue(reading.isValid());
    }

    @Test
    void lecturaSinProveedorTieneValorNuloYEstadoNoValido() {
        MetricReading<Float> reading = new MetricReading<>(null, MetricState.NO_PROVIDER, "°C");

        assertNull(reading.value());
        assertEquals(MetricState.NO_PROVIDER, reading.state());
        assertFalse(reading.isValid());
    }

    @Test
    void lecturaConEstadoWarmingUpNoEsValida() {
        MetricReading<Integer> reading = new MetricReading<>(0, MetricState.WARMING_UP, "RPM");

        assertFalse(reading.isValid());
    }

    @Test
    void estadoNoPuedeSerNull() {
        assertThrows(NullPointerException.class,
                () -> new MetricReading<>(75.5f, null, "°C"));
    }

    @Test
    void recordEsInmutable() {
        MetricReading<Float> original = new MetricReading<>(70f, MetricState.VALID, "°C");
        // Los records no expiden setters; value/state/unit son accesores de solo lectura.
        assertEquals(70f, original.value());
        assertEquals(MetricState.VALID, original.state());
        assertEquals("°C", original.unit());
    }
}
