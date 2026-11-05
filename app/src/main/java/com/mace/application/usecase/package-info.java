/**
 * Casos de uso de MACE (Java puro: sin FFM, JavaFX ni Win32).
 *
 * <p>Composición prevista (la realiza la presentación en {@code MaceApplication}):</p>
 * <pre>{@code
 * InfrastructureAdapters adapters = InfrastructureAdapters.create(mode);
 * BlackboxSinkPort blackbox = adapters.openBlackbox();                 // NoOp si no puede abrirse
 * SubmissionTelemetryPublisher publisher = new SubmissionTelemetryPublisher();
 * PollSystemMetricsService poll = new PollSystemMetricsService(
 *         adapters.sensors(), publisher, blackbox, new ThermalAlertEvaluator());
 * ListProcessesService list = new ListProcessesService(adapters.processes());
 * ProcessInspectionService inspection = new ProcessInspectionService(adapters.processes());
 * TerminateProcessService terminate = new TerminateProcessService(
 *         adapters.processes(), new SystemProcessGuard(), blackbox, Clock.systemUTC());
 * MonitoringService monitor = new MonitoringService(poll, list, inspection,
 *         new MemoryLeakDetector(), new HangDetector(), blackbox, publisher);
 * publisher.subscribeMonitoring(dashboardSubscriber);   // y el Micro-HUD, al abrirse
 * monitor.start();                                      // ÚNICO bucle a 1 Hz
 * }</pre>
 *
 * <p>Cierre, en este orden: {@code monitor.close()} → ViewModels/suscriptores →
 * {@code publisher.close()} → {@code adapters.close()} (caja negra y después una sola llamada a
 * {@code CP_ShutdownTelemetry}).</p>
 *
 * <p>Ningún caso de uso termina ni suspende procesos por su cuenta: solo
 * {@link com.mace.application.usecase.TerminateProcessService#terminate(com.mace.domain.model.ProcessIdentity)}
 * lo hace, por acción manual y con la identidad completa de la fila.</p>
 */
package com.mace.application.usecase;
