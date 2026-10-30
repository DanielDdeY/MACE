package com.mace.domain.model;

/** Fabricante de CPU detectado por CPUID en la capa nativa. */
public enum CpuVendor {
    UNKNOWN("Desconocido"),
    INTEL("Intel"),
    AMD("AMD");

    private final String label;

    CpuVendor(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
