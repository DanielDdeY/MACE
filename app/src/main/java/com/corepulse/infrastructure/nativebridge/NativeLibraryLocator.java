package com.corepulse.infrastructure.nativebridge;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Localiza en disco la DLL nativa de MACE sin acoplarse a una ruta fija.
 *
 * <p>Orden de resolución:</p>
 * <ol>
 *   <li>Propiedad del sistema {@value #SYSTEM_PROPERTY} (ruta a la DLL o a su carpeta).</li>
 *   <li>Variable de entorno {@value #ENV_VARIABLE} (ídem).</li>
 *   <li>Directorio {@code app/} de la aplicación empaquetada con {@code jpackage} ({@code $APPDIR}).</li>
 *   <li>Entradas de {@code java.library.path}.</li>
 *   <li>Directorio de trabajo y su padre, más las carpetas de salida habituales de CMake
 *       ({@code native/build/Release}, {@code native/out/build/x64-Release}, ...).</li>
 *   <li>Carpeta desde la que se cargó este JAR / directorio de clases.</li>
 * </ol>
 *
 * <p>Se aceptan dos nombres de archivo: {@code mace_native.dll} (nombre que produce el
 * {@code CMakeLists.txt} de la capa nativa) y {@code corepulse_native.dll} (nombre del
 * documento de arquitectura).</p>
 */
public final class NativeLibraryLocator {

    public static final String SYSTEM_PROPERTY = "mace.native.lib";
    public static final String ENV_VARIABLE = "MACE_NATIVE_LIB";

    /** Nombres base admitidos, en orden de preferencia. */
    public static final List<String> LIBRARY_BASE_NAMES = List.of("mace_native", "corepulse_native");

    private static final List<String> RELATIVE_BUILD_DIRS = List.of(
            "",
            "native",
            "native/build",
            "native/build/Release",
            "native/build/RelWithDebInfo",
            "native/build/Debug",
            "native/out/build/x64-Release",
            "native/out/build/x64-Debug"
    );

    private NativeLibraryLocator() {
    }

    /** Nombres de archivo candidatos según la plataforma (p. ej. {@code mace_native.dll}). */
    public static List<String> libraryFileNames() {
        List<String> names = new ArrayList<>(LIBRARY_BASE_NAMES.size());
        for (String base : LIBRARY_BASE_NAMES) {
            names.add(System.mapLibraryName(base));
        }
        return List.copyOf(names);
    }

    /**
     * Busca la DLL siguiendo el orden documentado en la clase.
     *
     * @return ruta absoluta al archivo si existe, o vacío si no se encontró
     */
    public static Optional<Path> locate() {
        Optional<Path> explicit = explicitLocation();
        if (explicit.isPresent()) {
            return resolveExplicit(explicit.get());
        }
        for (Path directory : searchDirectories()) {
            Optional<Path> found = findIn(directory);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /** Directorios que se inspeccionan (sin duplicados, en orden) cuando no hay ruta explícita. */
    public static List<Path> searchDirectories() {
        Set<Path> directories = new LinkedHashSet<>();

        appImageDirectory().ifPresent(appDir -> {
            directories.add(appDir);
            Path parent = appDir.getParent();
            if (parent != null) {
                directories.add(parent);
            }
        });

        String libraryPath = System.getProperty("java.library.path", "");
        for (String entry : libraryPath.split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                toPath(entry).ifPresent(directories::add);
            }
        }

        Path workingDir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        addBuildDirectories(directories, workingDir);
        Path workingParent = workingDir.getParent();
        if (workingParent != null) {
            addBuildDirectories(directories, workingParent);
        }

        codeSourceDirectory().ifPresent(codeDir -> {
            directories.add(codeDir);
            Path parent = codeDir.getParent();
            if (parent != null) {
                directories.add(parent);
            }
        });

        return List.copyOf(directories);
    }

    /** Mensaje de diagnóstico para cuando no se localiza la DLL. */
    public static String notFoundMessage() {
        StringBuilder message = new StringBuilder(512);
        message.append("No se encontró la biblioteca nativa de MACE (")
                .append(String.join(" | ", libraryFileNames()))
                .append("). Compílala con CMake desde 'native/' o indica su ruta con -D")
                .append(SYSTEM_PROPERTY)
                .append("=<ruta> o la variable de entorno ")
                .append(ENV_VARIABLE)
                .append(". Directorios inspeccionados:");
        for (Path directory : searchDirectories()) {
            message.append(System.lineSeparator()).append("  - ").append(directory);
        }
        return message.toString();
    }

    // ------------------------------------------------------------------

    private static Optional<Path> explicitLocation() {
        String property = System.getProperty(SYSTEM_PROPERTY);
        if (property != null && !property.isBlank()) {
            return toPath(property);
        }
        String env = System.getenv(ENV_VARIABLE);
        if (env != null && !env.isBlank()) {
            return toPath(env);
        }
        return Optional.empty();
    }

    private static Optional<Path> resolveExplicit(Path explicit) {
        if (Files.isRegularFile(explicit)) {
            return Optional.of(explicit.toAbsolutePath().normalize());
        }
        if (Files.isDirectory(explicit)) {
            return findIn(explicit);
        }
        return Optional.empty();
    }

    private static Optional<Path> findIn(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return Optional.empty();
        }
        for (String fileName : libraryFileNames()) {
            Path candidate = directory.resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate.toAbsolutePath().normalize());
            }
        }
        return Optional.empty();
    }

    private static void addBuildDirectories(Set<Path> directories, Path root) {
        for (String relative : RELATIVE_BUILD_DIRS) {
            directories.add(relative.isEmpty() ? root : root.resolve(relative).normalize());
        }
    }

    /** Carpeta {@code app/} de una imagen creada con jpackage (el launcher define {@code jpackage.app-path}). */
    private static Optional<Path> appImageDirectory() {
        String launcher = System.getProperty("jpackage.app-path");
        if (launcher == null || launcher.isBlank()) {
            return Optional.empty();
        }
        Optional<Path> launcherDir = toPath(launcher).map(Path::getParent);
        return launcherDir.map(dir -> dir.resolve("app")).or(() -> launcherDir);
    }

    private static Optional<Path> codeSourceDirectory() {
        try {
            CodeSource source = NativeLibraryLocator.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return Optional.empty();
            }
            Path location = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
            return Optional.ofNullable(Files.isDirectory(location) ? location : location.getParent());
        } catch (URISyntaxException | IllegalArgumentException | SecurityException e) {
            return Optional.empty();
        }
    }

    private static Optional<Path> toPath(String raw) {
        try {
            return Optional.of(Path.of(raw.trim()).toAbsolutePath().normalize());
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }
}
