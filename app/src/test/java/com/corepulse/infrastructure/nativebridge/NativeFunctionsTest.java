package com.corepulse.infrastructure.nativebridge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Cubre la carga de la DLL y la localización en disco. La DLL real no está disponible en
 * CI, así que se verifican los caminos de error (archivo inexistente, archivo inválido,
 * biblioteca sin los símbolos esperados) y la resolución de rutas.
 */
class NativeFunctionsTest {

    @AfterEach
    void limpiarPropiedad() {
        System.clearProperty(NativeLibraryLocator.SYSTEM_PROPERTY);
    }

    @Test
    void lanzaExcepcionDescriptivaSiLaDllNoExiste() {
        Path inexistente = Path.of("no-existe-" + System.nanoTime(), "mace_native.dll");

        NativeLibraryException error = assertThrows(NativeLibraryException.class,
                () -> NativeFunctions.load(inexistente));

        assertTrue(error.getMessage().contains("No existe"), error.getMessage());
        assertTrue(error.getMessage().contains("mace_native.dll"), error.getMessage());
    }

    @Test
    void lanzaExcepcionSiElArchivoNoEsUnaBibliotecaValida(@TempDir Path dir) throws IOException {
        Path falsa = dir.resolve("mace_native.dll");
        Files.writeString(falsa, "esto no es una DLL", StandardCharsets.UTF_8);

        NativeLibraryException error = assertThrows(NativeLibraryException.class,
                () -> NativeFunctions.load(falsa));

        assertTrue(error.getMessage().contains("No se pudo cargar"), error.getMessage());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void lanzaExcepcionSiLaBibliotecaNoExportaLosSimbolosDeMace() {
        String systemRoot = System.getenv("SystemRoot");
        assumeTrue(systemRoot != null && !systemRoot.isBlank(), "SystemRoot no definido");
        Path kernel32 = Path.of(systemRoot, "System32", "kernel32.dll");
        assumeTrue(Files.isRegularFile(kernel32), "kernel32.dll no encontrada");

        NativeLibraryException error = assertThrows(NativeLibraryException.class,
                () -> NativeFunctions.load(kernel32));

        assertTrue(error.getMessage().contains(NativeFunctions.SYMBOL_INITIALIZE_TELEMETRY), error.getMessage());
    }

    @Test
    void loadSinRutaLanzaConDiagnosticoCuandoNoHayDll() {
        System.setProperty(NativeLibraryLocator.SYSTEM_PROPERTY,
                Path.of("carpeta-inexistente-" + System.nanoTime()).toString());

        NativeLibraryException error = assertThrows(NativeLibraryException.class, NativeFunctions::load);

        assertTrue(error.getMessage().contains(NativeLibraryLocator.SYSTEM_PROPERTY), error.getMessage());
        assertTrue(error.getMessage().contains("Directorios inspeccionados"), error.getMessage());
    }

    @Test
    void isAvailableNuncaLanza() {
        assertDoesNotThrow(NativeFunctions::isAvailable);
    }

    // ------------------------------------------------------------------
    // NativeLibraryLocator
    // ------------------------------------------------------------------

    @Test
    void elLocalizadorAceptaUnaCarpetaEnLaPropiedadDelSistema(@TempDir Path dir) throws IOException {
        Path dll = dir.resolve(System.mapLibraryName("mace_native"));
        Files.createFile(dll);
        System.setProperty(NativeLibraryLocator.SYSTEM_PROPERTY, dir.toString());

        assertEquals(dll.toAbsolutePath().normalize(), NativeLibraryLocator.locate().orElseThrow());
    }

    @Test
    void elLocalizadorAceptaLaRutaDelArchivoEnLaPropiedadDelSistema(@TempDir Path dir) throws IOException {
        Path dll = dir.resolve("cualquier-nombre.dll");
        Files.createFile(dll);
        System.setProperty(NativeLibraryLocator.SYSTEM_PROPERTY, dll.toString());

        assertEquals(dll.toAbsolutePath().normalize(), NativeLibraryLocator.locate().orElseThrow());
    }

    @Test
    void elLocalizadorPrefiereMaceNativeSobreCorepulseNative(@TempDir Path dir) throws IOException {
        Path preferida = dir.resolve(System.mapLibraryName("mace_native"));
        Path alternativa = dir.resolve(System.mapLibraryName("corepulse_native"));
        Files.createFile(alternativa);
        Files.createFile(preferida);
        System.setProperty(NativeLibraryLocator.SYSTEM_PROPERTY, dir.toString());

        assertEquals(preferida.toAbsolutePath().normalize(), NativeLibraryLocator.locate().orElseThrow());
    }

    @Test
    void elLocalizadorDevuelveVacioSiLaPropiedadApuntaAUnaRutaInexistente() {
        System.setProperty(NativeLibraryLocator.SYSTEM_PROPERTY, "Z:\\no\\existe\\mace_native.dll");

        assertTrue(NativeLibraryLocator.locate().isEmpty());
        assertFalse(NativeFunctions.isAvailable());
    }

    @Test
    void losNombresDeArchivoSiguenLaConvencionDeLaPlataforma() {
        List<String> names = NativeLibraryLocator.libraryFileNames();

        assertEquals(List.of(System.mapLibraryName("mace_native"), System.mapLibraryName("corepulse_native")), names);
    }

    @Test
    void losDirectoriosDeBusquedaIncluyenLasSalidasHabitualesDeCmake() {
        Path workingDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> directories = NativeLibraryLocator.searchDirectories();

        assertTrue(directories.contains(workingDir));
        assertTrue(directories.contains(workingDir.resolve("native/build/Release").normalize()));
        assertEquals(directories.size(), directories.stream().distinct().count(), "sin duplicados");
    }
}
