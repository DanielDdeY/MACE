package com.mace.infrastructure.nativebridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Real child JVMs with production build metadata first, not a runtime profile override. */
class NativeProductionPolicyTest {
    @TempDir Path temporary;
    private static final String METADATA = "com/mace/infrastructure/nativebridge/native-profile.properties";

    @Test void lasCuatroVariablesAjenasNoCambianLaDllNiProveenUnHelper() throws Exception {
        run("production", "valid", "good", "good");
    }
    @Test void sinParametrosNoUsaElEntorno() throws Exception { run("production", "missing", null, null); }
    @Test void sinRutaNoBuscaEnElEntorno() throws Exception { run("production", "missing", null, "good"); }
    @Test void sinHashNoAceptaElHashDelEntorno() throws Exception { run("production", "missing", "good", null); }
    @Test void rutaRelativaNoEsUnPinDelLauncher() throws Exception { run("production", "missing", "relative", "good"); }
    @Test void hashMalformadoFallaGenerico() throws Exception { run("production", "missing", "good", "malformed"); }
    @Test void hashDistintoFallaGenerico() throws Exception { run("production", "missing", "good", "wrong"); }
    @Test void archivoAusenteNoHaceFallback() throws Exception { run("production", "missing", "absent", "good"); }
    @Test void sobrecargaExplicitaNoEludeLosPins() throws Exception { run("production", "explicit", null, null); }
    @Test void sobrecargaNoPuedeSustituirLaRutaFijada() throws Exception { run("production", "alternate", "good", "good"); }
    @Test void autoNoOcultaElFalloConMocks() throws Exception { run("production", "auto", null, null); }
    @Test void metadataAusenteNoHabilitaDesarrollo() throws Exception { run(null, "missing", null, null); }
    @Test void metadataDesconocidaNoHabilitaDesarrollo() throws Exception { run("unknown", "missing", null, null); }

    private void run(String profile, String mode, String location, String hash) throws Exception {
        Path root = temporary.resolve("production-classes");
        Path classes = Path.of(NativeLoadProfile.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (var files = Files.walk(classes)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path dest = root.resolve(classes.relativize(source));
                Files.createDirectories(dest.getParent());
                Files.copy(source, dest);
            }
        }
        Path metadata = root.resolve(METADATA);
        if (profile == null) Files.deleteIfExists(metadata);
        else Files.writeString(metadata, "profile=" + profile + "\n");
        // Copy only the probe; never let a test-resource development profile leak
        // into the production child, including the missing-metadata scenario.
        String probeEntry = NativeProductionProbe.class.getName().replace('.', '/') + ".class";
        Path probeTarget = root.resolve(probeEntry);
        Files.createDirectories(probeTarget.getParent());
        try (var probeBytes = NativeProductionProbe.class.getResourceAsStream("NativeProductionProbe.class")) {
            Files.copy(probeBytes, probeTarget);
        }
        Path repo = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if (repo.endsWith("app")) repo = repo.getParent();
        Path dll = repo.resolve("native/build/fase2-paso4b/prod/Release/mace_native.dll");
        if (!Files.isRegularFile(dll)) throw new AssertionError("Build the production native regression first");
        Path foreignHelper = repo.resolve("native/build/fase2-paso4b/dev/Release/lhm_fake_helper.exe");
        if (!Files.isRegularFile(foreignHelper)) throw new AssertionError("Build the development native fixtures first");
        Path pinned = temporary.resolve("mace_native.dll");
        Files.copy(dll, pinned);
        String sha = NativeLibraryIntegrity.sha256(pinned);
        Path foreign = temporary.resolve("foreign.dll");
        Files.writeString(foreign, "untrusted environment input");
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "--enable-native-access=ALL-UNNAMED",
                "-Dmace.native.profile=development", "-Dmace.build.nativeProfile=development",
                "-Dmace.native.development=true"));
        if (location != null) command.add("-Dmace.native.lib=" + switch (location) {
            case "good" -> pinned.toString(); case "relative" -> "mace_native.dll";
            default -> temporary.resolve("absent.dll").toString();
        });
        if (hash != null) command.add("-Dmace.native.sha256=" + switch (hash) {
            case "good" -> sha; case "wrong" -> "00".repeat(32); default -> "malformed";
        });
        Path testClasses = Path.of(NativeProductionProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String dependencies = java.util.Arrays.stream(System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path")).split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .filter(entry -> !Path.of(entry).equals(classes) && !Path.of(entry).equals(testClasses))
                .collect(java.util.stream.Collectors.joining(File.pathSeparator));
        command.addAll(List.of("-cp", root + File.pathSeparator + dependencies,
                NativeProductionProbe.class.getName(), mode, foreign.toString()));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().keySet().removeIf(key -> key.toUpperCase(java.util.Locale.ROOT).startsWith("MACE_"));
        for (String option : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) {
            builder.environment().remove(option);
        }
        builder.environment().put("MACE_NATIVE_LIB", foreign.toString());
        builder.environment().put("MACE_NATIVE_SHA256", NativeLibraryIntegrity.sha256(foreign));
        builder.environment().put("MACE_LHM_HELPER", foreignHelper.toString());
        builder.environment().put("MACE_LHM_HELPER_SHA256", NativeLibraryIntegrity.sha256(foreignHelper));
        builder.environment().put("MACE_NATIVE_PROFILE", "development");
        builder.environment().put("MACE_DEVELOPMENT", "1");
        Path output = temporary.resolve("probe.txt");
        Process child = builder.redirectOutput(output.toFile()).start();
        assertTrue(child.waitFor(20, TimeUnit.SECONDS), "Controlled probe did not return; no process forcibly terminated");
        String result = Files.readString(output);
        assertEquals(0, child.exitValue(), result);
        assertTrue(result.contains("PASS production"), result);
    }
}
