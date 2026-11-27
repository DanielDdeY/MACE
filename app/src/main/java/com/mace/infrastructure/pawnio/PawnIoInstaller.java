package com.mace.infrastructure.pawnio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Instala PawnIO a petición del usuario reutilizando el coordinador del instalador
 * ({@code installer/mace-pawnio-setup.exe}). Toda la verificación (hash, firma,
 * servicio ya presente, espera máxima de 180 s sin matar el setup) la hace el
 * coordinador; esta clase solo lo localiza en la imagen instalada, lo ejecuta y
 * traduce su código de salida. Nunca se usa en modo simulado (Preview).
 */
public final class PawnIoInstaller {

    /** Margen sobre los 180 s del coordinador antes de dejar de esperarlo (sin matarlo). */
    static final Duration WAIT_LIMIT = Duration.ofSeconds(200);
    static final int NOT_FINISHED = Integer.MIN_VALUE;

    public enum Outcome {
        INSTALLED, REBOOT_REQUIRED, ALREADY_PRESENT, STATE_UNKNOWN, NOT_ELEVATED,
        VERIFICATION_FAILED, SIGNATURE_UNVERIFIED, SETUP_FAILED, NOT_CORROBORATED,
        TIMEOUT, LAUNCH_FAILED
    }

    public record Result(Outcome outcome, int exitCode) {
        public Result {
            Objects.requireNonNull(outcome, "outcome no puede ser null");
        }

        public boolean succeeded() {
            return outcome == Outcome.INSTALLED || outcome == Outcome.REBOOT_REQUIRED
                    || outcome == Outcome.ALREADY_PRESENT;
        }

        /** Texto para el usuario; no incluye rutas ni datos de la máquina. */
        public String message() {
            return switch (outcome) {
                case INSTALLED -> "PawnIO se instaló correctamente. Si la temperatura de la CPU sigue en N/D, "
                        + "cierra y vuelve a abrir MACE.";
                case REBOOT_REQUIRED -> "PawnIO se instaló. Reinicia Windows para habilitar los sensores de CPU.";
                case ALREADY_PRESENT -> "PawnIO ya estaba instalado; no se modificó. Si la temperatura sigue en N/D, "
                        + "reinicia Windows.";
                case STATE_UNKNOWN -> "No se pudo comprobar si PawnIO está instalado; no se instaló nada.";
                case NOT_ELEVATED -> "Para instalar PawnIO, abre MACE como administrador.";
                case VERIFICATION_FAILED -> "No se instaló PawnIO: el instalador incluido no pasó la comprobación "
                        + "de integridad o de firma. Reinstala MACE.";
                case SIGNATURE_UNVERIFIED -> "No se pudo comprobar la firma de PawnIO (¿sin conexión a Internet?). "
                        + "Conéctate y vuelve a intentarlo.";
                case SETUP_FAILED -> "El instalador de PawnIO terminó con error (código " + exitCode + ").";
                case NOT_CORROBORATED -> "El instalador de PawnIO terminó, pero el controlador no aparece "
                        + "registrado. Reinicia Windows y vuelve a comprobarlo.";
                case TIMEOUT -> "La instalación de PawnIO sigue en curso o no respondió a tiempo. "
                        + "Reinicia Windows antes de volver a intentarlo.";
                case LAUNCH_FAILED -> "No se pudo iniciar el instalador de PawnIO. Reinstala MACE.";
            };
        }
    }

    /** Ejecuta un comando y devuelve su código de salida, o {@link #NOT_FINISHED} si no terminó a tiempo. */
    @FunctionalInterface
    public interface ProcessRunner {
        int run(List<String> command, Duration limit) throws IOException, InterruptedException;
    }

    private final Path coordinator;
    private final Path setup;
    private final Path log;
    private final ProcessRunner runner;

    PawnIoInstaller(Path coordinator, Path setup, Path log, ProcessRunner runner) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator no puede ser null");
        this.setup = Objects.requireNonNull(setup, "setup no puede ser null");
        this.log = log;
        this.runner = Objects.requireNonNull(runner, "runner no puede ser null");
    }

    /** Localiza las piezas en la imagen instalada; vacío fuera de una instalación completa. */
    public static Optional<PawnIoInstaller> locate() {
        String launcher = System.getProperty("jpackage.app-path");
        if (launcher == null || launcher.isBlank()) {
            return Optional.empty();
        }
        try {
            Path launcherDir = Path.of(launcher).toAbsolutePath().normalize().getParent();
            String programData = System.getenv("ProgramData");
            Path logDir = programData == null || programData.isBlank()
                    ? null : Path.of(programData, "MACE", "InstallerLogs");
            return locate(launcherDir, logDir, PawnIoInstaller::runProcess);
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    static Optional<PawnIoInstaller> locate(Path launcherDir, Path logDir, ProcessRunner runner) {
        if (launcherDir == null) {
            return Optional.empty();
        }
        Path coordinator = launcherDir.resolve("installer").resolve("mace-pawnio-setup.exe");
        Path setup = launcherDir.resolve("app").resolve("prerequisites").resolve("PawnIO_setup.exe");
        if (!Files.isRegularFile(coordinator, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(setup, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        Path log = logDir == null ? null : logDir.resolve("pawnio-mace.log");
        return Optional.of(new PawnIoInstaller(coordinator, setup, log, runner));
    }

    /** Bloqueante (hasta ~200 s): llamar fuera del hilo de JavaFX. */
    public Result install() {
        List<String> command = List.of(coordinator.toString(), "--setup", setup.toString());
        if (log != null && logDirectoryReady()) {
            command = List.of(coordinator.toString(), "--setup", setup.toString(), "--log", log.toString());
        }
        try {
            int code = runner.run(command, WAIT_LIMIT);
            return interpret(code);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(Outcome.TIMEOUT, NOT_FINISHED);
        } catch (IOException | SecurityException e) {
            return new Result(Outcome.LAUNCH_FAILED, -1);
        }
    }

    /** El registro es opcional: si no se puede crear su carpeta se instala sin él. */
    private boolean logDirectoryReady() {
        try {
            Files.createDirectories(log.getParent());
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    /** Traduce el contrato de códigos de installer/README.md. */
    static Result interpret(int code) {
        Outcome outcome = switch (code) {
            case 0 -> Outcome.INSTALLED;
            case 3010 -> Outcome.REBOOT_REQUIRED;
            case 10, 11 -> Outcome.ALREADY_PRESENT;
            case 12 -> Outcome.STATE_UNKNOWN;
            case 13 -> Outcome.NOT_ELEVATED;
            case 20, 21 -> Outcome.VERIFICATION_FAILED;
            case 22 -> Outcome.SIGNATURE_UNVERIFIED;
            case 31 -> Outcome.NOT_CORROBORATED;
            case 40, NOT_FINISHED -> Outcome.TIMEOUT;
            default -> Outcome.SETUP_FAILED;
        };
        return new Result(outcome, code);
    }

    private static int runProcess(List<String> command, Duration limit) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        process.getOutputStream().close();
        // D3: si no termina a tiempo se deja de esperar, pero no se termina el proceso.
        if (!process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS)) {
            return NOT_FINISHED;
        }
        return process.exitValue();
    }
}
