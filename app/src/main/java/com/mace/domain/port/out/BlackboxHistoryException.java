package com.mace.domain.port.out;

/**
 * Error al consultar el historial de la caja negra. {@link #sessionMissing()} distingue un archivo
 * que la retención borró entre el listado y la lectura de un error de lectura real.
 */
public class BlackboxHistoryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean sessionMissing;

    public BlackboxHistoryException(String message, boolean sessionMissing, Throwable cause) {
        super(message, cause);
        this.sessionMissing = sessionMissing;
    }

    /** @return {@code true} si la sesión ya no existe (posible rotación) */
    public boolean sessionMissing() {
        return sessionMissing;
    }
}
