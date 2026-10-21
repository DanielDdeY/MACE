package com.mace.infrastructure.nativebridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Identificación exacta de la DLL cargada mediante SHA-256. El hash calculado se expone para el
 * informe de integración; si se configura un hash esperado ({@value #SYSTEM_PROPERTY} o
 * {@value #ENV_VARIABLE}), una DLL distinta se rechaza antes de cargarla.
 */
public final class NativeLibraryIntegrity {

    public static final String SYSTEM_PROPERTY = "mace.native.sha256";
    public static final String ENV_VARIABLE = "MACE_NATIVE_SHA256";

    private NativeLibraryIntegrity() {
    }

    /** SHA-256 en hexadecimal en mayúsculas (mismo formato que {@code Get-FileHash}). */
    public static String sha256(Path file) {
        Objects.requireNonNull(file, "file no puede ser null");
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().withUpperCase().formatHex(digest.digest());
        } catch (IOException e) {
            throw new NativeLibraryException("No se pudo leer la biblioteca nativa para calcular su SHA-256: " + file, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    /** Hash esperado configurado, normalizado a mayúsculas. */
    public static Optional<String> expectedSha256() {
        String value = System.getProperty(SYSTEM_PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv(ENV_VARIABLE);
        }
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * @return el SHA-256 calculado
     * @throws NativeLibraryException si hay un hash esperado y no coincide
     */
    public static String verify(Path file, Optional<String> expected) {
        String actual = sha256(file);
        expected.ifPresent(hash -> {
            if (!hash.equalsIgnoreCase(actual)) {
                throw new NativeLibraryException("La DLL " + file + " no es la versión verificada: SHA-256 "
                        + actual + " ≠ esperado " + hash + ". Usa bin/mace_native.dll del paquete de Dev 1.");
            }
        });
        return actual;
    }
}
