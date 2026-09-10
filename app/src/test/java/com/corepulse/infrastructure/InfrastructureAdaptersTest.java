package com.corepulse.infrastructure;

import com.corepulse.infrastructure.nativebridge.NativeLibraryException;
import com.corepulse.infrastructure.nativebridge.NativeLibraryLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfrastructureAdaptersTest {

    @AfterEach
    void limpiarPropiedades() {
        System.clearProperty(InfrastructureAdapters.MODE_PROPERTY);
        System.clearProperty(NativeLibraryLocator.SYSTEM_PROPERTY);
    }

    @Test
    void elModoMockEntregaAdaptadoresSimuladosFuncionales() {
        try (InfrastructureAdapters adapters = InfrastructureAdapters.create(InfrastructureAdapters.Mode.MOCK)) {
            assertFalse(adapters.isNative());
            assertTrue(adapters.nativeLibrary().isEmpty());
            assertNotNull(adapters.sensors().fetchCurrentTelemetry());
            assertFalse(adapters.processes().listUserlandProcesses().isEmpty());
        }
    }

    @Test
    void elModoAutoCaeAlSimuladoCuandoNoHayDll() {
        sinDllDisponible();

        try (InfrastructureAdapters adapters = InfrastructureAdapters.create(InfrastructureAdapters.Mode.AUTO)) {
            assertFalse(adapters.isNative());
            assertNotNull(adapters.sensors().fetchCurrentTelemetry());
        }
    }

    @Test
    void elModoNativeExigeLaDll() {
        sinDllDisponible();

        assertThrows(NativeLibraryException.class,
                () -> InfrastructureAdapters.create(InfrastructureAdapters.Mode.NATIVE));
    }

    @Test
    void createSinArgumentosRespetaLaPropiedadDelSistema() {
        System.setProperty(InfrastructureAdapters.MODE_PROPERTY, "mock");

        try (InfrastructureAdapters adapters = InfrastructureAdapters.create()) {
            assertFalse(adapters.isNative());
        }
    }

    @Test
    void elModoSeLeeSinDistinguirMayusculas() {
        System.setProperty(InfrastructureAdapters.MODE_PROPERTY, " Native ");
        assertEquals(InfrastructureAdapters.Mode.NATIVE, InfrastructureAdapters.Mode.fromSystemProperty());

        System.setProperty(InfrastructureAdapters.MODE_PROPERTY, "MOCK");
        assertEquals(InfrastructureAdapters.Mode.MOCK, InfrastructureAdapters.Mode.fromSystemProperty());
    }

    @Test
    void unModoDesconocidoOAusenteEquivaleAAuto() {
        System.clearProperty(InfrastructureAdapters.MODE_PROPERTY);
        assertEquals(InfrastructureAdapters.Mode.AUTO, InfrastructureAdapters.Mode.fromSystemProperty());

        System.setProperty(InfrastructureAdapters.MODE_PROPERTY, "lo-que-sea");
        assertEquals(InfrastructureAdapters.Mode.AUTO, InfrastructureAdapters.Mode.fromSystemProperty());
    }

    @Test
    void cerrarEnModoSimuladoEsInofensivo() {
        InfrastructureAdapters adapters = InfrastructureAdapters.mock();
        adapters.close();
        adapters.close();
        assertNotNull(adapters.sensors().fetchCurrentTelemetry());
    }

    /** Fuerza al localizador a no encontrar ninguna DLL, incluso si existe una en la maquina. */
    private static void sinDllDisponible() {
        System.setProperty(NativeLibraryLocator.SYSTEM_PROPERTY,
                Path.of("carpeta-inexistente-" + System.nanoTime()).toString());
    }
}
