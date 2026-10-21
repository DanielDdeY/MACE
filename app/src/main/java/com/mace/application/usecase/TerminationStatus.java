package com.mace.application.usecase;

/**
 * Resultado posible de una solicitud manual de terminación de proceso. Cada motivo es distinto
 * para que la UI explique lo ocurrido y refresque la lista cuando corresponda; ninguno habilita
 * un reintento automático.
 */
public enum TerminationStatus {
    SUCCESS,
    /** Error de Windows no clasificado; ver {@link TerminationResult#win32Error()}. */
    FAILED,
    /** Bloqueado por el dominio: PID reservado (0/4) o el propio MACE. No se llamó a la DLL. */
    REJECTED_SYSTEM_PROCESS,
    /** Rechazado por la DLL: proceso crítico, nombre/ruta protegidos o no verificables. */
    REJECTED_PROTECTED_PROCESS,
    /** Faltan permisos (elevación) para terminar el proceso. */
    ACCESS_DENIED,
    /** El proceso ya había terminado: refrescar la lista. */
    PROCESS_EXITED,
    /** El PID pertenece ahora a otro proceso: refrescar la lista, no reintentar con el PID. */
    PID_REUSED,
    /** Se pidió terminar solo por PID; se requiere la identidad completa de la fila. */
    IDENTITY_REQUIRED;

    /** @return {@code true} si la fila ya no corresponde a un proceso vivo y conviene refrescar */
    public boolean requiresRefresh() {
        return this == SUCCESS || this == PROCESS_EXITED || this == PID_REUSED;
    }
}
