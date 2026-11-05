package com.mace.domain.service;

import com.mace.domain.model.HangAlert;
import com.mace.domain.model.ProcessEntry;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessInventory;
import com.mace.domain.model.ResponsivenessOutcome;
import com.mace.domain.model.ResponsivenessResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Seguimiento de la capacidad de respuesta por identidad: decide qué procesos consultar en cada
 * ciclo y conserva el último resultado. {@code RESPONDING}, {@code HUNG}, {@code NO_WINDOW},
 * {@code PROCESS_EXITED} y {@code ERROR} se mantienen distintos.
 *
 * <p>Solo genera {@link HangAlert}; nunca cierra ni suspende procesos. Seguro para uso
 * concurrente.</p>
 */
public final class HangDetector {

    private final HangDetectionPolicy policy;
    private final Map<ProcessIdentity, Tracked> tracked = new HashMap<>();

    public HangDetector() {
        this(HangDetectionPolicy.defaults());
    }

    public HangDetector(HangDetectionPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy no puede ser null");
    }

    /**
     * Olvida procesos que ya no están en el inventario y devuelve los que toca consultar, los
     * más atrasados primero, hasta {@code maxChecksPerCycle}.
     */
    public synchronized List<ProcessIdentity> dueForCheck(ProcessInventory inventory, long nowNanos) {
        Objects.requireNonNull(inventory, "inventory no puede ser null");
        Set<ProcessIdentity> live = new HashSet<>();
        for (ProcessEntry entry : inventory.processes()) {
            live.add(entry.identity());
            tracked.computeIfAbsent(entry.identity(), identity -> new Tracked()).name = entry.name();
        }
        tracked.keySet().retainAll(live);

        List<Map.Entry<ProcessIdentity, Tracked>> due = new ArrayList<>();
        for (Map.Entry<ProcessIdentity, Tracked> entry : tracked.entrySet()) {
            Tracked state = entry.getValue();
            if (state.last == null || nowNanos - state.checkedAtNanos >= intervalFor(state.last.outcome())) {
                due.add(entry);
            }
        }
        due.sort(Comparator.comparingLong((Map.Entry<ProcessIdentity, Tracked> entry) ->
                entry.getValue().last == null ? Long.MIN_VALUE : entry.getValue().checkedAtNanos));
        return due.stream().limit(policy.maxChecksPerCycle()).map(Map.Entry::getKey).toList();
    }

    /** Registra el resultado de una consulta. */
    public synchronized void record(ResponsivenessResult result, long nowNanos) {
        Objects.requireNonNull(result, "result no puede ser null");
        Tracked state = tracked.computeIfAbsent(result.identity(), identity -> new Tracked());
        state.last = result;
        state.checkedAtNanos = nowNanos;
        if (result.outcome() == ResponsivenessOutcome.PROCESS_EXITED
                || result.outcome() == ResponsivenessOutcome.PID_REUSED) {
            tracked.remove(result.identity());
        }
    }

    /** Último resultado conocido por identidad. */
    public synchronized Map<ProcessIdentity, ResponsivenessResult> latest() {
        Map<ProcessIdentity, ResponsivenessResult> latest = new HashMap<>();
        tracked.forEach((identity, state) -> {
            if (state.last != null) {
                latest.put(identity, state.last);
            }
        });
        return Map.copyOf(latest);
    }

    /** Alertas de los procesos cuyo último resultado es {@code HUNG}. */
    public synchronized List<HangAlert> activeAlerts(Instant now) {
        List<HangAlert> alerts = new ArrayList<>();
        tracked.forEach((identity, state) -> {
            if (state.last != null && state.last.isHung()) {
                alerts.add(new HangAlert(identity, state.name, state.last.windowsHung(),
                        state.last.windowsExamined(), now));
            }
        });
        alerts.sort(Comparator.comparingLong(alert -> alert.identity().pid().value()));
        return List.copyOf(alerts);
    }

    private long intervalFor(ResponsivenessOutcome outcome) {
        return switch (outcome) {
            case RESPONDING, HUNG -> policy.windowedInterval().toNanos();
            default -> policy.noWindowInterval().toNanos();
        };
    }

    private static final class Tracked {
        private ResponsivenessResult last;
        private long checkedAtNanos;
        private String name;
    }
}
