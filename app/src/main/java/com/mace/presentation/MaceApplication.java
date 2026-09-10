package com.mace.presentation;

import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Punto de entrada de MACE (parte UI - Dev 4).
 *
 * Arranca de forma 100% independiente, consumiendo datos simulados generados
 * localmente en MainDashboardViewModel. No depende de que la capa nativa (C)
 * ni el puente FFM esten terminados. Cuando la integracion este lista, el
 * unico cambio necesario es que el ViewModel consuma los casos de uso reales
 * en lugar del Timeline mock (ver notas en MainDashboardViewModel).
 */
public class MaceApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Tema oscuro AtlantaFX (regla de diseno del proyecto).
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainDashboard.fxml"));
        Scene scene = new Scene(loader.load(), 1100, 700);

        primaryStage.setTitle("MACE - CorePulse");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}