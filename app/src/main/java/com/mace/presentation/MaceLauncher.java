package com.mace.presentation;

import javafx.application.Application;

/** Entrada de classpath para jpackage cuando JavaFX viaja como dependencias de la app. */
public final class MaceLauncher {

    private MaceLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(MaceApplication.class, args);
    }
}
