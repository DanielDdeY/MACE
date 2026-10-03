package com.mace.presentation;

import atlantafx.base.theme.PrimerDark;
import com.mace.application.usecase.ListProcessesService;
import com.mace.application.usecase.PollSystemMetricsService;
import com.mace.application.usecase.TerminateProcessService;
import com.mace.domain.service.SystemProcessGuard;
import com.mace.domain.service.ThermalAlertEvaluator;
import com.mace.infrastructure.InfrastructureAdapters;
import com.mace.infrastructure.telemetry.NoOpBlackboxSink;
import com.mace.infrastructure.telemetry.SubmissionTelemetryPublisher;
import com.mace.presentation.view.MainDashboardController;
import com.mace.presentation.viewmodel.MainDashboardViewModel;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Punto de composición de MACE. */
public final class MaceApplication extends Application {

    private InfrastructureAdapters adapters;
    private SubmissionTelemetryPublisher telemetryPublisher;
    private MainDashboardViewModel dashboardViewModel;

    @Override
    public void start(Stage primaryStage) throws Exception {
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

        PollSystemMetricsService pollService = new PollSystemMetricsService(
                adapters.sensors(), telemetryPublisher, new NoOpBlackboxSink(), new ThermalAlertEvaluator());
        ListProcessesService listProcessesService = new ListProcessesService(adapters.processes());
        TerminateProcessService terminateProcessService = new TerminateProcessService(
                adapters.processes(), new SystemProcessGuard());

        dashboardViewModel = new MainDashboardViewModel(
                pollService, listProcessesService, terminateProcessService, telemetryPublisher);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainDashboard.fxml"));
        loader.setControllerFactory(type -> {
            if (type == MainDashboardController.class) {
                return new MainDashboardController(dashboardViewModel);
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
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (dashboardViewModel != null) {
            dashboardViewModel.close();
        }
        if (telemetryPublisher != null) {
            telemetryPublisher.close();
        }
        if (adapters != null) {
            adapters.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
