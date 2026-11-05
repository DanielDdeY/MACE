package com.mace.presentation.viewmodel;

import com.mace.application.usecase.ListProcessesService;
import com.mace.application.usecase.MonitoringService;
import com.mace.application.usecase.PollSystemMetricsService;
import com.mace.application.usecase.ProcessInspectionService;
import com.mace.application.usecase.TerminateProcessService;
import com.mace.application.usecase.TerminationStatus;
import com.mace.domain.model.ProcessEntry;
import com.mace.domain.service.HangDetector;
import com.mace.domain.service.MemoryLeakDetector;
import com.mace.domain.service.SystemProcessGuard;
import com.mace.domain.service.ThermalAlertEvaluator;
import com.mace.infrastructure.mock.MockProcessAdapter;
import com.mace.infrastructure.mock.MockSensorsAdapter;
import com.mace.infrastructure.telemetry.NoOpBlackboxSink;
import com.mace.infrastructure.telemetry.SubmissionTelemetryPublisher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainDashboardViewModelTest {

    @Test
    void botonUsaLaIdentidadCompletaDeLaFilaConfirmada() {
        MockProcessAdapter processes = new MockProcessAdapter();
        ProcessEntry entry = processes.enumerateProcesses().processes().stream()
                .filter(process -> process.name().equals("chrome.exe")).findFirst().orElseThrow();
        ProcessRow row = new ProcessRow(entry.identity(), entry.name(), entry.imagePath(), entry.memory());

        try (SubmissionTelemetryPublisher publisher = new SubmissionTelemetryPublisher();
             MonitoringService monitor = monitor(processes, publisher);
             MainDashboardViewModel viewModel = viewModel(monitor, processes, publisher)) {
            assertEquals(TerminationStatus.SUCCESS, viewModel.terminateProcess(row).status());
            assertTrue(processes.currentIdentityOf(row.pid()).isEmpty());
        }
    }

    @Test
    void filaAntiguaNoFinalizaAlNuevoDuenoDeUnPidReutilizado() {
        MockProcessAdapter processes = new MockProcessAdapter();
        ProcessEntry old = processes.enumerateProcesses().processes().stream()
                .filter(process -> process.pid().value() == MockProcessAdapter.REUSED_PID)
                .findFirst().orElseThrow();
        ProcessRow row = new ProcessRow(old.identity(), old.name(), old.imagePath(), old.memory());
        for (int i = 0; i < MockProcessAdapter.REUSE_AFTER_ENUMERATIONS; i++) {
            processes.enumerateProcesses();
        }

        try (SubmissionTelemetryPublisher publisher = new SubmissionTelemetryPublisher();
             MonitoringService monitor = monitor(processes, publisher);
             MainDashboardViewModel viewModel = viewModel(monitor, processes, publisher)) {
            assertEquals(TerminationStatus.PID_REUSED, viewModel.terminateProcess(row).status());
            assertEquals(MockProcessAdapter.reusedNewIdentity(),
                    processes.currentIdentityOf(MockProcessAdapter.REUSED_PID).orElseThrow());
        }
    }

    private static MonitoringService monitor(MockProcessAdapter processes, SubmissionTelemetryPublisher publisher) {
        NoOpBlackboxSink blackbox = new NoOpBlackboxSink();
        return new MonitoringService(new PollSystemMetricsService(new MockSensorsAdapter(), publisher, blackbox,
                new ThermalAlertEvaluator()), new ListProcessesService(processes),
                new ProcessInspectionService(processes), new MemoryLeakDetector(), new HangDetector(),
                blackbox, publisher);
    }

    private static MainDashboardViewModel viewModel(MonitoringService monitor, MockProcessAdapter processes,
                                                     SubmissionTelemetryPublisher publisher) {
        return new MainDashboardViewModel(monitor,
                new TerminateProcessService(processes, new SystemProcessGuard()), publisher,
                "Datos simulados", null);
    }
}
