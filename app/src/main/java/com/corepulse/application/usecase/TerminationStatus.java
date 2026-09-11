package com.corepulse.application.usecase;

/**
 * Resultado posible de una solicitud manual de terminación de proceso.
 */
public enum TerminationStatus {
    SUCCESS,
    FAILED,
    REJECTED_SYSTEM_PROCESS
}
