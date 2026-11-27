package com.mace.domain.model;

/** Clase de registro guardado en la caja negra. */
public enum BlackboxEntryKind {
    CPU_SAMPLE("Muestra CPU"),
    GPU_SAMPLE("Muestra GPU"),
    PROCESS_SAMPLE("Muestra proceso"),
    MEMORY_SAMPLE("Muestra RAM"),
    /** Registros que el anillo temporal sobrescribió antes de copiarse al archivo. */
    GAP("Hueco"),
    /** Evento de aplicación: ver {@link BlackboxEntry#event()}. */
    EVENT("Evento"),
    UNKNOWN("Desconocido");

    private final String label;

    BlackboxEntryKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isSample() {
        return this == CPU_SAMPLE || this == GPU_SAMPLE || this == PROCESS_SAMPLE || this == MEMORY_SAMPLE;
    }
}
