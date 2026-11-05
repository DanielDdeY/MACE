package com.mace.infrastructure.mock;

import com.mace.domain.model.MetricState;
import com.mace.domain.model.ProcessEntry;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessInventory;
import com.mace.domain.model.ProcessQueryStatus;
import com.mace.domain.model.ResponsivenessOutcome;
import com.mace.domain.model.TerminationOutcome;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockProcessAdapterTest {

    private final AtomicLong nanos = new AtomicLong();
    private final MockProcessAdapter adapter = new MockProcessAdapter(5L,
            Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC), () -> nanos.addAndGet(1_000_000_000L));

    private static ProcessIdentity identity(long pid) {
        return MockProcessAdapter.identityOf(pid, pid % 3600);
    }

    private ProcessInventory enumerate(int times) {
        ProcessInventory last = null;
        for (int i = 0; i < times; i++) {
            last = adapter.enumerateProcesses();
        }
        return last;
    }

    @Test
    void cadaProcesoTieneIdentidadCompleta() {
        ProcessInventory inventory = adapter.enumerateProcesses();

        assertTrue(inventory.processes().size() > 10);
        assertEquals(MockProcessAdapter.SKIPPED_COUNT, inventory.skippedCount());
        assertTrue(inventory.processes().stream().allMatch(entry -> entry.identity().creationTime100nsUtc() != 0));
    }

    @Test
    void colgadoSinVentanaYRespondiendoSonResultadosDistintos() {
        assertEquals(ResponsivenessOutcome.HUNG,
                adapter.checkResponsiveness(identity(MockProcessAdapter.HUNG_PROCESS_PID)).outcome());
        assertEquals(ResponsivenessOutcome.NO_WINDOW,
                adapter.checkResponsiveness(identity(MockProcessAdapter.NO_WINDOW_PID)).outcome());
        assertEquals(ResponsivenessOutcome.RESPONDING, adapter.checkResponsiveness(identity(4120)).outcome());
    }

    @Test
    void unProcesoDeVidaCortaTerminaYSeInformaComoFinalizado() {
        ProcessIdentity shortLived = identity(MockProcessAdapter.SHORT_LIVED_PID);
        assertTrue(enumerate(MockProcessAdapter.SHORT_LIVED_ENUMERATIONS).find(shortLived).isPresent());

        ProcessInventory after = adapter.enumerateProcesses();

        assertTrue(after.find(shortLived).isEmpty());
        assertEquals(ResponsivenessOutcome.PROCESS_EXITED, adapter.checkResponsiveness(shortLived).outcome());
        assertEquals(TerminationOutcome.PROCESS_EXITED, adapter.terminate(shortLived).outcome());
    }

    @Test
    void unPidReutilizadoRechazaLaIdentidadAnterior() {
        ProcessIdentity old = identity(MockProcessAdapter.REUSED_PID);
        ProcessInventory inventory = enumerate(MockProcessAdapter.REUSE_AFTER_ENUMERATIONS + 1);

        ProcessEntry reused = inventory.processes().stream()
                .filter(entry -> entry.pid().value() == MockProcessAdapter.REUSED_PID).findFirst().orElseThrow();
        assertEquals(MockProcessAdapter.REUSED_NEW_NAME, reused.name());
        assertFalse(reused.identity().equals(old));
        assertEquals(TerminationOutcome.PID_REUSED, adapter.terminate(old).outcome());
        assertEquals(ProcessQueryStatus.PID_REUSED, adapter.sample(old).status());
        assertTrue(adapter.enumerateProcesses().find(reused.identity()).isPresent(), "el proceso nuevo no se toca");
    }

    @Test
    void protegidoYSinPermisosRechazanLaTerminacion() {
        assertEquals(TerminationOutcome.PROTECTED_PROCESS,
                adapter.terminate(identity(MockProcessAdapter.PROTECTED_PID)).outcome());
        assertEquals(TerminationOutcome.ACCESS_DENIED,
                adapter.terminate(identity(MockProcessAdapter.ELEVATED_PID)).outcome());
        assertEquals(TerminationOutcome.PROTECTED_PROCESS, adapter.terminate(ProcessIdentity.of(4, 1)).outcome());
    }

    @Test
    void sinPermisosLaRutaYLaMemoriaNoSeInventan() {
        ProcessEntry elevated = adapter.enumerateProcesses().find(identity(MockProcessAdapter.ELEVATED_PID)).orElseThrow();

        assertEquals(ProcessQueryStatus.ACCESS_DENIED, elevated.imagePath().status());
        assertEquals(MetricState.ACCESS_DENIED, elevated.memory().workingSetReading().state());
    }

    @Test
    void terminarUnProcesoNormalLoQuitaDeLaTabla() {
        ProcessIdentity chrome = identity(4120);

        assertEquals(TerminationOutcome.TERMINATED, adapter.terminate(chrome).outcome());
        assertTrue(adapter.enumerateProcesses().find(chrome).isEmpty());
        assertEquals(TerminationOutcome.PROCESS_EXITED, adapter.terminate(chrome).outcome());
    }

    @Test
    void elProcesoConFugaCreceDeFormaSostenida() {
        ProcessIdentity leaky = identity(MockProcessAdapter.LEAKY_PID);
        long first = adapter.enumerateProcesses().find(leaky).orElseThrow().memory().privateBytes();
        long later = enumerate(10).find(leaky).orElseThrow().memory().privateBytes();

        assertTrue(later - first > 10L * 1024 * 1024);
    }

    @Test
    void laMismaSemillaProduceLaMismaMemoria() {
        MockProcessAdapter a = new MockProcessAdapter(9L);
        MockProcessAdapter b = new MockProcessAdapter(9L);

        assertEquals(a.enumerateProcesses().processes(), b.enumerateProcesses().processes());
    }
}
