package com.mace.domain.model;

/**
 * Estado de fiabilidad de una métrica individual de hardware.
 *
 * <p>Una métrica con estado distinto de {@link #VALID} no debe tratarse como una
 * medición válida, incluso si su valor numérico es cero o distinto de cero. Esto
 * permite distinguir, por ejemplo, "GPU detectada sin temperatura" de "no hay GPU",
 * y evitar que un {@code 0} de sustitución se interprete como una lectura real.</p>
 */
public enum MetricState {
    /** La métrica proviene de un sensor real y su valor es fiable. */
    VALID,

    /** No hay proveedor ni sensor para esta métrica (p. ej. equipo sin NVML). */
    NO_PROVIDER,

    /** El hardware existe pero no soporta esta métrica en particular. */
    UNSUPPORTED,

    /** El sensor está inicializando y el valor aún no es fiable. */
    WARMING_UP,

    /** Se produjo un error al intentar leer la métrica. */
    ERROR
}
