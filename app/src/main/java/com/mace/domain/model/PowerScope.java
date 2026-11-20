package com.mace.domain.model;

/**
 * Ámbito físico que cubre una lectura de potencia ({@code MacePowerScope}).
 *
 * <p>Dos lecturas de ámbitos distintos pueden solaparse (p. ej. {@link #APU_SOC} ya incluye la
 * iGPU). Por eso MACE <strong>no</strong> suma CPU + GPU ni lo presenta como «consumo total del
 * equipo»: cada potencia se etiqueta con su ámbito.</p>
 */
public enum PowerScope {
    UNKNOWN("Ámbito desconocido"),
    CPU_PACKAGE("CPU package"),
    CPU_RAIL("CPU rail"),
    GPU_BOARD("GPU board"),
    GPU_CHIP("GPU chip"),
    APU_SOC("APU SoC");

    private final String label;

    PowerScope(String label) {
        this.label = label;
    }

    /** Etiqueta para la UI, p. ej. «CPU package» o «GPU board». */
    public String label() {
        return label;
    }
}
