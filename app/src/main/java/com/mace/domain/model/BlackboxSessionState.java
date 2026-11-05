package com.mace.domain.model;

/** Estado de un archivo de sesión de la caja negra, tal como puede recuperarse del disco. */
public enum BlackboxSessionState {
    /** Es el archivo que la sesión actual sigue escribiendo. */
    IN_PROGRESS("En curso"),
    /** La sesión cerró el archivo correctamente. */
    CLOSED("Cerrada"),
    /** El archivo no tiene marca de cierre (corte de energía, cierre forzado). */
    INTERRUPTED("Sin cierre registrado"),
    /** Hay bytes dañados o truncados después del último registro válido. */
    INCOMPLETE("Registros incompletos"),
    /** La cabecera no es la de una caja negra de MACE. */
    INVALID_HEADER("Cabecera inválida"),
    /** No se pudo leer el archivo. */
    READ_ERROR("No disponible");

    private final String label;

    BlackboxSessionState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
