package com.mace.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuAdapterTest {

    @Test
    void creaAdaptadorConIdentificadorDeSesionNombreYBus() {
        GpuAdapter adapter = new GpuAdapter("GPU-0", "NVIDIA GeForce RTX 3060", "PCI-01-00-0");

        assertEquals("GPU-0", adapter.sessionId());
        assertEquals("NVIDIA GeForce RTX 3060", adapter.name());
        assertEquals("PCI-01-00-0", adapter.pciBusId());
    }

    @Test
    void sessionIdNullLanzaExcepcion() {
        assertThrows(NullPointerException.class,
                () -> new GpuAdapter(null, "GPU", null));
    }

    @Test
    void nombreVacioLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> new GpuAdapter("GPU-0", "   ", null));
    }

    @Test
    void recordEsInmutable() {
        GpuAdapter adapter = new GpuAdapter("GPU-0", "Nombre", "BUS");
        assertTrue(adapter.sessionId().equals("GPU-0"));
        assertTrue(adapter.name().equals("Nombre"));
    }
}
