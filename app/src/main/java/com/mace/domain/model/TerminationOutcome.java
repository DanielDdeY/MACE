package com.mace.domain.model;

/**
 * Desenlace de una solicitud de terminación enviada al puerto de procesos. Los motivos de
 * rechazo se mantienen separados para que la UI explique exactamente qué ocurrió.
 */
public enum TerminationOutcome {
    TERMINATED,
    /** La capa nativa rechazó el objetivo: propio, crítico, ruta de sistema o no verificable. */
    PROTECTED_PROCESS,
    ACCESS_DENIED,
    /** El proceso ya había terminado. */
    PROCESS_EXITED,
    /** El PID pertenece ahora a otro proceso (fecha de creación distinta). */
    PID_REUSED,
    /** Identidad inválida para la capa nativa. */
    INVALID_IDENTITY,
    /** Error de Windows; ver {@code win32Error}. */
    FAILED
}
