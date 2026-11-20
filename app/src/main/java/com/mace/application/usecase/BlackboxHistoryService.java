package com.mace.application.usecase;

import com.mace.domain.model.BlackboxEntry;
import com.mace.domain.model.BlackboxSession;
import com.mace.domain.model.BlackboxSessionState;
import com.mace.domain.model.BlackboxSessionSummary;
import com.mace.domain.model.BlackboxStats;
import com.mace.domain.port.out.BlackboxHistoryPort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Caso de uso de solo lectura para explorar sesiones anteriores de la caja negra desde la UI sin
 * depender de la infraestructura. Marca como {@link BlackboxSessionState#IN_PROGRESS} el archivo
 * que la sesión actual sigue escribiendo. Las lecturas pueden ser lentas: llamar fuera del hilo
 * de JavaFX.
 */
public final class BlackboxHistoryService {

    private final BlackboxHistoryPort historyPort;
    private final Supplier<BlackboxStats> currentStats;

    /**
     * @param currentStats estado del sink actual (p. ej. {@code blackbox::stats}) para reconocer
     *                     el archivo en curso
     */
    public BlackboxHistoryService(BlackboxHistoryPort historyPort, Supplier<BlackboxStats> currentStats) {
        this.historyPort = Objects.requireNonNull(historyPort, "historyPort no puede ser null");
        this.currentStats = Objects.requireNonNull(currentStats, "currentStats no puede ser null");
    }

    /** Sesiones de la más reciente a la más antigua. */
    public List<BlackboxSessionSummary> listSessions() {
        BlackboxStats stats = currentStats.get();
        return historyPort.listSessions().stream()
                .map(summary -> markCurrent(summary, stats))
                .sorted(Comparator.comparing(BlackboxSessionSummary::id).reversed())
                .toList();
    }

    public BlackboxSession readSession(String sessionId) {
        BlackboxSession session = historyPort.readSession(Objects.requireNonNull(sessionId, "sessionId no puede ser null"));
        return session.withSummary(markCurrent(session.summary(), currentStats.get()));
    }

    /**
     * Registros más recientes primero, hasta {@code max}.
     *
     * @param onlyEventsAndGaps {@code true} para ocultar las muestras periódicas
     */
    public static List<BlackboxEntry> recentEntries(BlackboxSession session, int max, boolean onlyEventsAndGaps) {
        Objects.requireNonNull(session, "session no puede ser null");
        if (max < 0) {
            throw new IllegalArgumentException("max no puede ser negativo: " + max);
        }
        List<BlackboxEntry> entries = session.entries();
        List<BlackboxEntry> recent = new ArrayList<>(Math.min(max, entries.size()));
        for (int index = entries.size() - 1; index >= 0 && recent.size() < max; index--) {
            BlackboxEntry entry = entries.get(index);
            if (!onlyEventsAndGaps || !entry.kind().isSample()) {
                recent.add(entry);
            }
        }
        return List.copyOf(recent);
    }

    /** @return {@code true} si {@code summary} es el archivo que la sesión actual está escribiendo */
    public static boolean isCurrent(BlackboxSessionSummary summary, BlackboxStats stats) {
        if (!stats.enabled() || stats.location().isBlank()) {
            return false;
        }
        String location = stats.location();
        String id = summary.id();
        return location.equals(id) || location.endsWith("\\" + id) || location.endsWith("/" + id);
    }

    private static BlackboxSessionSummary markCurrent(BlackboxSessionSummary summary, BlackboxStats stats) {
        return isCurrent(summary, stats) ? summary.withState(BlackboxSessionState.IN_PROGRESS) : summary;
    }
}
