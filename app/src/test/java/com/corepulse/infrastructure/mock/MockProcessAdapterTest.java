package com.corepulse.infrastructure.mock;

import com.corepulse.domain.model.MemoryMetrics;
import com.corepulse.domain.model.ProcessEntry;
import com.corepulse.domain.model.ProcessId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockProcessAdapterTest {

    @Test
    void listaProcesosDeEjemploValidosParaElDominio() {
        List<ProcessEntry> processes = new MockProcessAdapter(1L).listUserlandProcesses();

        assertFalse(processes.isEmpty());
        for (ProcessEntry p : processes) {
            assertTrue(p.name().toLowerCase().endsWith(".exe"), p.name());
            assertTrue(p.fullPath().endsWith(p.name()), p.fullPath());
            assertTrue(p.memory().workingSetBytes() >= 0);
            assertTrue(p.memory().privateBytes() <= p.memory().workingSetBytes(), "private <= working set");
        }
        assertEquals(processes.size(), processes.stream().map(ProcessEntry::pid).distinct().count(), "PIDs unicos");
    }

    @Test
    void terminarEliminaElProcesoDeLaTablaSimulada() {
        MockProcessAdapter adapter = new MockProcessAdapter(1L);
        ProcessEntry victim = adapter.listUserlandProcesses().get(0);

        assertTrue(adapter.terminateProcess(victim.pid()));

        assertTrue(adapter.listUserlandProcesses().stream().noneMatch(p -> p.pid().equals(victim.pid())));
        assertFalse(adapter.terminateProcess(victim.pid()), "segunda terminacion del mismo PID devuelve false");
    }

    @Test
    void terminarUnProcesoInexistenteDevuelveFalse() {
        assertFalse(new MockProcessAdapter(1L).terminateProcess(new ProcessId(999_999L)));
    }

    @Test
    void nuncaTerminaLosPidsProtegidosDelSistema() {
        MockProcessAdapter adapter = new MockProcessAdapter(List.of(
                entry(0L, "System Idle Process"),
                entry(4L, "System"),
                entry(1234L, "app.exe")), 1L);

        assertFalse(adapter.terminateProcess(new ProcessId(0L)));
        assertFalse(adapter.terminateProcess(new ProcessId(4L)));
        assertEquals(3, adapter.listUserlandProcesses().size());
        assertTrue(adapter.terminateProcess(new ProcessId(1234L)));
    }

    @Test
    void soloElProcesoCongeladoNoResponde() {
        MockProcessAdapter adapter = new MockProcessAdapter(1L);

        assertFalse(adapter.isWindowResponsive(new ProcessId(MockProcessAdapter.HUNG_PROCESS_PID)));
        for (ProcessEntry p : adapter.listUserlandProcesses()) {
            if (p.pid().value() != MockProcessAdapter.HUNG_PROCESS_PID) {
                assertTrue(adapter.isWindowResponsive(p.pid()), p.name());
            } else {
                assertEquals(MockProcessAdapter.HUNG_PROCESS_NAME, p.name());
            }
        }
    }

    @Test
    void laMemoriaFluctuaPeroLaIdentidadDeLosProcesosSeMantiene() {
        MockProcessAdapter adapter = new MockProcessAdapter(99L);
        List<ProcessEntry> first = adapter.listUserlandProcesses();
        List<ProcessEntry> second = adapter.listUserlandProcesses();

        assertEquals(first.size(), second.size());
        boolean anyMemoryChanged = false;
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).pid(), second.get(i).pid());
            assertEquals(first.get(i).name(), second.get(i).name());
            anyMemoryChanged |= !first.get(i).memory().equals(second.get(i).memory());
        }
        assertTrue(anyMemoryChanged, "la memoria simulada debe variar entre consultas");
    }

    @Test
    void esDeterministaConLaMismaSemilla() {
        assertEquals(new MockProcessAdapter(5L).listUserlandProcesses(),
                new MockProcessAdapter(5L).listUserlandProcesses());
    }

    private static ProcessEntry entry(long pid, String name) {
        return new ProcessEntry(new ProcessId(pid), name, "C:\\" + name, new MemoryMetrics(1024L, 512L));
    }
}
