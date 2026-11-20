package com.mace.domain.service;

import com.mace.domain.model.LeakAlert;
import com.mace.domain.model.LongMetricReading;
import com.mace.domain.model.MemorySeries;
import com.mace.domain.model.ProcessEntry;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessInventory;
import com.mace.domain.model.ProcessSample;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Detector de posibles fugas de memoria. Retiene hasta {@code maxSamples} muestras por
 * <strong>identidad completa</strong> y analiza Working Set y Private Bytes por separado con
 * una {@link LeakCalculationStrategy}.
 *
 * <ul>
 *   <li>Un PID reutilizado tiene otra fecha de creación: empieza una serie nueva, nunca se une a
 *       la del proceso anterior.</li>
 *   <li>Las lecturas no válidas se descartan en su serie; los huecos de tiempo se respetan porque
 *       la regresión usa la marca monotónica real de cada muestra.</li>
 *   <li>Los procesos que desaparecen del inventario se olvidan.</li>
 * </ul>
 *
 * <p>Solo informa: nunca cierra ni suspende procesos. Seguro para uso concurrente.</p>
 */
public final class MemoryLeakDetector {

    private final LeakCalculationStrategy strategy;
    private final LeakDetectionPolicy policy;
    private final Map<ProcessIdentity, Track> tracks = new LinkedHashMap<>();

    public MemoryLeakDetector() {
        this(new LinearRegressionLeakStrategy(), LeakDetectionPolicy.defaults());
    }

    public MemoryLeakDetector(LeakCalculationStrategy strategy, LeakDetectionPolicy policy) {
        this.strategy = Objects.requireNonNull(strategy, "strategy no puede ser null");
        this.policy = Objects.requireNonNull(policy, "policy no puede ser null");
    }

    public LeakDetectionPolicy policy() {
        return policy;
    }

    /**
     * Incorpora una muestra por cada proceso del inventario, olvida los que ya no aparecen y
     * devuelve las alertas activas.
     */
    public synchronized List<LeakAlert> observe(ProcessInventory inventory) {
        Objects.requireNonNull(inventory, "inventory no puede ser null");
        Set<ProcessIdentity> live = new HashSet<>();
        for (ProcessEntry entry : inventory.processes()) {
            live.add(entry.identity());
            add(ProcessSample.fromInventory(entry, inventory.monotonicNanos()), entry.name());
        }
        tracks.keySet().retainAll(live);
        return evaluateAll(inventory.timestamp());
    }

    /**
     * Incorpora una muestra individual (p. ej. de {@code CP_SampleProcessV2}).
     *
     * @return {@code true} si se aceptó; {@code false} si no tenía ninguna serie válida o llegó
     *         antes del intervalo mínimo respecto de la anterior
     */
    public synchronized boolean observe(ProcessSample sample, String processName) {
        return add(Objects.requireNonNull(sample, "sample no puede ser null"), processName);
    }

    /** Alertas activas de todos los procesos seguidos. */
    public synchronized List<LeakAlert> evaluateAll(Instant now) {
        List<LeakAlert> alerts = new ArrayList<>();
        for (Map.Entry<ProcessIdentity, Track> entry : tracks.entrySet()) {
            alerts.addAll(evaluate(entry.getKey(), entry.getValue(), now));
        }
        return List.copyOf(alerts);
    }

    /** Alertas activas de un proceso (vacía si no se sigue). */
    public synchronized List<LeakAlert> evaluate(ProcessIdentity identity, Instant now) {
        Track track = tracks.get(identity);
        return track == null ? List.of() : evaluate(identity, track, now);
    }

    /** Tendencia actual de una serie, para mostrarla en detalle. */
    public synchronized Optional<TrendEstimate> trend(ProcessIdentity identity, MemorySeries series) {
        Track track = tracks.get(identity);
        return track == null ? Optional.empty() : strategy.estimate(points(track, series));
    }

    public synchronized List<ProcessSample> samplesOf(ProcessIdentity identity) {
        Track track = tracks.get(identity);
        return track == null ? List.of() : List.copyOf(track.samples);
    }

    public synchronized void forget(ProcessIdentity identity) {
        tracks.remove(identity);
    }

    public synchronized int trackedCount() {
        return tracks.size();
    }

    private boolean add(ProcessSample sample, String processName) {
        if (!sample.workingSet().isValid() && !sample.privateBytes().isValid()) {
            return false;
        }
        Track track = tracks.computeIfAbsent(sample.identity(), identity -> new Track());
        track.name = processName;
        ProcessSample last = track.samples.peekLast();
        if (last != null && sample.monotonicNanos() - last.monotonicNanos() < policy.minSampleInterval().toNanos()) {
            return false;
        }
        track.samples.addLast(sample);
        while (track.samples.size() > policy.maxSamples()) {
            track.samples.removeFirst();
        }
        return true;
    }

    private List<LeakAlert> evaluate(ProcessIdentity identity, Track track, Instant now) {
        List<LeakAlert> alerts = new ArrayList<>(2);
        for (MemorySeries series : MemorySeries.values()) {
            List<SeriesPoint> points = points(track, series);
            if (points.size() < policy.minSamples()) {
                continue;
            }
            strategy.estimate(points)
                    .filter(policy::indicatesLeak)
                    .ifPresent(estimate -> alerts.add(new LeakAlert(identity, track.name, series,
                            estimate.slopeBytesPerSecond(), estimate.rSquared(), estimate.sampleCount(),
                            Duration.ofNanos(estimate.spanNanos()), now)));
        }
        return alerts;
    }

    private static List<SeriesPoint> points(Track track, MemorySeries series) {
        List<SeriesPoint> points = new ArrayList<>(track.samples.size());
        for (ProcessSample sample : track.samples) {
            LongMetricReading reading = series.readFrom(sample);
            if (reading.isValid()) {
                points.add(new SeriesPoint(sample.monotonicNanos(), reading.value()));
            }
        }
        return points;
    }

    private static final class Track {
        private final Deque<ProcessSample> samples = new ArrayDeque<>();
        private String name;
    }
}
