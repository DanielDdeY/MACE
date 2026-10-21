package com.mace.infrastructure;

import com.mace.domain.DomainFixtures;
import com.mace.domain.model.BlackboxStats;
import com.mace.domain.port.out.BlackboxSinkPort;
import com.mace.infrastructure.mock.MockScenario;
import com.mace.infrastructure.nativebridge.NativeLibraryException;
import com.mace.infrastructure.nativebridge.NativeLibraryLocator;
import com.mace.infrastructure.telemetry.BlackboxSettings;
import com.mace.infrastructure.telemetry.NoOpBlackboxSink;
import com.mace.infrastructure.telemetry.PersistentBlackboxSink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfrastructureAdaptersTest {

    @AfterEach
    void limpiarPropiedades() {
        System.clearProperty(InfrastructureAdapters.MODE_PROPERTY);
        System.clearProperty(NativeLibraryLocator.SYSTEM_PROPERTY);
        System.clearProperty(MockScenario.PROPERTY);
    }

    @Test
    void elModoMockEntregaAdaptadoresSimuladosFuncionales() {
        try (InfrastructureAdapters adapters = InfrastructureAdapters.create(InfrastructureAdapters.Mode.MOCK)) {
            assertFalse(adapters.isNative());
            assertTrue(adapters.nativeLibrary().isEmpty());
            assertTrue(adapters.fallbackReason().isEmpty(), "mock explícito no es una caída");
            assertEquals(MockScenario.NORMAL, adapters.mockScenario().orElseThrow());
            assertNotNull(adapters.sensors().fetchHardwareSnapshot());
            assertNotNull(adapters.sensors().fetchCurrentTelemetry());
            assertFalse(adapters.processes().listUserlandProcesses().isEmpty());
            assertTrue(adapters.dataSourceDescription().startsWith("Datos simulados"));
        }
    }

    @Test
    void elModoMockNuncaCargaLaDllAunqueLaRutaApunteAUnArchivoInvalido(@TempDir Path dir) throws IOException {
        Path basura = dir.resolve("mace_native.dll");
        Files.writeString(basura, "no es una DLL");
        System.setProperty(NativeLibraryLocator.SYSTEM_PROPERTY, basura.toString());

        try (InfrastructureAdapters adapters = InfrastructureAdapters.create(InfrastructureAdapters.Mode.MOCK)) {
            assertFalse(adapters.isNative());
        }
    }

    @Test
    void elEscenarioSimuladoSeEligePorPropiedad() {
        System.setProperty(MockScenario.PROPERTY, "DUAL_GPU");

        try (InfrastructureAdapters adapters = InfrastructureAdapters.mock()) {
            assertEquals(2, adapters.sensors().fetchHardwareSnapshot().gpus().size());
        }
    }

    @Test
    void elModoAutoCaeAlSimuladoCuandoNoHayDllEInformaElMotivo() {
        sinDllDisponible();

        try (InfrastructureAdapters adapters = InfrastructureAdapters.create(InfrastructureAdapters.Mode.AUTO)) {
            assertFalse(adapters.isNative());
            assertTrue(adapters.fallbackReason().isPresent(), "la UI debe poder mostrar «Datos simulados»");
            assertTrue(adapters.dataSourceDescription().contains("DLL no disponible"));
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

    @Test
    void laCajaNegraSimuladaSeAbreUnaVezYSeCierraConLosAdaptadores(@TempDir Path dir) {
        InfrastructureAdapters adapters = InfrastructureAdapters.mock(MockScenario.BLACKBOX_GAP, 1);
        BlackboxSinkPort sink = adapters.openBlackbox(BlackboxSettings.defaults(dir));
        assertSame(sink, adapters.openBlackbox(BlackboxSettings.defaults(dir)));
        assertInstanceOf(PersistentBlackboxSink.class, sink);

        sink.record(adapters.sensors().fetchHardwareSnapshot());
        sink.flush();
        BlackboxStats stats = sink.stats();
        adapters.close();

        assertTrue(stats.hasGaps(), "el perfil BLACKBOX_GAP usa un anillo mínimo");
        assertFalse(sink.stats().enabled());
        assertEquals(1, PersistentBlackboxSink.listBlackboxFiles(dir).size());
    }

    @Test
    void siLaCajaNegraNoPuedeAbrirseLaAppSigueSinPersistencia(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("no-es-carpeta");
        Files.writeString(file, "x");

        try (InfrastructureAdapters adapters = InfrastructureAdapters.mock(MockScenario.NORMAL, 1)) {
            BlackboxSinkPort sink = adapters.openBlackbox(BlackboxSettings.defaults(file.resolve("sub")));

            assertInstanceOf(NoOpBlackboxSink.class, sink);
            assertTrue(adapters.blackboxFailure().isPresent());
            sink.record(DomainFixtures.snapshot(DomainFixtures.cpu(50)));
            assertFalse(sink.stats().enabled());
        }
    }

    /** Fuerza al localizador a no encontrar ninguna DLL, incluso si existe una en la maquina. */
    private static void sinDllDisponible() {
        System.setProperty(NativeLibraryLocator.SYSTEM_PROPERTY,
                Path.of("carpeta-inexistente-" + System.nanoTime()).toString());
    }
}
