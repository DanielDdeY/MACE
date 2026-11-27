package com.mace.infrastructure.pawnio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sin procesos reales: el runner es simulado y solo registra el comando. */
class PawnIoInstallerTest {

    @TempDir
    Path root;

    private Path image() throws IOException {
        Files.createDirectories(root.resolve("installer"));
        Files.createDirectories(root.resolve("app").resolve("prerequisites"));
        Files.writeString(root.resolve("installer").resolve("mace-pawnio-setup.exe"), "c");
        Files.writeString(root.resolve("app").resolve("prerequisites").resolve("PawnIO_setup.exe"), "s");
        return root;
    }

    @Test
    void sinPiezasDeLaInstalacionNoSeOfrece() {
        assertTrue(PawnIoInstaller.locate(root, null, (c, l) -> 0).isEmpty());
        assertTrue(PawnIoInstaller.locate(null, null, (c, l) -> 0).isEmpty());
    }

    @Test
    void ejecutaElCoordinadorConElSetupIncluidoYElRegistro() throws IOException {
        List<List<String>> calls = new ArrayList<>();
        List<Duration> limits = new ArrayList<>();
        Path logs = root.resolve("logs");
        PawnIoInstaller installer = PawnIoInstaller.locate(image(), logs, (command, limit) -> {
            calls.add(command);
            limits.add(limit);
            return 0;
        }).orElseThrow();

        PawnIoInstaller.Result result = installer.install();

        assertEquals(PawnIoInstaller.Outcome.INSTALLED, result.outcome());
        assertTrue(result.succeeded());
        assertEquals(List.of(
                root.resolve("installer").resolve("mace-pawnio-setup.exe").toString(),
                "--setup", root.resolve("app").resolve("prerequisites").resolve("PawnIO_setup.exe").toString(),
                "--log", logs.resolve("pawnio-mace.log").toString()), calls.get(0));
        assertEquals(PawnIoInstaller.WAIT_LIMIT, limits.get(0));
        assertTrue(Files.isDirectory(logs));
    }

    @Test
    void sinCarpetaDeRegistroInstalaIgual() throws IOException {
        List<List<String>> calls = new ArrayList<>();
        PawnIoInstaller installer = PawnIoInstaller.locate(image(), null, (command, limit) -> {
            calls.add(command);
            return 3010;
        }).orElseThrow();

        assertEquals(PawnIoInstaller.Outcome.REBOOT_REQUIRED, installer.install().outcome());
        assertFalse(calls.get(0).contains("--log"));
    }

    @Test
    void traduceTodoElContratoDeCodigos() {
        assertEquals(PawnIoInstaller.Outcome.INSTALLED, PawnIoInstaller.interpret(0).outcome());
        assertEquals(PawnIoInstaller.Outcome.REBOOT_REQUIRED, PawnIoInstaller.interpret(3010).outcome());
        assertEquals(PawnIoInstaller.Outcome.ALREADY_PRESENT, PawnIoInstaller.interpret(10).outcome());
        assertEquals(PawnIoInstaller.Outcome.ALREADY_PRESENT, PawnIoInstaller.interpret(11).outcome());
        assertEquals(PawnIoInstaller.Outcome.STATE_UNKNOWN, PawnIoInstaller.interpret(12).outcome());
        assertEquals(PawnIoInstaller.Outcome.NOT_ELEVATED, PawnIoInstaller.interpret(13).outcome());
        assertEquals(PawnIoInstaller.Outcome.VERIFICATION_FAILED, PawnIoInstaller.interpret(20).outcome());
        assertEquals(PawnIoInstaller.Outcome.VERIFICATION_FAILED, PawnIoInstaller.interpret(21).outcome());
        assertEquals(PawnIoInstaller.Outcome.SIGNATURE_UNVERIFIED, PawnIoInstaller.interpret(22).outcome());
        assertEquals(PawnIoInstaller.Outcome.SETUP_FAILED, PawnIoInstaller.interpret(30).outcome());
        assertEquals(PawnIoInstaller.Outcome.NOT_CORROBORATED, PawnIoInstaller.interpret(31).outcome());
        assertEquals(PawnIoInstaller.Outcome.TIMEOUT, PawnIoInstaller.interpret(40).outcome());
        assertEquals(PawnIoInstaller.Outcome.TIMEOUT,
                PawnIoInstaller.interpret(PawnIoInstaller.NOT_FINISHED).outcome());
        assertEquals(PawnIoInstaller.Outcome.SETUP_FAILED, PawnIoInstaller.interpret(1603).outcome());
        assertFalse(PawnIoInstaller.interpret(21).succeeded());
    }

    @Test
    void fallosAlLanzarNoSeConviertenEnExito() throws IOException {
        PawnIoInstaller broken = PawnIoInstaller.locate(image(), null, (c, l) -> {
            throw new IOException("no se pudo crear el proceso");
        }).orElseThrow();
        assertEquals(PawnIoInstaller.Outcome.LAUNCH_FAILED, broken.install().outcome());
    }

    @Test
    void losMensajesNoIncluyenRutas() {
        for (PawnIoInstaller.Outcome outcome : PawnIoInstaller.Outcome.values()) {
            String message = new PawnIoInstaller.Result(outcome, 1).message();
            assertFalse(message.isBlank(), outcome.name());
            assertFalse(message.matches("(?s).*[A-Za-z]:\\\\.*"), outcome.name());
        }
    }
}
