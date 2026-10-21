package com.mace.domain.model;

/** Fabricante de GPU según su identificador PCI. */
public enum GpuVendor {
    NVIDIA("NVIDIA"),
    AMD("AMD"),
    INTEL("Intel"),
    OTHER("Otro");

    private final String label;

    GpuVendor(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** @param pciVendorId identificador PCI (0x10DE NVIDIA, 0x1002/0x1022 AMD, 0x8086 Intel) */
    public static GpuVendor fromPciVendorId(int pciVendorId) {
        return switch (pciVendorId) {
            case 0x10DE -> NVIDIA;
            case 0x1002, 0x1022 -> AMD;
            case 0x8086 -> INTEL;
            default -> OTHER;
        };
    }
}
