package com.mace.application.usecase;

import com.mace.domain.model.BlackboxEvent;
import com.mace.domain.model.CpuReading;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.HangAlert;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.LeakAlert;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.MonitoringSnapshot;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessInventory;
import com.mace.domain.model.ProcessSample;
import com.mace.domain.model.ResponsivenessOutcome;
import com.mace.domain.model.ResponsivenessResult;
import com.mace.domain.model.SystemMemoryReading;
import com.mace.domain.model.ThermalAlert;
import com.mace.domain.port.out.BlackboxSinkPort;
import com.mace.domain.port.out.MonitoringPublisherPort;
import com.mace.domain.service.HangDetector;
import com.mace.domain.service.MemoryLeakDetector;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Único bucle de sondeo periódico de MACE (1 Hz por defecto). Cada ciclo:
 * <ol>
 *   <li>sondea el hardware una sola vez ({@link PollSystemMetricsService#pollHardware()});</li>
 *   <li>enumera procesos y alimenta el detector de fugas;</li>
 *   <li>consulta ventanas de los procesos que tocan según {@link HangDetector};</li>
 *   <li>registra en la caja negra las alertas nuevas y vacía el anillo temporal;</li>
 *   <li>publica un {@link MonitoringSnapshot} para dashboard y Micro-HUD.</li>
 * </ol>
 *
 * <p>Sustituye al planificador que hoy vive en la presentación: no deben coexistir dos bucles.
 * Todas las alertas solo informan; ningún camino de esta clase termina ni suspende procesos.</p>
 */
public final class MonitoringService implements AutoCloseable {

    public static final Duration DEFAULT_PERIOD = Duration.ofSeconds(1);

    private static final Logger LOG = System.getLogger(MonitoringService.class.getName());

    private final PollSystemMetricsService pollService;
    private final ListProcessesService listProcessesService;
    private final ProcessInspectionService inspectionService;
    private final MemoryLeakDetector leakDetector;
    private final HangDetector hangDetector;
    private final BlackboxSinkPort blackbox;
    private final MonitoringPublisherPort publisher;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final Duration period;
    private final ScheduledExecutorService scheduler;

    private boolean started;
    private boolean closed;
    private long sequence;
    private long hardwareSequence;
    private HardwarePoll lastHardware;
    private MonitoringSnapshot latest;
    private Set<String> activeLeakKeys = Set.of();
    private Set<ProcessIdentity> activeHangs = Set.of();
    private final ProcessSamplingPolicy samplingPolicy;
    private boolean fullSampleTaken;
    private long lastFullSampleNanos;
    private final Map<ProcessIdentity, Long> lastAlertedSampleNanos = new HashMap<>();

    public MonitoringService(PollSystemMetricsService pollService, ListProcessesService listProcessesService,
                             ProcessInspectionService inspectionService, MemoryLeakDetector leakDetector,
                             HangDetector hangDetector, BlackboxSinkPort blackbox, MonitoringPublisherPort publisher) {
        this(pollService, listProcessesService, inspectionService, leakDetector, hangDetector, blackbox, publisher,
                Clock.systemUTC(), System::nanoTime, DEFAULT_PERIOD);
    }

    public MonitoringService(PollSystemMetricsService pollService, ListProcessesService listProcessesService,
                             ProcessInspectionService inspectionService, MemoryLeakDetector leakDetector,
                             HangDetector hangDetector, BlackboxSinkPort blackbox, MonitoringPublisherPort publisher,
                             Clock clock, LongSupplier nanoTime, Duration period) {
        this(pollService, listProcessesService, inspectionService, leakDetector, hangDetector, blackbox, publisher,
                clock, nanoTime, period, ProcessSamplingPolicy.defaults());
    }

    /**
     * @param samplingPolicy frecuencia con la que las muestras de procesos se copian a la caja negra
     */
    public MonitoringService(PollSystemMetricsService pollService, ListProcessesService listProcessesService,
                             ProcessInspectionService inspectionService, MemoryLeakDetector leakDetector,
                             HangDetector hangDetector, BlackboxSinkPort blackbox, MonitoringPublisherPort publisher,
                             Clock clock, LongSupplier nanoTime, Duration period, ProcessSamplingPolicy samplingPolicy) {
        this.samplingPolicy = Objects.requireNonNull(samplingPolicy, "samplingPolicy no puede ser null");
        this.pollService = Objects.requireNonNull(pollService, "pollService no puede ser null");
        this.listProcessesService = Objects.requireNonNull(listProcessesService, "listProcessesService no puede ser null");
        this.inspectionService = Objects.requireNonNull(inspectionService, "inspectionService no puede ser null");
        this.leakDetector = Objects.requireNonNull(leakDetector, "leakDetector no puede ser null");
        this.hangDetector = Objects.requireNonNull(hangDetector, "hangDetector no puede ser null");
        this.blackbox = Objects.requireNonNull(blackbox, "blackbox no puede ser null");
        this.publisher = Objects.requireNonNull(publisher, "publisher no puede ser null");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime no puede ser null");
        this.period = Objects.requireNonNull(period, "period no puede ser null");
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("period debe ser positivo: " + period);
        }
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mace-monitoring");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Inicia el sondeo periódico. Idempotente. */
    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("MonitoringService ya fue cerrado");
        }
        if (started) {
            return;
        }
        started = true;
        scheduler.scheduleAtFixedRate(this::tickSafely, 0, period.toNanos(), TimeUnit.NANOSECONDS);
    }

    public synchronized boolean isRunning() {
        return started && !closed;
    }

    /**
     * Ejecuta un ciclo completo de forma síncrona y publica su resultado. Lo usa el planificador;
     * es público para pruebas y para el preview.
     */
    public synchronized MonitoringSnapshot tick() {
        HardwarePoll hardware = pollHardwareOrFallback();
        hardwareSequence++;
        lastHardware = hardware;
        Optional<ProcessInventory> inventory = enumerateProcesses();
        return publish(hardware, inventory);
    }

    /**
     * Refresca solo la lista de procesos (p. ej. tras una terminación manual) sin otro sondeo de
     * hardware: el snapshot publicado repite {@code hardwareSequence}. Se ejecuta en el hilo del
     * monitor para no solaparse con el ciclo periódico.
     */
    public void requestProcessRefresh() {
        try {
            scheduler.execute(() -> {
                try {
                    refreshProcessesNow();
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "No se pudo refrescar la lista de procesos", e);
                }
            });
        } catch (RejectedExecutionException e) {
            LOG.log(Level.DEBUG, "Refresco ignorado: el monitor está cerrado");
        }
    }

    /** Refresco síncrono de procesos; requiere al menos un ciclo previo. */
    public synchronized Optional<MonitoringSnapshot> refreshProcessesNow() {
        if (lastHardware == null || closed) {
            return Optional.empty();
        }
        return Optional.of(publish(lastHardware, enumerateProcesses()));
    }

    /** Último snapshot publicado. */
    public synchronized Optional<MonitoringSnapshot> latest() {
        return Optional.ofNullable(latest);
    }

    /**
     * Detiene el bucle, espera al ciclo en curso y vacía la caja negra. No cierra los
     * adaptadores: la composición los cierra después.
     */
    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        scheduler.shutdownNow();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.log(Level.WARNING, "El ciclo de monitoreo no terminó en 5 s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            blackbox.flush();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo vaciar la caja negra al cerrar", e);
        }
    }

    private void tickSafely() {
        try {
            tick();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Error durante el ciclo de monitoreo", e);
        }
    }

    private MonitoringSnapshot publish(HardwarePoll hardware, Optional<ProcessInventory> inventory) {
        Instant now = clock.instant();
        List<LeakAlert> leakAlerts = inventory.map(leakDetector::observe).orElseGet(() -> leakDetector.evaluateAll(now));
        inventory.ifPresent(this::checkResponsiveness);
        List<HangAlert> hangAlerts = hangDetector.activeAlerts(now);
        inventory.ifPresent(found -> recordProcessSamples(found, leakAlerts, hangAlerts));
        recordNewAlerts(leakAlerts, hangAlerts);
        try {
            blackbox.flush();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo vaciar la caja negra", e);
        }

        MonitoringSnapshot snapshot = new MonitoringSnapshot(++sequence, hardwareSequence, hardware.snapshot(),
                hardware.thermalAlerts(), inventory, leakAlerts, hangAlerts, hangDetector.latest(), blackbox.stats());
        latest = snapshot;
        try {
            publisher.publish(snapshot);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo publicar el snapshot de monitoreo", e);
        }
        return snapshot;
    }

    private HardwarePoll pollHardwareOrFallback() {
        try {
            return pollService.pollHardware();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Falló el sondeo de hardware; se publican métricas READ_FAILED", e);
            long nanos = nanoTime.getAsLong();
            List<GpuReading> gpus = lastHardware == null ? List.of() : lastHardware.snapshot().gpus().stream()
                    .map(gpu -> GpuReading.unavailable(gpu.adapter(), MetricState.READ_FAILED, nanos))
                    .toList();
            HardwareSnapshot failed = new HardwareSnapshot(clock.instant(),
                    CpuReading.unavailable(MetricState.READ_FAILED, nanos), gpus,
                    SystemMemoryReading.unavailable(MetricState.READ_FAILED, nanos));
            return new HardwarePoll(failed, List.<ThermalAlert>of());
        }
    }

    private Optional<ProcessInventory> enumerateProcesses() {
        try {
            return Optional.of(listProcessesService.inventory());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Falló la enumeración de procesos", e);
            return Optional.empty();
        }
    }

    private void checkResponsiveness(ProcessInventory inventory) {
        for (ProcessIdentity identity : hangDetector.dueForCheck(inventory, nanoTime.getAsLong())) {
            ResponsivenessResult result;
            try {
                result = inspectionService.checkResponsiveness(identity);
            } catch (RuntimeException e) {
                LOG.log(Level.DEBUG, "No se pudo consultar ventanas de " + identity, e);
                result = ResponsivenessResult.of(identity, ResponsivenessOutcome.ERROR);
            }
            hangDetector.record(result, nanoTime.getAsLong());
        }
    }

    private void recordNewAlerts(List<LeakAlert> leakAlerts, List<HangAlert> hangAlerts) {
        Set<String> leakKeys = new HashSet<>();
        for (LeakAlert alert : leakAlerts) {
            String key = alert.identity() + "/" + alert.series();
            leakKeys.add(key);
            if (!activeLeakKeys.contains(key)) {
                safeRecord(BlackboxEvent.leak(alert));
            }
        }
        activeLeakKeys = leakKeys;

        Set<ProcessIdentity> hangs = new HashSet<>();
        for (HangAlert alert : hangAlerts) {
            hangs.add(alert.identity());
            if (!activeHangs.contains(alert.identity())) {
                safeRecord(BlackboxEvent.hang(alert));
            }
        }
        activeHangs = hangs;
    }

    private void safeRecord(BlackboxEvent event) {
        try {
            blackbox.recordEvent(event);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo registrar el evento en la caja negra", e);
        }
    }

    /**
     * Persiste las mismas lecturas del inventario usadas para las tendencias, sin otro sondeo:
     * todos los procesos según {@link ProcessSamplingPolicy#allProcessesInterval()} y los que
     * tienen alerta activa según {@link ProcessSamplingPolicy#alertedProcessInterval()}.
     */
    private void recordProcessSamples(ProcessInventory inventory, List<LeakAlert> leakAlerts,
                                      List<HangAlert> hangAlerts) {
        long now = inventory.monotonicNanos();
        boolean full = !fullSampleTaken
                || now - lastFullSampleNanos >= samplingPolicy.allProcessesInterval().toNanos();
        Set<ProcessIdentity> alerted = new HashSet<>();
        leakAlerts.forEach(alert -> alerted.add(alert.identity()));
        hangAlerts.forEach(alert -> alerted.add(alert.identity()));
        lastAlertedSampleNanos.keySet().retainAll(alerted);

        for (var entry : inventory.processes()) {
            ProcessIdentity identity = entry.identity();
            boolean isAlerted = alerted.contains(identity);
            Long last = lastAlertedSampleNanos.get(identity);
            boolean alertDue = isAlerted
                    && (last == null || now - last >= samplingPolicy.alertedProcessInterval().toNanos());
            if (!full && !alertDue) {
                continue;
            }
            try {
                blackbox.record(ProcessSample.fromInventory(entry, now));
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "No se pudo registrar la muestra de " + identity, e);
            }
            if (isAlerted) {
                lastAlertedSampleNanos.put(identity, now);
            }
        }
        if (full) {
            fullSampleTaken = true;
            lastFullSampleNanos = now;
        }
    }
}
