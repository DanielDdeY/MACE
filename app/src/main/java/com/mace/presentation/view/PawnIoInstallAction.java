package com.mace.presentation.view;

import java.util.Objects;

/**
 * Acción que instala el controlador PawnIO a petición del usuario. Bloqueante:
 * la vista la ejecuta fuera del hilo de JavaFX. Solo existe en modo nativo y en
 * una instalación completa; en Preview no se ofrece.
 */
@FunctionalInterface
public interface PawnIoInstallAction {

    Feedback install();

    /** Resultado para mostrar al usuario, sin rutas ni datos de la máquina. */
    record Feedback(boolean succeeded, String message) {
        public Feedback {
            Objects.requireNonNull(message, "message no puede ser null");
        }
    }
}
