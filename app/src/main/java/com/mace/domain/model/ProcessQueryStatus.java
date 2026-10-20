package com.mace.domain.model;

/**
 * Resultado de una consulta puntual sobre un proceso identificado (ruta, muestra de memoria).
 * Cada motivo de fallo se conserva por separado para poder explicarlo en la UI.
 */
public enum ProcessQueryStatus {
    OK("Disponible"),
    ACCESS_DENIED("Acceso denegado"),
    PROTECTED_PROCESS("Proceso protegido"),
    PROCESS_EXITED("El proceso terminó"),
    PID_REUSED("PID reutilizado por otro proceso"),
    ERROR("Error de Windows");

    private final String label;

    ProcessQueryStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** @return {@code true} si el proceso ya no es el mismo y la lista debe refrescarse */
    public boolean processIsGone() {
        return this == PROCESS_EXITED || this == PID_REUSED;
    }
}
