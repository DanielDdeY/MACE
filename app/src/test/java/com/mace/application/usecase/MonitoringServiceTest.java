package com.mace.application.usecase;

import com.mace.domain.model.BlackboxEvent;
import com.mace.domain.model.BlackboxEventType;
import com.mace.domain.model.BlackboxStats;
import com.mace.domain.model.GpuAdapter;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.MonitoringSnapshot;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessInventory;
import com.mace.domain.model.ProcessSample;
import com.mace.domain.model.ProcessSampleResult;
import com.mace.domain.model.ResponsivenessOutcome;
import com.mace.domain.model.ResponsivenessResult;
import com.mace.domain.model.TelemetrySnapshot;
import com.mace.domain.model.TerminationAttempt;
import com.mace.domain.port.out.BlackboxSinkPort;
import com.mace.domain.port.out.HardwareSensorsPort;
import com.mace.domain.port.out.MonitoringPublisherPort;
import com.mace.domain.port.out.ProcessLifecyclePort;
import com.mace.domain.port.out.TelemetryPublisherPort;
import com.mace.domain.service.HangDetector;
import com.mace.domain.service.MemoryLeakDetector;
import com.mace.domain.service.ThermalAlertEvaluator;
import com.mace.infrastructure.mock.MockProcessAdapter;
import com.mace.infrastructure.mock.MockScenario;
import com.mace.infrastructure.mock.MockSensorsAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ciclo completo con los adaptadores simulados: un sondeo por ciclo, alertas de fuga y cuelgue
 * y ninguna terminación automática.
 */
class MonitoringServiceTest {

    private final AtomicLong nanos = new AtomicLong();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC);
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final RecordingBlackbox blackbox = new RecordingBlackbox();

    private CountingSensors sensors;
    private RecordingProcesses processes;
    private MonitoringService monitor;

    @BeforeEach
    void setUp() {
        sensors = new CountingSensors(new MockSensorsAdapter(MockScenario.THERMAL_CRITICAL, 1, clock, nanos::get));
        processes = new RecordingProcesses(new MockProcessAdapter(1, clock, () -> nanos.addAndGet(1_000_000_000L)));
        monitor = newMonitor(sensors, processes);
    }

    @AfterEach
    void tearDown() {
        if (monitor != null) {
            monitor.close();
        }
    }

    private MonitoringService newMonitor(HardwareSensorsPort sensorsPort, ProcessLifecyclePort processPort) {
        return newMonitor(sensorsPort, processPort, ProcessSamplingPolicy.defaults());
    }

    private MonitoringService newMonitor(HardwareSensorsPort sensorsPort, ProcessLifecyclePort processPort,
                                         ProcessSamplingPolicy policy) {
        PollSystemMetricsService poll = new PollSystemMetricsService(sensorsPort, new NoOpLegacyPublisher(), blackbox,
                new ThermalAlertEvaluator());
        return new MonitoringService(poll, new ListProcessesService(processPort), new ProcessInspectionService(processPort),
                new MemoryLeakDetector(), new HangDetector(), blackbox, publisher, clock, nanos::get, Duration.ofSeconds(1),
                policy);
    }

    private void useSamplingPolicy(Duration allProcesses, Duration alerted) {
        monitor.close();
        monitor = newMonitor(sensors, processes, new ProcessSamplingPolicy(allProcesses, alerted));
    }

    private static ProcessIdentity hungIdentity() {
        return MockProcessAdapter.identityOf(MockProcessAdapter.HUNG_PROCESS_PID, MockProcessAdapter.HUNG_PROCESS_PID % 3600);
    }

    @Test
    void cadaCicloSondeaUnaVezYPublicaUnSnapshotNumerado() {
        MonitoringSnapshot first = monitor.tick();
        MonitoringSnapshot second = monitor.tick();

        assertEquals(1, first.sequence());
        assertEquals(1, first.hardwareSequence());
        assertEquals(2, second.sequence());
        assertEquals(2, second.hardwareSequence());
        assertEquals(2, sensors.polls.get());
        assertEquals(List.of(first, second), publisher.snapshots);
        assertTrue(first.processes().isPresent());
    }

    @Test
    void persisteLasMuestrasDeProcesosDelMismoInventario() {
        MonitoringSnapshot snapshot = monitor.tick();

        ProcessInventory inventory = snapshot.processes().orElseThrow();
        assertEquals(inventory.processes().size(), blackbox.samples.size());
        assertEquals(inventory.monotonicNanos(), blackbox.samples.get(0).monotonicNanos());
        assertEquals(inventory.processes().get(0).identity(), blackbox.samples.get(0).identity());
        assertEquals(1, sensors.polls.get(), "registrar muestras no debe repetir el sondeo físico");
    }

    @Test
    void noGuardaTodosLosProcesosEnCadaCiclo() {
        int firstInventory = monitor.tick().processes().orElseThrow().processes().size();
        monitor.tick();
        monitor.tick();

        assertEquals(firstInventory, blackbox.samples.size(),
                "entre muestras completas (60 s) no se repite el inventario cada segundo");
    }

    @Test
    void vuelveAGuardarTodosLosProcesosTrasElIntervalo() {
        useSamplingPolicy(Duration.ofSeconds(3), Duration.ofSeconds(100));

        int first = monitor.tick().processes().orElseThrow().processes().size();   // t = 1 s: completa
        monitor.tick();                                                            // t = 2 s
        monitor.tick();                                                            // t = 3 s
        int fourth = monitor.tick().processes().orElseThrow().processes().size();  // t = 4 s: completa

        assertEquals(first + fourth, blackbox.samples.size());
    }

    @Test
    void losProcesosConAlertaSeGuardanMasSeguido() {
        useSamplingPolicy(Duration.ofSeconds(60), Duration.ofSeconds(2));

        MonitoringSnapshot first = monitor.tick();
        for (int i = 0; i < 4; i++) {
            monitor.tick();                                                        // t = 2..5 s
        }

        assertTrue(first.hangAlerts().stream().anyMatch(alert -> alert.identity().equals(hungIdentity())));
        long hungSamples = blackbox.samples.stream().filter(s -> s.identity().equals(hungIdentity())).count();
        long chromeSamples = blackbox.samples.stream()
                .filter(s -> s.identity().pid().value() == 4120L).count();
        assertEquals(3, hungSamples, "proceso colgado: t = 1, 3 y 5 s");
        assertEquals(1, chromeSamples, "proceso sin alerta: solo la muestra completa");
    }

    @Test
    void refrescarProcesosNoGeneraOtroSondeoDeHardware() {
        MonitoringSnapshot tick = monitor.tick();

        MonitoringSnapshot refresh = monitor.refreshProcessesNow().orElseThrow();

        assertEquals(1, sensors.polls.get(), "el HUD y las gráficas no reciben una muestra duplicada");
        assertEquals(tick.hardwareSequence(), refresh.hardwareSequence());
        assertSame(tick.hardware(), refresh.hardware());
        assertEquals(2, refresh.sequence());
    }

    @Test
    void alertasTermicasDeFugaYCuelgueNuncaTerminanProcesos() {
        MonitoringSnapshot last = null;
        for (int i = 0; i < 15; i++) {
            last = monitor.tick();
        }

        assertFalse(last.thermalAlerts().isEmpty(), "perfil THERMAL_CRITICAL");
        assertTrue(last.leakAlerts().stream().anyMatch(a -> a.processName().equals(MockProcessAdapter.LEAKY_NAME)));
        assertTrue(last.hangAlerts().stream().anyMatch(a -> a.processName().equals(MockProcessAdapter.HUNG_PROCESS_NAME)));
        assertEquals(0, processes.terminations.get(), "ninguna alerta termina procesos");
    }

    @Test
    void conservaLosEstadosDeVentanaDistintos() {
        MonitoringSnapshot snapshot = monitor.tick();

        assertEquals(ResponsivenessOutcome.HUNG, snapshot.responsiveness().get(
                MockProcessAdapter.identityOf(MockProcessAdapter.HUNG_PROCESS_PID, MockProcessAdapter.HUNG_PROCESS_PID % 3600)).outcome());
        assertEquals(ResponsivenessOutcome.NO_WINDOW, snapshot.responsiveness().get(
                MockProcessAdapter.identityOf(MockProcessAdapter.NO_WINDOW_PID, MockProcessAdapter.NO_WINDOW_PID % 3600)).outcome());
    }

    @Test
    void registraCadaAlertaNuevaUnaSolaVezEnLaCajaNegra() {
        for (int i = 0; i < 15; i++) {
            monitor.tick();
        }

        long hangEvents = blackbox.events.stream().filter(e -> e.type() == BlackboxEventType.HANG_ALERT).count();
        long leakEvents = blackbox.events.stream().filter(e -> e.type() == BlackboxEventType.LEAK_ALERT).count();
        assertEquals(1, hangEvents);
        assertEquals(2, leakEvents, "Working Set y Private Bytes");
        assertTrue(blackbox.flushes > 0);
    }

    @Test
    void unFalloDeHardwarePublicaMetricasReadFailedSinPerderLasGpu() {
        monitor.tick();
        sensors.failNext = true;

        MonitoringSnapshot failed = monitor.tick();

        assertEquals(MetricState.READ_FAILED, failed.hardware().cpu().temperatureCelsius().state());
        assertEquals(1, failed.hardware().gpus().size());
        assertFalse(failed.hardware().gpus().get(0).hasAnyValidMetric());
        assertTrue(failed.thermalAlerts().isEmpty());
    }

    @Test
    void unFalloAlEnumerarProcesosNoDetieneElCiclo() {
        RecordingProcesses broken = new RecordingProcesses(new MockProcessAdapter(1));
        broken.failEnumeration = true;
        monitor.close();
        monitor = newMonitor(sensors, broken);

        MonitoringSnapshot snapshot = monitor.tick();

        assertTrue(snapshot.processes().isEmpty());
        assertFalse(snapshot.thermalAlerts().isEmpty());
    }

    @Test
    void startEsIdempotenteYCloseImpideReiniciar() {
        monitor.start();
        monitor.start();
        assertTrue(monitor.isRunning());

        monitor.close();

        assertFalse(monitor.isRunning());
        assertThrows(IllegalStateException.class, monitor::start);
        assertTrue(monitor.refreshProcessesNow().isEmpty());
    }

    // ------------------------------------------------------------------

    private static final class CountingSensors implements HardwareSensorsPort {
        private final HardwareSensorsPort delegate;
        private final AtomicInteger polls = new AtomicInteger();
        private volatile boolean failNext;

        CountingSensors(HardwareSensorsPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public HardwareSnapshot fetchHardwareSnapshot() {
            polls.incrementAndGet();
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("sensor caído");
            }
            return delegate.fetchHardwareSnapshot();
        }

        @Override
        public List<GpuAdapter> listGpuAdapters() {
            return delegate.listGpuAdapters();
        }
    }

    /** Envoltorio del puerto que cuenta terminaciones (sin espiar clases finales). */
    private static final class RecordingProcesses implements ProcessLifecyclePort {
        private final ProcessLifecyclePort delegate;
        private final AtomicInteger terminations = new AtomicInteger();
        private volatile boolean failEnumeration;

        RecordingProcesses(ProcessLifecyclePort delegate) {
            this.delegate = delegate;
        }

        @Override
        public ProcessInventory enumerateProcesses() {
            if (failEnumeration) {
                throw new IllegalStateException("boom");
            }
            return delegate.enumerateProcesses();
        }

        @Override
        public TerminationAttempt terminate(ProcessIdentity identity) {
            terminations.incrementAndGet();
            return delegate.terminate(identity);
        }

        @Override
        public ProcessSampleResult sample(ProcessIdentity identity) {
            return delegate.sample(identity);
        }

        @Override
        public ResponsivenessResult checkResponsiveness(ProcessIdentity identity) {
            return delegate.checkResponsiveness(identity);
        }
    }

    private static final class RecordingPublisher implements MonitoringPublisherPort {
        private final List<MonitoringSnapshot> snapshots = new ArrayList<>();

        @Override
        public synchronized void publish(MonitoringSnapshot snapshot) {
            snapshots.add(snapshot);
        }

        @Override
        public void subscribeMonitoring(Flow.Subscriber<? super MonitoringSnapshot> subscriber) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class NoOpLegacyPublisher implements TelemetryPublisherPort {
        @Override
        public void publish(TelemetrySnapshot snapshot) {
        }

        @Override
        public void subscribe(Flow.Subscriber<? super TelemetrySnapshot> subscriber) {
        }
    }

    private static final class RecordingBlackbox implements BlackboxSinkPort {
        private final List<BlackboxEvent> events = new ArrayList<>();
        private final List<ProcessSample> samples = new ArrayList<>();
        private int flushes;

        @Override
        public void record(HardwareSnapshot snapshot) {
        }

        @Override
        public void record(ProcessSample sample) {
            samples.add(sample);
        }

        @Override
        public synchronized void recordEvent(BlackboxEvent event) {
            events.add(event);
        }

        @Override
        public synchronized void flush() {
            flushes++;
        }

        @Override
        public BlackboxStats stats() {
            return BlackboxStats.disabled();
        }
    }
}
