package com.mace.infrastructure.telemetry;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Configuración de la caja negra persistente.
 *
 * <p>Política explícita de archivos: cada sesión (y cada parte cuando un archivo se llena) crea
 * un archivo nuevo {@code mace-blackbox-<UTC>-<pid>-<parte>.mbbx}; nunca se reescribe uno
 * existente. Antes de crear un archivo se borran los más antiguos hasta respetar
 * {@code maxFiles} y {@code maxTotalBytes}. Los archivos incompletos de sesiones anteriores se
 * conservan intactos y se recuperan en lectura.</p>
 *
 * @param directory           carpeta de datos de usuario
 * @param ringCapacityRecords capacidad del anillo temporal en RAM (1–65 536)
 * @param fileCapacityRecords registros por archivo antes de rotar
 * @param maxFiles            archivos máximos conservados
 * @param maxTotalBytes       tamaño total máximo de la carpeta
 */
public record BlackboxSettings(Path directory, int ringCapacityRecords, int fileCapacityRecords,
                               int maxFiles, long maxTotalBytes) {

    public static final String DIRECTORY_PROPERTY = "mace.blackbox.dir";

    public BlackboxSettings {
        Objects.requireNonNull(directory, "directory no puede ser null");
        if (ringCapacityRecords <= 0 || ringCapacityRecords > 65_536) {
            throw new IllegalArgumentException("ringCapacityRecords debe estar en [1, 65536]");
        }
        if (fileCapacityRecords <= 0 || maxFiles <= 0 || maxTotalBytes <= 0) {
            throw new IllegalArgumentException("Límites de archivo deben ser positivos");
        }
    }

    /**
     * Valores por defecto: anillo de 4 096 registros, archivos de 8 192 registros (≈1,5 MiB),
     * 20 archivos y 64 MiB como máximo.
     */
    public static BlackboxSettings defaults(Path directory) {
        return new BlackboxSettings(directory, 4_096, 8_192, 20, 64L * 1024 * 1024);
    }

    /**
     * Carpeta por defecto: {@code -Dmace.blackbox.dir}, si no {@code %LOCALAPPDATA%\MACE\blackbox}
     * y, fuera de Windows, {@code ~/.mace/blackbox}. Los datos simulados van a una subcarpeta
     * {@code simulated} para no mezclarse con los reales.
     */
    public static Path defaultDirectory(boolean simulated) {
        Path base;
        String configured = System.getProperty(DIRECTORY_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            base = Path.of(configured.trim());
        } else {
            String localAppData = System.getenv("LOCALAPPDATA");
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            base = windows && localAppData != null && !localAppData.isBlank()
                    ? Path.of(localAppData, "MACE", "blackbox")
                    : Path.of(System.getProperty("user.home", "."), ".mace", "blackbox");
        }
        return (simulated ? base.resolve("simulated") : base).toAbsolutePath().normalize();
    }

    public BlackboxSettings withRingCapacity(int capacity) {
        return new BlackboxSettings(directory, capacity, fileCapacityRecords, maxFiles, maxTotalBytes);
    }
}
