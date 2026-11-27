package com.mace.application.usecase;

import com.mace.domain.model.BlackboxEntry;
import com.mace.domain.model.BlackboxEntryKind;
import com.mace.domain.model.BlackboxEventType;
import com.mace.domain.model.BlackboxSession;
import com.mace.domain.model.BlackboxSessionState;
import com.mace.domain.model.BlackboxSessionSummary;
import com.mace.domain.model.BlackboxStats;
import com.mace.domain.port.out.BlackboxHistoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackboxHistoryServiceTest {

    private static final String OLD = "mace-blackbox-20261020T100000Z-1-000001.mbbx";
    private static final String CURRENT = "mace-blackbox-20261021T120000Z-2-000003.mbbx";

    private static BlackboxSessionSummary summary(String id, BlackboxSessionState state) {
        return new BlackboxSessionSummary(id, "C:\\datos\\" + id, false, true, state, 5, 5, 0, 0);
    }

    private static BlackboxEntry entry(long sequence, BlackboxEntryKind kind) {
        BlackboxEventType event = kind == BlackboxEntryKind.EVENT ? BlackboxEventType.HANG_ALERT : null;
        return new BlackboxEntry(sequence, kind, event, Instant.EPOCH, 0, 0, "registro " + sequence);
    }

    private final AtomicReference<BlackboxStats> stats =
            new AtomicReference<>(new BlackboxStats(true, 10, 0, 0, "C:\\datos\\" + CURRENT));

    private final BlackboxHistoryPort port = new BlackboxHistoryPort() {
        @Override
        public List<BlackboxSessionSummary> listSessions() {
            return List.of(summary(OLD, BlackboxSessionState.CLOSED), summary(CURRENT, BlackboxSessionState.INTERRUPTED));
        }

        @Override
        public BlackboxSession readSession(String sessionId) {
            return new BlackboxSession(summary(sessionId, BlackboxSessionState.INTERRUPTED), List.of(
                    entry(1, BlackboxEntryKind.EVENT), entry(2, BlackboxEntryKind.CPU_SAMPLE),
                    entry(3, BlackboxEntryKind.GAP), entry(4, BlackboxEntryKind.MEMORY_SAMPLE)));
        }
    };

    private final BlackboxHistoryService service = new BlackboxHistoryService(port, stats::get);

    @Test
    void ordenaDeLaMasRecienteALaMasAntiguaYMarcaLaSesionEnCurso() {
        List<BlackboxSessionSummary> sessions = service.listSessions();

        assertEquals(List.of(CURRENT, OLD), sessions.stream().map(BlackboxSessionSummary::id).toList());
        assertEquals(BlackboxSessionState.IN_PROGRESS, sessions.get(0).state(),
                "el archivo que sigue escribiéndose no se muestra como interrumpido");
        assertEquals(BlackboxSessionState.CLOSED, sessions.get(1).state());
    }

    @Test
    void sinCajaNegraActivaNingunArchivoEstaEnCurso() {
        stats.set(BlackboxStats.disabled());

        assertEquals(BlackboxSessionState.INTERRUPTED, service.readSession(CURRENT).summary().state());
    }

    @Test
    void losRegistrosRecientesVanPrimeroYPuedenOcultarLasMuestras() {
        BlackboxSession session = service.readSession(CURRENT);

        List<BlackboxEntry> all = BlackboxHistoryService.recentEntries(session, 3, false);
        List<BlackboxEntry> eventsAndGaps = BlackboxHistoryService.recentEntries(session, 10, true);

        assertEquals(List.of(4L, 3L, 2L), all.stream().map(BlackboxEntry::sequence).toList());
        assertEquals(List.of(3L, 1L), eventsAndGaps.stream().map(BlackboxEntry::sequence).toList());
        assertTrue(eventsAndGaps.get(0).isGap());
        assertEquals("Programa sin respuesta", eventsAndGaps.get(1).typeLabel());
    }
}
