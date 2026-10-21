package com.mace.presentation.viewmodel;

import com.mace.application.usecase.ListProcessesService;
import com.mace.application.usecase.MonitoringService;
import com.mace.application.usecase.PollSystemMetricsService;
import com.mace.application.usecase.ProcessInspectionService;
import com.mace.application.usecase.TerminateProcessService;
import com.mace.domain.model.AlertLevel;
import com.mace.domain.model.BlackboxStats;
import com.mace.domain.model.GpuAdapterId;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.HangAlert;
import com.mace.domain.model.LeakAlert;
import com.mace.domain.model.MemorySeries;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.MonitoringSnapshot;
import com.mace.domain.model.ProcessInventory;
import com.mace.domain.model.ResponsivenessOutcome;
import com.mace.domain.model.ResponsivenessResult;
import com.mace.domain.model.TelemetrySnapshot;
import com.mace.domain.model.ThermalAlert;
import com.mace.domain.port.out.MonitoringPublisherPort;
import com.mace.domain.port.out.TelemetryPublisherPort;
import com.mace.domain.service.HangDetector;
import com.mace.domain.service.MemoryLeakDetector;
import com.mace.domain.service.SystemProcessGuard;
import com.mace.domain.service.ThermalAlertEvaluator;
import com.mace.infrastructure.mock.MockProcessAdapter;
import com.mace.infrastructure.mock.MockScenario;
import com.mace.infrastructure.mock.MockSensorsAdapter;
import com.mace.infrastructure.telemetry.NoOpBlackboxSink;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainDashboardPresentationTest {

    @Test
    void conservaAmbasGpuPorLuidAunqueCambieElOrdenYNoDuplicaMuestrasEnRefrescoDeProcesos() {
        try (Fixture fixture = new Fixture()) {
            HardwareSnapshot first = new MockSensorsAdapter(MockScenario.DUAL_GPU).fetchHardwareSnapshot();
            fixture.publish(snapshot(1, 1, first));
            assertEquals(2, fixture.viewModel.gpuCards().size());
            var hottest = first.gpus().stream().max(java.util.Comparator.comparingDouble(
                    gpu -> gpu.temperatureCelsius().value())).orElseThrow();
            assertEquals(hottest.adapter().displayName(), fixture.viewModel.gpuSummaryNameProperty().get());
            assertEquals(hottest.temperatureCelsius().value(),
                    fixture.viewModel.gpuSummaryTemperatureDisplayProperty().get().value().orElseThrow());
            GpuCardViewModel intel = fixture.viewModel.gpuCards().get(0);
            GpuCardViewModel nvidia = fixture.viewModel.gpuCards().get(1);
            GpuAdapterId intelId = intel.id();
            assertEquals(MetricState.UNSUPPORTED, intel.fanRpmProperty().get().state());
            assertEquals(1, intel.temperatureHistory().points().size());
            assertEquals(1, fixture.viewModel.cpuTemperaturePoints().points().size());

            HardwareSnapshot reordered = new HardwareSnapshot(first.timestamp(), first.cpu(),
                    List.of(first.gpus().get(1), first.gpus().get(0)), first.memory());
            fixture.publish(snapshot(2, 2, reordered));
            assertSame(nvidia, fixture.viewModel.gpuCards().get(0));
            assertSame(intel, fixture.viewModel.gpuCards().get(1));
            assertEquals(intelId, fixture.viewModel.gpuCards().get(1).id());
            assertEquals(2, intel.temperatureHistory().points().size());

            ProcessInventory inventory = fixture.processes.enumerateProcesses();
            fixture.publish(new MonitoringSnapshot(3, 2, reordered, List.of(), Optional.of(inventory),
                    List.of(), List.of(), Map.of(), BlackboxStats.disabled()));
            assertEquals(2, intel.temperatureHistory().points().size());
            assertEquals(2, fixture.viewModel.cpuTemperaturePoints().points().size());
            assertTrue(fixture.viewModel.processInventoryAvailableProperty().get());
            assertEquals(inventory.processes().size(), fixture.viewModel.getProcesses().size());
            assertEquals(3, fixture.viewModel.monitoringSequenceProperty().get());
            assertEquals(2, fixture.viewModel.hardwareSequenceProperty().get());
            assertThrows(UnsupportedOperationException.class, () -> fixture.viewModel.gpuCards().clear());
        }
    }

    @Test
    void ausenciaDeGpuYDeSensorSeConservanComoEstadosDistintos() {
        try (Fixture fixture = new Fixture()) {
            HardwareSnapshot none = new MockSensorsAdapter(MockScenario.NO_GPU).fetchHardwareSnapshot();
            fixture.publish(snapshot(1, 1, none));
            assertTrue(fixture.viewModel.gpuCards().isEmpty());
            assertEquals(MetricState.NO_DEVICE,
                    fixture.viewModel.gpuSummaryTemperatureDisplayProperty().get().state());

            HardwareSnapshot noSensor = new MockSensorsAdapter(MockScenario.GPU_NO_SENSOR).fetchHardwareSnapshot();
            fixture.publish(snapshot(2, 2, noSensor));
            assertEquals(1, fixture.viewModel.gpuCards().size());
            MetricDisplay temperature = fixture.viewModel.gpuCards().get(0).temperatureProperty().get();
            assertEquals(MetricState.NO_PROVIDER, temperature.state());
            assertEquals(MetricState.NO_PROVIDER,
                    fixture.viewModel.gpuSummaryTemperatureDisplayProperty().get().state());
            assertFalse(temperature.value().isPresent());
            assertTrue(temperature.text().startsWith("N/D"));
            assertFalse(fixture.viewModel.gpuCards().getFirst().temperatureProperty().get().isValid());
        }
    }

    @Test
    void cpuAmdMantieneTemperaturaYUsoAunqueNoHayaPotenciaYGpuReaparecidaTieneNuevoHistorial() {
        try (Fixture fixture = new Fixture()) {
            HardwareSnapshot amd = new MockSensorsAdapter(MockScenario.AMD_NO_POWER).fetchHardwareSnapshot();
            fixture.publish(snapshot(1, 1, amd));
            assertEquals("AMD", fixture.viewModel.cpuVendorProperty().get());
            assertTrue(fixture.viewModel.cpuTemperatureDisplayProperty().get().isValid());
            assertTrue(fixture.viewModel.cpuUsageDisplayProperty().get().isValid());
            assertEquals(MetricState.NO_PROVIDER, fixture.viewModel.cpuPowerDisplayProperty().get().state());
            assertTrue(fixture.viewModel.memoryUsedDisplayProperty().get().isValid());
            GpuCardViewModel original = fixture.viewModel.gpuCards().getFirst();

            HardwareSnapshot removed = new HardwareSnapshot(amd.timestamp(), amd.cpu(), List.of(), amd.memory());
            fixture.publish(snapshot(2, 2, removed));
            assertTrue(fixture.viewModel.gpuCards().isEmpty());
            fixture.publish(snapshot(3, 3, amd));
            assertEquals(1, fixture.viewModel.gpuCards().size());
            assertFalse(original == fixture.viewModel.gpuCards().getFirst());
            assertEquals(1, fixture.viewModel.gpuCards().getFirst().temperatureHistory().points().size());
        }
    }

    @Test
    void conservaAlertasCajaNegraYNoAplicaEventosAtrasadosNiEnColaTrasCerrar() {
        try (Fixture fixture = new Fixture()) {
            HardwareSnapshot hardware = new MockSensorsAdapter(MockScenario.NORMAL).fetchHardwareSnapshot();
            GpuAdapterId id = hardware.gpus().get(0).adapterId();
            BlackboxStats stats = new BlackboxStats(true, 4, 1, 1, "C:\\mace\\blackbox");
            MonitoringSnapshot current = new MonitoringSnapshot(2, 2, hardware,
                    List.of(new ThermalAlert(AlertLevel.WARNING, "GPU", id, 77f, Instant.now())),
                    Optional.empty(), List.of(), List.of(), Map.of(), stats);

            fixture.queueInsteadOfDispatching();
            fixture.publisher.publish(current);
            fixture.publisher.publish(snapshot(1, 1, hardware));
            fixture.flush();
            assertEquals(2, fixture.viewModel.monitoringSequenceProperty().get());
            assertEquals(1, fixture.viewModel.alerts().size());
            assertEquals(id, fixture.viewModel.alerts().get(0).gpuId().orElseThrow());
            assertEquals(stats, fixture.viewModel.blackboxStatsProperty().get());
            assertFalse(fixture.viewModel.processInventoryAvailableProperty().get());

            fixture.publisher.publish(snapshot(3, 3, hardware));
            fixture.viewModel.close();
            fixture.flush();
            assertEquals(2, fixture.viewModel.monitoringSequenceProperty().get());
        }
    }

    @Test
    void publicaAlertasDeTresTiposYResultadosDeVentanaSinCerrarProcesos() {
        try (Fixture fixture = new Fixture()) {
            HardwareSnapshot hardware = new MockSensorsAdapter(MockScenario.NORMAL).fetchHardwareSnapshot();
            var process = fixture.processes.enumerateProcesses().processes().getFirst();
            Instant now = Instant.parse("2026-01-01T00:00:00Z");
            var response = new ResponsivenessResult(process.identity(), ResponsivenessOutcome.HUNG, 1, 1, 0);
            fixture.publish(new MonitoringSnapshot(1, 1, hardware,
                    List.of(new ThermalAlert(AlertLevel.CRITICAL, "CPU", 90f, now)),
                    Optional.empty(),
                    List.of(new LeakAlert(process.identity(), process.name(), MemorySeries.WORKING_SET,
                            1024 * 1024, 0.95, 10, Duration.ofMinutes(1), now)),
                    List.of(new HangAlert(process.identity(), process.name(), 1, 1, now)),
                    Map.of(process.identity(), response), BlackboxStats.disabled()));
            assertEquals(List.of(AlertItemViewModel.Kind.THERMAL, AlertItemViewModel.Kind.MEMORY_LEAK,
                    AlertItemViewModel.Kind.HUNG_PROCESS),
                    fixture.viewModel.alerts().stream().map(AlertItemViewModel::kind).toList());
            assertEquals(response, fixture.viewModel.responsivenessProperty().get().get(process.identity()));
            assertTrue(fixture.processes.currentIdentityOf(process.pid().value()).isPresent());
        }
    }

    private static MonitoringSnapshot snapshot(long sequence, long hardwareSequence, HardwareSnapshot hardware) {
        return new MonitoringSnapshot(sequence, hardwareSequence, hardware, List.of(), Optional.empty(),
                List.of(), List.of(), Map.of(), BlackboxStats.disabled());
    }

    private static final class Fixture implements AutoCloseable {
        final ManualPublisher publisher = new ManualPublisher();
        final MockProcessAdapter processes = new MockProcessAdapter();
        final NoOpBlackboxSink blackbox = new NoOpBlackboxSink();
        final TelemetryPublisherPort legacy = new TelemetryPublisherPort() {
            @Override public void publish(TelemetrySnapshot snapshot) { }
            @Override public void subscribe(Flow.Subscriber<? super TelemetrySnapshot> subscriber) { }
        };
        final MonitoringService monitor = new MonitoringService(
                new PollSystemMetricsService(new MockSensorsAdapter(), legacy, blackbox,
                        new ThermalAlertEvaluator()), new ListProcessesService(processes),
                new ProcessInspectionService(processes), new MemoryLeakDetector(), new HangDetector(),
                blackbox, publisher);
        final List<Runnable> queue = new ArrayList<>();
        boolean deferred;
        final MainDashboardViewModel viewModel = new MainDashboardViewModel(monitor,
                new TerminateProcessService(processes, new SystemProcessGuard()), publisher,
                "Datos simulados", null, task -> {
                    if (deferred) {
                        queue.add(task);
                    } else {
                        task.run();
                    }
                });

        void publish(MonitoringSnapshot snapshot) { publisher.publish(snapshot); }
        void queueInsteadOfDispatching() { deferred = true; }
        void flush() {
            List<Runnable> pending = List.copyOf(queue);
            queue.clear();
            pending.forEach(Runnable::run);
        }
        @Override public void close() {
            viewModel.close();
            monitor.close();
        }
    }

    private static final class ManualPublisher implements MonitoringPublisherPort {
        private Flow.Subscriber<? super MonitoringSnapshot> subscriber;
        private boolean cancelled;

        @Override public void publish(MonitoringSnapshot snapshot) {
            if (!cancelled) {
                subscriber.onNext(snapshot);
            }
        }
        @Override public void subscribeMonitoring(Flow.Subscriber<? super MonitoringSnapshot> next) {
            subscriber = next;
            next.onSubscribe(new Flow.Subscription() {
                @Override public void request(long n) { }
                @Override public void cancel() { cancelled = true; }
            });
        }
    }
}
