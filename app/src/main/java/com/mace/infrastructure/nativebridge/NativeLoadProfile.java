package com.mace.infrastructure.nativebridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Build-time policy. Runtime environment variables and -D flags cannot enable development. */
public final class NativeLoadProfile {
    public static final String PRODUCTION_FAILURE =
            "No se pudo verificar el componente nativo de MACE. Repara la instalación y vuelve a intentarlo.";
    private static final boolean DEVELOPMENT = readDevelopmentBuild();

    private NativeLoadProfile() { }

    public static boolean isDevelopment() { return DEVELOPMENT; }

    private static boolean readDevelopmentBuild() {
        try (InputStream input = NativeLoadProfile.class.getResourceAsStream("native-profile.properties")) {
            if (input == null) return false;
            Properties metadata = new Properties();
            metadata.load(input);
            return "development".equals(metadata.getProperty("profile"));
        } catch (IOException | IllegalArgumentException error) {
            return false; // Missing/malformed metadata never relaxes policy.
        }
    }

    static Parameters productionParameters() {
        try {
            String location = System.getProperty(NativeLibraryLocator.SYSTEM_PROPERTY);
            String hash = System.getProperty(NativeLibraryIntegrity.SYSTEM_PROPERTY);
            if (location == null || location.isBlank() || hash == null || !hash.matches("[0-9a-fA-F]{64}")) {
                throw productionFailure();
            }
            Path path = Path.of(location);
            if (!path.isAbsolute()) throw productionFailure();
            return new Parameters(path.normalize(), hash.toUpperCase(Locale.ROOT));
        } catch (RuntimeException error) {
            throw productionFailure(); // No cause carrying local paths or pins.
        }
    }

    static NativeLibraryException productionFailure() {
        return new NativeLibraryException(PRODUCTION_FAILURE);
    }

    record Parameters(Path path, String sha256) { }
}
