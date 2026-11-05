package com.mace.infrastructure.mock;

import com.mace.domain.model.ImagePath;
import com.mace.domain.model.MemoryMetrics;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.ProcessEntry;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessInventory;
import com.mace.domain.model.ProcessQueryStatus;
import com.mace.domain.model.ProcessSample;
import com.mace.domain.model.ProcessSampleResult;
import com.mace.domain.model.ResponsivenessOutcome;
import com.mace.domain.model.ResponsivenessResult;
import com.mace.domain.model.TerminationAttempt;
import com.mace.domain.model.TerminationOutcome;
import com.mace.domain.port.out.ProcessLifecyclePort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.LongSupplier;

/**
 * Implementación simulada de {@link ProcessLifecyclePort} con identidades completas y los casos
 * que la UI debe saber mostrar, sin tocar procesos reales:
 *
 * <ul>
 *   <li>{@value #HUNG_PROCESS_NAME}: ventana colgada ({@code HUNG}).</li>
 *   <li>{@value #NO_WINDOW_NAME}: proceso sin ventana ({@code NO_WINDOW}, distinto de colgado).</li>
 *   <li>{@value #LEAKY_NAME}: Working Set y Private Bytes crecen ~1,5 MiB por enumeración.</li>
 *   <li>{@value #SHORT_LIVED_NAME}: termina tras {@value #SHORT_LIVED_ENUMERATIONS} enumeraciones.</li>
 *   <li>{@value #REUSED_OLD_NAME}: termina tras {@value #REUSE_AFTER_ENUMERATIONS} enumeraciones y
 *       su PID {@value #REUSED_PID} pasa a {@value #REUSED_NEW_NAME} con otra fecha de creación.</li>
 *   <li>{@value #PROTECTED_NAME}: la terminación se rechaza como protegida.</li>
 *   <li>{@value #ELEVATED_NAME}: sin permisos (ruta, memoria y terminación con acceso denegado).</li>
 * </ul>
 *
 * <p>Con la misma semilla la fluctuación de memoria es idéntica. Es seguro para uso concurrente
 * (sondeo en hilo de fondo + acciones desde el hilo de UI).</p>
 */
public final class MockProcessAdapter implements ProcessLifecyclePort {

    public static final long HUNG_PROCESS_PID = 7777L;
    public static final String HUNG_PROCESS_NAME = "frozen-app.exe";
    public static final long NO_WINDOW_PID = 3100L;
    public static final String NO_WINDOW_NAME = "background-sync.exe";
    public static final long LEAKY_PID = 5150L;
    public static final String LEAKY_NAME = "leaky-app.exe";
    public static final long SHORT_LIVED_PID = 2600L;
    public static final String SHORT_LIVED_NAME = "installer-helper.exe";
    public static final int SHORT_LIVED_ENUMERATIONS = 5;
    public static final long REUSED_PID = 6400L;
    public static final String REUSED_OLD_NAME = "updater.exe";
    public static final String REUSED_NEW_NAME = "setup.exe";
    public static final int REUSE_AFTER_ENUMERATIONS = 8;
    public static final long PROTECTED_PID = 1800L;
    public static final String PROTECTED_NAME = "antivirus-guard.exe";
    public static final long ELEVATED_PID = 1900L;
    public static final String ELEVATED_NAME = "elevated-tool.exe";
    /** Procesos que la enumeración simulada declara omitidos por permisos. */
    public static final int SKIPPED_COUNT = 3;

    /** Base de las fechas de creación simuladas. */
    public static final Instant BOOT_TIME = Instant.parse("2026-01-01T08:00:00Z");

    private static final long MIB = 1024L * 1024L;
    private static final double MEMORY_JITTER = 0.03;
    private static final long LEAK_BYTES_PER_ENUMERATION = 3 * MIB / 2;

    private enum Behavior { NORMAL, HUNG, NO_WINDOW, LEAKY, SHORT_LIVED, REUSED_OLD, PROTECTED, ACCESS_DENIED }

    private record MockProcess(ProcessIdentity identity, String name, String path, long workingSetBase,
                               long privateBase, Behavior behavior) {
    }

    private final Map<ProcessIdentity, MockProcess> live = new LinkedHashMap<>();
    private final Random random;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private int enumerations;

    /** Tabla de ejemplo con la semilla de {@code -Dmace.mock.seed}. */
    public MockProcessAdapter() {
        this(MockScenario.seedFromSystemProperty(), Clock.systemUTC(), System::nanoTime);
    }

    public MockProcessAdapter(long seed) {
        this(seed, Clock.systemUTC(), System::nanoTime);
    }

    public MockProcessAdapter(long seed, Clock clock, LongSupplier nanoTime) {
        this.random = new Random(seed);
        this.clock = Objects.requireNonNull(clock, "clock no puede ser null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime no puede ser null");
        for (MockProcess process : sampleProcesses()) {
            live.put(process.identity(), process);
        }
    }

    /** Identidad simulada estable: PID + arranque + {@code offsetSeconds}. */
    public static ProcessIdentity identityOf(long pid, long offsetSeconds) {
        return ProcessIdentity.of(pid, ProcessIdentity.toFileTime(BOOT_TIME.plusSeconds(offsetSeconds)));
    }

    /** Identidad del proceso que reutiliza {@value #REUSED_PID}. */
    public static ProcessIdentity reusedNewIdentity() {
        return identityOf(REUSED_PID, Duration.ofDays(1).toSeconds());
    }

    private static List<MockProcess> sampleProcesses() {
        return List.of(
                process(4120L, "chrome.exe", "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe", 512, 380, Behavior.NORMAL),
                process(9021L, "Code.exe", "C:\\Users\\usuario\\AppData\\Local\\Programs\\Microsoft VS Code\\Code.exe", 420, 300, Behavior.NORMAL),
                process(1023L, "Discord.exe", "C:\\Users\\usuario\\AppData\\Local\\Discord\\app-1.0.9165\\Discord.exe", 260, 190, Behavior.NORMAL),
                process(5544L, "steam.exe", "C:\\Program Files (x86)\\Steam\\steam.exe", 340, 210, Behavior.NORMAL),
                process(3456L, "java.exe", "C:\\Program Files\\Eclipse Adoptium\\jdk-22.0.2.9-hotspot\\bin\\java.exe", 610, 540, Behavior.NORMAL),
                process(4402L, "explorer.exe", "C:\\Windows\\explorer.exe", 150, 90, Behavior.NORMAL),
                // Private Bytes por encima del Working Set: memoria comprometida no residente.
                process(1500L, "ms-teams.exe", "C:\\Program Files\\WindowsApps\\MSTeams_24004.1307.2669.7070_x64__8wekyb3d8bbwe\\ms-teams.exe", 300, 450, Behavior.NORMAL),
                process(HUNG_PROCESS_PID, HUNG_PROCESS_NAME, "C:\\Users\\usuario\\Desktop\\" + HUNG_PROCESS_NAME, 75, 50, Behavior.HUNG),
                process(NO_WINDOW_PID, NO_WINDOW_NAME, "C:\\Program Files\\Sync\\" + NO_WINDOW_NAME, 40, 30, Behavior.NO_WINDOW),
                process(LEAKY_PID, LEAKY_NAME, "C:\\Users\\usuario\\Desktop\\" + LEAKY_NAME, 120, 110, Behavior.LEAKY),
                process(SHORT_LIVED_PID, SHORT_LIVED_NAME, "C:\\Users\\usuario\\Downloads\\" + SHORT_LIVED_NAME, 20, 15, Behavior.SHORT_LIVED),
                process(REUSED_PID, REUSED_OLD_NAME, "C:\\Program Files\\Updater\\" + REUSED_OLD_NAME, 35, 25, Behavior.REUSED_OLD),
                process(PROTECTED_PID, PROTECTED_NAME, "C:\\Program Files\\Guard\\" + PROTECTED_NAME, 90, 70, Behavior.PROTECTED),
                process(ELEVATED_PID, ELEVATED_NAME, null, 0, 0, Behavior.ACCESS_DENIED));
    }

    private static MockProcess process(long pid, String name, String path, long workingSetMib, long privateMib,
                                       Behavior behavior) {
        return new MockProcess(identityOf(pid, pid % 3600), name, path, workingSetMib * MIB, privateMib * MIB, behavior);
    }

    @Override
    public synchronized ProcessInventory enumerateProcesses() {
        enumerations++;
        advanceLifecycle();
        List<ProcessEntry> entries = new ArrayList<>(live.size());
        for (MockProcess process : live.values()) {
            entries.add(new ProcessEntry(process.identity(), process.name(), pathOf(process), memoryOf(process)));
        }
        return new ProcessInventory(entries, SKIPPED_COUNT, clock.instant(), nanoTime.getAsLong());
    }

    /** Termina procesos de vida corta y simula la reutilización de PID. */
    private void advanceLifecycle() {
        if (enumerations > SHORT_LIVED_ENUMERATIONS) {
            live.values().removeIf(process -> process.behavior() == Behavior.SHORT_LIVED);
        }
        if (enumerations > REUSE_AFTER_ENUMERATIONS
                && live.values().removeIf(process -> process.behavior() == Behavior.REUSED_OLD)) {
            ProcessIdentity reused = reusedNewIdentity();
            live.put(reused, new MockProcess(reused, REUSED_NEW_NAME,
                    "C:\\Users\\usuario\\Downloads\\" + REUSED_NEW_NAME, 45 * MIB, 30 * MIB, Behavior.NORMAL));
        }
    }

    private static ImagePath pathOf(MockProcess process) {
        return process.behavior() == Behavior.ACCESS_DENIED
                ? ImagePath.unavailable(ProcessQueryStatus.ACCESS_DENIED)
                : ImagePath.of(process.path());
    }

    private MemoryMetrics memoryOf(MockProcess process) {
        if (process.behavior() == Behavior.ACCESS_DENIED) {
            return MemoryMetrics.unavailable(MetricState.ACCESS_DENIED);
        }
        if (process.behavior() == Behavior.LEAKY) {
            long growth = enumerations * LEAK_BYTES_PER_ENUMERATION;
            long noise = Math.round(random.nextGaussian() * 64 * 1024);
            return new MemoryMetrics(process.workingSetBase() + growth + noise, process.privateBase() + growth + noise);
        }
        return new MemoryMetrics(jitter(process.workingSetBase()), jitter(process.privateBase()));
    }

    /**
     * Elimina el proceso de la tabla simulada. Solo debe invocarse por acción manual del usuario.
     */
    @Override
    public synchronized TerminationAttempt terminate(ProcessIdentity identity) {
        Objects.requireNonNull(identity, "identity no puede ser null");
        if (identity.pid().value() <= 4) {
            return new TerminationAttempt(identity, TerminationOutcome.PROTECTED_PROCESS, 0);
        }
        MockProcess process = live.get(identity);
        if (process == null) {
            return new TerminationAttempt(identity, goneOutcome(identity), 0);
        }
        return switch (process.behavior()) {
            case PROTECTED -> new TerminationAttempt(identity, TerminationOutcome.PROTECTED_PROCESS, 0);
            case ACCESS_DENIED -> new TerminationAttempt(identity, TerminationOutcome.ACCESS_DENIED, 5);
            default -> {
                live.remove(identity);
                yield new TerminationAttempt(identity, TerminationOutcome.TERMINATED, 0);
            }
        };
    }

    @Override
    public synchronized ProcessSampleResult sample(ProcessIdentity identity) {
        Objects.requireNonNull(identity, "identity no puede ser null");
        MockProcess process = live.get(identity);
        if (process == null) {
            return ProcessSampleResult.failed(identity, goneQueryStatus(identity), 0);
        }
        MemoryMetrics memory = memoryOf(process);
        return ProcessSampleResult.of(new ProcessSample(identity, nanoTime.getAsLong(),
                memory.workingSetReading(), memory.privateBytesReading()));
    }

    @Override
    public synchronized ResponsivenessResult checkResponsiveness(ProcessIdentity identity) {
        Objects.requireNonNull(identity, "identity no puede ser null");
        MockProcess process = live.get(identity);
        if (process == null) {
            ResponsivenessOutcome outcome = goneQueryStatus(identity) == ProcessQueryStatus.PID_REUSED
                    ? ResponsivenessOutcome.PID_REUSED : ResponsivenessOutcome.PROCESS_EXITED;
            return ResponsivenessResult.of(identity, outcome);
        }
        return switch (process.behavior()) {
            case HUNG -> new ResponsivenessResult(identity, ResponsivenessOutcome.HUNG, 1, 1, 0);
            case NO_WINDOW, PROTECTED -> new ResponsivenessResult(identity, ResponsivenessOutcome.NO_WINDOW, 0, 0, 0);
            case ACCESS_DENIED -> ResponsivenessResult.of(identity, ResponsivenessOutcome.ACCESS_DENIED);
            default -> new ResponsivenessResult(identity, ResponsivenessOutcome.RESPONDING, 1, 0, 0);
        };
    }

    /** Identidad viva con el mismo PID, si existe (PID reutilizado). */
    public synchronized Optional<ProcessIdentity> currentIdentityOf(long pid) {
        return live.keySet().stream().filter(identity -> identity.pid().value() == pid).findFirst();
    }

    private TerminationOutcome goneOutcome(ProcessIdentity identity) {
        return goneQueryStatus(identity) == ProcessQueryStatus.PID_REUSED
                ? TerminationOutcome.PID_REUSED : TerminationOutcome.PROCESS_EXITED;
    }

    private ProcessQueryStatus goneQueryStatus(ProcessIdentity identity) {
        boolean pidInUse = live.keySet().stream().anyMatch(other -> other.pid().equals(identity.pid()));
        return pidInUse ? ProcessQueryStatus.PID_REUSED : ProcessQueryStatus.PROCESS_EXITED;
    }

    private long jitter(long baseBytes) {
        double factor = 1.0 + random.nextGaussian() * MEMORY_JITTER;
        return Math.max(0L, Math.round(baseBytes * factor));
    }
}
