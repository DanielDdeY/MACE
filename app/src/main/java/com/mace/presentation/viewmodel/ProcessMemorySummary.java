package com.mace.presentation.viewmodel;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Resumen acotado para el gráfico circular de Working Set de procesos listados. */
public final class ProcessMemorySummary {

    public record Slice(String label, double mebibytes) { }

    private ProcessMemorySummary() { }

    public static List<Slice> topSlices(List<ProcessRow> rows, int limit) {
        Objects.requireNonNull(rows, "rows no puede ser null");
        if (limit <= 0) throw new IllegalArgumentException("limit debe ser positivo");
        List<ProcessRow> ranked = rows.stream().filter(ProcessRow::hasWorkingSet)
                .filter(row -> row.workingSetMb() > 0 && Double.isFinite(row.workingSetMb()))
                .sorted(Comparator.comparingDouble(ProcessRow::workingSetMb).reversed()
                        .thenComparingLong(ProcessRow::pid)).toList();
        List<Slice> visible = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(limit, ranked.size()); i++) {
            ProcessRow row = ranked.get(i);
            visible.add(new Slice(row.name() + " (PID " + row.pid() + ")", row.workingSetMb()));
        }
        double rest = ranked.stream().skip(limit).mapToDouble(ProcessRow::workingSetMb).sum();
        if (rest > 0) visible.add(new Slice("Otros", rest));
        return List.copyOf(visible);
    }

    public static double totalListedMebibytes(List<ProcessRow> rows) {
        Objects.requireNonNull(rows, "rows no puede ser null");
        return rows.stream().filter(ProcessRow::hasWorkingSet)
                .mapToDouble(ProcessRow::workingSetMb).filter(Double::isFinite).sum();
    }
}
