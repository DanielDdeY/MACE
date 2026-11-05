package com.mace.presentation.viewmodel;

import com.mace.domain.model.MemoryMetrics;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.ProcessIdentity;
import com.mace.infrastructure.mock.MockProcessAdapter;
import javafx.collections.FXCollections;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessPresentationTest {

    @Test
    void seleccionSigueLaIdentidadTrasRefrescarFiltrarYOrdenar() {
        var entries = new MockProcessAdapter().enumerateProcesses().processes();
        ProcessRow first = row(entries.get(0));
        ProcessRow second = row(entries.get(1));
        var source = FXCollections.observableArrayList(first, second);
        ProcessTableViewModel table = new ProcessTableViewModel(source);
        table.rememberSelection(second);
        ProcessRow refreshed = new ProcessRow(second.identity(), second.name(), second.imagePath(),
                new MemoryMetrics(12L * 1024 * 1024, 13L * 1024 * 1024));
        source.setAll(refreshed, first);
        assertSame(refreshed, table.selectedVisibleRow().orElseThrow());
        table.getSortedProcesses().setComparator(Comparator.comparing(ProcessRow::name).reversed());
        assertSame(refreshed, table.selectedVisibleRow().orElseThrow());

        table.searchTextProperty().set("ninguna-coincidencia");
        assertTrue(table.selectedVisibleRow().isEmpty());
        table.searchTextProperty().set(Long.toString(second.pid()));
        assertSame(refreshed, table.selectedVisibleRow().orElseThrow());
        AtomicReference<ProcessRow> requested = new AtomicReference<>();
        table.setOnTerminateRequested(requested::set);
        table.requestTerminate(refreshed);
        assertSame(refreshed, requested.get());
    }

    @Test
    void graficoCircularLimitaProcesosAgrupaOtrosYExcluyeMemoriaNoDisponible() {
        var example = new MockProcessAdapter().enumerateProcesses().processes().getFirst();
        List<ProcessRow> rows = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            rows.add(new ProcessRow(ProcessIdentity.of(20_000 + index,
                    ProcessIdentity.toFileTime(Instant.parse("2026-01-01T00:00:00Z"))),
                    "p" + index, example.imagePath(),
                    new MemoryMetrics((index + 1L) * 1024 * 1024, 0)));
        }
        rows.add(new ProcessRow(ProcessIdentity.of(30_000,
                ProcessIdentity.toFileTime(Instant.parse("2026-01-01T00:00:00Z"))),
                "sin-lectura", example.imagePath(), MemoryMetrics.unavailable(MetricState.ACCESS_DENIED)));
        List<ProcessMemorySummary.Slice> slices = ProcessMemorySummary.topSlices(rows, 8);
        assertEquals(9, slices.size());
        assertEquals("Otros", slices.getLast().label());
        assertEquals(3.0, slices.getLast().mebibytes());
        assertEquals(55.0, ProcessMemorySummary.totalListedMebibytes(rows));
        assertFalse(slices.stream().anyMatch(slice -> slice.label().contains("sin-lectura")));
    }

    private static ProcessRow row(com.mace.domain.model.ProcessEntry entry) {
        return new ProcessRow(entry.identity(), entry.name(), entry.imagePath(), entry.memory());
    }
}
