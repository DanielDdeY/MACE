package com.mace.infrastructure.nativebridge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.SymbolLookup;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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
                () -> NativeFunctions.load(falsa, Optional.empty()));

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
                () -> NativeFunctions.load(kernel32, Optional.empty()));

        assertTrue(error.getMessage().contains(NativeFunctions.SYMBOL_GET_ABI_INFO), error.getMessage());
    }

    @Test
    void unaDllAnteriorSinCpGetAbiInfoSeRechazaAntesDeEnlazarNada() {
        // Una DLL heredada exporta los siete símbolos antiguos pero no CP_GetAbiInfo.
        SymbolLookup soloHeredados = name -> Optional.empty();

        NativeLibraryException error = assertThrows(NativeLibraryException.class,
                () -> new NativeFunctions(Path.of("mace_native.dll"), "ABC", soloHeredados));

        assertTrue(error.getMessage().contains("CP_GetAbiInfo"), error.getMessage());
        assertTrue(error.getMessage().contains("anterior"), error.getMessage());
    }

    @Test
    void unaDllConHashDistintoSeRechazaSinCargarla(@TempDir Path dir) throws IOException {
        Path dll = dir.resolve("mace_native.dll");
        Files.writeString(dll, "contenido", StandardCharsets.UTF_8);

        NativeLibraryException error = assertThrows(NativeLibraryException.class,
                () -> NativeFunctions.load(dll, Optional.of("00".repeat(32))));

        assertTrue(error.getMessage().contains("SHA-256"), error.getMessage());
        assertTrue(error.getMessage().contains(NativeLibraryIntegrity.sha256(dll)), error.getMessage());
    }

    @Test
    void elHashSeCalculaEnElFormatoDeGetFileHash(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("abc.bin");
        Files.writeString(file, "abc", StandardCharsets.US_ASCII);

        assertEquals("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
                NativeLibraryIntegrity.sha256(file));
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
    void elLocalizadorDevuelveVacioSiLaPropiedadApuntaAUnaRutaInexistente() {
        System.setProperty(NativeLibraryLocator.SYSTEM_PROPERTY, "Z:\\no\\existe\\mace_native.dll");

        assertTrue(NativeLibraryLocator.locate().isEmpty());
        assertFalse(NativeFunctions.isAvailable());
    }

    @Test
    void losNombresDeArchivoSiguenLaConvencionDeLaPlataforma() {
        List<String> names = NativeLibraryLocator.libraryFileNames();

        assertEquals(List.of(System.mapLibraryName("mace_native")), names);
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
