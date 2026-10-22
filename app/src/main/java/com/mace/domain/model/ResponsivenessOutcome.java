package com.mace.domain.model;

/**
 * Estado de las ventanas superiores visibles de un proceso. Cada caso es distinto:
 * «sin ventana» no significa «responde», y «finalizado» no significa «colgado».
 */
public enum ResponsivenessOutcome {
    /** Tiene ventanas visibles y ninguna aparece colgada. */
    RESPONDING("Responde"),
    /** Al menos una ventana visible aparece colgada ({@code IsHungAppWindow}). */
    HUNG("No responde"),
    /** No tiene ventanas superiores visibles en el escritorio accesible. */
    NO_WINDOW("Sin ventana"),
    /** El proceso terminó durante la consulta. */
    PROCESS_EXITED("Finalizado"),
    /** El PID ya pertenece a otro proceso. */
    PID_REUSED("PID reutilizado"),
    /** Sin permisos para consultar el proceso. */
    ACCESS_DENIED("Acceso denegado"),
    /** Windows falló al consultar. */
    ERROR("Error");

    private final String label;

    ResponsivenessOutcome(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
