package com.corepulse.infrastructure.mock;

import com.corepulse.domain.model.MemoryMetrics;
import com.corepulse.domain.model.ProcessEntry;
import com.corepulse.domain.model.ProcessId;
import com.corepulse.domain.port.out.ProcessLifecyclePort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Implementación simulada de {@link ProcessLifecyclePort}: una tabla de procesos de usuario
 * plausibles cuyo consumo de memoria fluctúa ligeramente en cada consulta.
 *
 * <p>Permite ejercitar de extremo a extremo el flujo "listar → confirmar → finalizar" sin
 * tocar procesos reales: {@link #terminateProcess(ProcessId)} elimina la fila de la tabla
 * simulada (respetando los PID protegidos 0 y 4) y {@link #isWindowResponsive(ProcessId)}
 * reporta como colgado únicamente al proceso {@value #HUNG_PROCESS_NAME}
 * (PID {@value #HUNG_PROCESS_PID}) para que la UI pueda mostrar ese estado.</p>
 *
 * <p>Es seguro para uso concurrente (sondeo en hilo de fondo + acciones desde el hilo de UI).</p>
 */
public final class MockProcessAdapter implements ProcessLifecyclePort {

    /** PID del proceso simulado que siempre aparece como "no responde". */
    public static final long HUNG_PROCESS_PID = 7777L;
    /** Nombre del proceso simulado que siempre aparece como "no responde". */
    public static final String HUNG_PROCESS_NAME = "frozen-app.exe";

    private static final Set<Long> PROTECTED_PIDS = Set.of(0L, 4L);
    private static final long MIB = 1024L * 1024L;
    private static final double MEMORY_JITTER = 0.03;

    private final Map<Long, ProcessEntry> baseline = new LinkedHashMap<>();
    private final Random random;

    /** Tabla de ejemplo ({@link #sampleProcesses()}) con semilla aleatoria. */
    public MockProcessAdapter() {
        this(sampleProcesses(), System.nanoTime());
    }

    public MockProcessAdapter(long seed) {
        this(sampleProcesses(), seed);
    }

    /**
     * @param initialProcesses procesos con los que arranca la tabla simulada
     * @param seed             semilla para la fluctuación de memoria; misma semilla ⇒ misma secuencia
     */
    public MockProcessAdapter(List<ProcessEntry> initialProcesses, long seed) {
        Objects.requireNonNull(initialProcesses, "initialProcesses no puede ser null");
        for (ProcessEntry entry : initialProcesses) {
            baseline.put(entry.pid().value(), Objects.requireNonNull(entry, "la lista no admite null"));
        }
        this.random = new Random(seed);
    }

    /** Conjunto de procesos de usuario verosímiles en un equipo Windows de desarrollo. */
    public static List<ProcessEntry> sampleProcesses() {
        return List.of(
                entry(4120L, "chrome.exe",
                        "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe", 512, 380),
                entry(9021L, "Code.exe",
                        "C:\\Users\\usuario\\AppData\\Local\\Programs\\Microsoft VS Code\\Code.exe", 420, 300),
                entry(1023L, "Discord.exe",
                        "C:\\Users\\usuario\\AppData\\Local\\Discord\\app-1.0.9165\\Discord.exe", 260, 190),
                entry(5544L, "steam.exe",
                        "C:\\Program Files (x86)\\Steam\\steam.exe", 340, 210),
                entry(6310L, "Spotify.exe",
                        "C:\\Users\\usuario\\AppData\\Roaming\\Spotify\\Spotify.exe", 230, 160),
                entry(2210L, "msedge.exe",
                        "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe", 380, 250),
                entry(3456L, "java.exe",
                        "C:\\Program Files\\Eclipse Adoptium\\jdk-22.0.2.9-hotspot\\bin\\java.exe", 610, 540),
                entry(8891L, "MACE.exe",
                        "C:\\Program Files\\MACE\\MACE.exe", 180, 120),
                entry(4402L, "explorer.exe",
                        "C:\\Windows\\explorer.exe", 150, 90),
                entry(7788L, "OneDrive.exe",
                        "C:\\Users\\usuario\\AppData\\Local\\Microsoft\\OneDrive\\OneDrive.exe", 95, 60),
                entry(1500L, "ms-teams.exe",
                        "C:\\Program Files\\WindowsApps\\MSTeams_24004.1307.2669.7070_x64__8wekyb3d8bbwe\\ms-teams.exe",
                        450, 320),
                entry(HUNG_PROCESS_PID, HUNG_PROCESS_NAME,
                        "C:\\Users\\usuario\\Desktop\\" + HUNG_PROCESS_NAME, 75, 50)
        );
    }

    private static ProcessEntry entry(long pid, String name, String path, long workingSetMib, long privateMib) {
        return new ProcessEntry(new ProcessId(pid), name, path,
                new MemoryMetrics(workingSetMib * MIB, privateMib * MIB));
    }

    /** @return copia inmutable de la tabla simulada con memoria ligeramente fluctuante */
    @Override
    public synchronized List<ProcessEntry> listUserlandProcesses() {
        List<ProcessEntry> snapshot = new ArrayList<>(baseline.size());
        for (ProcessEntry base : baseline.values()) {
            long workingSet = jitter(base.memory().workingSetBytes());
            long privateBytes = Math.min(jitter(base.memory().privateBytes()), workingSet);
            snapshot.add(new ProcessEntry(base.pid(), base.name(), base.fullPath(),
                    new MemoryMetrics(workingSet, privateBytes)));
        }
        return List.copyOf(snapshot);
    }

    /**
     * Elimina el proceso de la tabla simulada. Solo debe invocarse por acción manual del usuario.
     *
     * @return {@code true} si el proceso existía y fue eliminado; {@code false} si no existe o
     *         es un PID protegido del sistema
     */
    @Override
    public synchronized boolean terminateProcess(ProcessId pid) {
        Objects.requireNonNull(pid, "pid no puede ser null");
        if (PROTECTED_PIDS.contains(pid.value())) {
            return false;
        }
        return baseline.remove(pid.value()) != null;
    }

    @Override
    public boolean isWindowResponsive(ProcessId pid) {
        Objects.requireNonNull(pid, "pid no puede ser null");
        return pid.value() != HUNG_PROCESS_PID;
    }

    private long jitter(long baseBytes) {
        double factor = 1.0 + random.nextGaussian() * MEMORY_JITTER;
        return Math.max(0L, Math.round(baseBytes * factor));
    }
}
