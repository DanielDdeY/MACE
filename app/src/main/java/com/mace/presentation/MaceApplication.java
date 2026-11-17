package com.mace.presentation;

import atlantafx.base.theme.PrimerDark;
import com.mace.application.usecase.ListProcessesService;
import com.mace.application.usecase.MonitoringService;
import com.mace.application.usecase.PollSystemMetricsService;
import com.mace.application.usecase.ProcessInspectionService;
import com.mace.application.usecase.TerminateProcessService;
import com.mace.domain.port.out.BlackboxSinkPort;
import com.mace.domain.service.HangDetector;
import com.mace.domain.service.MemoryLeakDetector;
import com.mace.domain.service.SystemProcessGuard;
import com.mace.domain.service.ThermalAlertEvaluator;
import com.mace.infrastructure.InfrastructureAdapters;
import com.mace.infrastructure.telemetry.BlackboxSettings;
import com.mace.infrastructure.telemetry.SubmissionTelemetryPublisher;
import com.mace.presentation.view.MainDashboardController;
import com.mace.presentation.viewmodel.MainDashboardViewModel;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Punto de composición de MACE. */
public final class MaceApplication extends Application {

    private InfrastructureAdapters adapters;
    private SubmissionTelemetryPublisher telemetryPublisher;
    private MonitoringService monitoringService;
    private MainDashboardViewModel dashboardViewModel;
    private MainDashboardController dashboardController;
    private final ShutdownCoordinator shutdown;
    private WindowsSingleInstance.Result instanceReservation;

    public MaceApplication() { this(new ShutdownCoordinator()); }
    MaceApplication(ShutdownCoordinator shutdown) { this.shutdown = shutdown; }

    @Override
    public void init() {
        // JavaFX calls init before creating the primary Stage. No telemetry/helper yet.
        instanceReservation = WindowsSingleInstance.acquire();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        if (instanceReservation == null || instanceReservation.status() != WindowsSingleInstance.Status.ACQUIRED) {
            boolean busy = instanceReservation != null && instanceReservation.status() == WindowsSingleInstance.Status.BUSY;
            Alert notice = new Alert(busy ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
            notice.setTitle("MACE");
            notice.setHeaderText(busy ? "MACE ya está abierto o se está cerrando" : "No se pudo reservar la instancia de MACE");
            notice.setContentText(busy
                    ? "Solo se permite una instancia de MACE o Preview por usuario y sesión. Espera unos segundos y vuelve a intentarlo."
                    : "No se inició la telemetría. Revisa los permisos de Windows y vuelve a intentarlo. Código: "
                        + (instanceReservation == null ? -1 : instanceReservation.error()));
            notice.showAndWait();
            Platform.exit();
            return;
        }
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        /*
         * La aplicación de escritorio arranca en modo NATIVO por defecto.
         * Así, un problema al localizar/cargar la DLL no queda oculto detrás
         * de datos simulados. Para desarrollo visual o CI todavía puede forzarse:
         *   -Dmace.infra.mode=mock
         */
        InfrastructureAdapters.Mode requestedMode =
                System.getProperty(InfrastructureAdapters.MODE_PROPERTY) == null
                        ? InfrastructureAdapters.Mode.NATIVE
                        : InfrastructureAdapters.Mode.fromSystemProperty();

        try {
            adapters = InfrastructureAdapters.create(requestedMode);
        } catch (RuntimeException nativeError) {
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("MACE - Biblioteca nativa no disponible");
            error.setHeaderText("No se pudo iniciar MACE con datos reales");
            error.setContentText(
                    nativeError.getMessage()
                            + System.lineSeparator()
                            + System.lineSeparator()
                            + "Copia mace_native.dll en app/lib, compila native/ con CMake "
                            + "o define MACE_NATIVE_LIB con la ruta de la DLL.");
            error.showAndWait();
            throw nativeError;
        }

        if (adapters.isNative()) {
            System.out.println("[MACE] Modo NATIVO - datos reales");
            adapters.nativeLibrary().ifPresent(path ->
                    System.out.println("[MACE] DLL: " + path));
        } else {
            System.out.println("[MACE] Modo MOCK - datos simulados");
        }

        telemetryPublisher = new SubmissionTelemetryPublisher();

        BlackboxSinkPort blackbox = adapters.openBlackbox();
        PollSystemMetricsService pollService = new PollSystemMetricsService(
                adapters.sensors(), telemetryPublisher, blackbox, new ThermalAlertEvaluator());
        ListProcessesService listProcessesService = new ListProcessesService(adapters.processes());
        ProcessInspectionService inspectionService = new ProcessInspectionService(adapters.processes());
        TerminateProcessService terminateProcessService = new TerminateProcessService(
                adapters.processes(), new SystemProcessGuard(), blackbox, Clock.systemUTC());

        monitoringService = new MonitoringService(pollService, listProcessesService, inspectionService,
                new MemoryLeakDetector(), new HangDetector(), blackbox, telemetryPublisher);

        dashboardViewModel = new MainDashboardViewModel(
                monitoringService, terminateProcessService, telemetryPublisher,
                adapters.dataSourceDescription(), adapters.blackboxFailure().orElse(null));

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainDashboard.fxml"));
        loader.setControllerFactory(type -> {
            if (type == MainDashboardController.class) {
                dashboardController = new MainDashboardController(dashboardViewModel,
                        BlackboxSettings.defaultDirectory(!adapters.isNative()));
                return dashboardController;
            }
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("No se pudo crear el controlador " + type.getName(), e);
            }
        });

        Scene scene = new Scene(loader.load(), 1100, 700);
        primaryStage.setTitle("MACE");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        Platform.setImplicitExit(false);
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            beginShutdown(true);
            primaryStage.hide();
        });
        primaryStage.show();
    }

    @Override
    public void stop() {
        // Also covers Platform.exit() and partial startup; never joins on JavaFX.
        beginShutdown(false);
    }

    private void beginShutdown(boolean exitWhenComplete) {
        shutdown.armWatchdog(); // Covers view detach as well as monitor/persistence/native cleanup.
        if (shutdown.isStarted()) return;
        // UI bindings/stages belong to JavaFX. Only detach them here; all waits,
        // persistence and native teardown belong to the dedicated cleanup owner.
        try { if (dashboardController != null) dashboardController.close(); }
        catch (RuntimeException error) { System.err.println("MACE: view detach failed during shutdown."); }
        try { if (dashboardViewModel != null) dashboardViewModel.close(); }
        catch (RuntimeException error) { System.err.println("MACE: subscription detach failed during shutdown."); }
        List<AutoCloseable> resources = new ArrayList<>();
        if (monitoringService != null) resources.add(monitoringService);
        if (telemetryPublisher != null) resources.add(telemetryPublisher);
        if (adapters != null) resources.add(adapters);
        shutdown.start(resources, failure -> {
            if (failure != null) System.err.println("MACE: cleanup completed with errors.");
            if (exitWhenComplete) {
                try { Platform.runLater(Platform::exit); }
                catch (IllegalStateException toolkitStopped) { /* stop() already ran. */ }
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
