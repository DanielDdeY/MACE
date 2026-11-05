package com.mace.domain.model;

/**
 * Proveedor que aportó las lecturas de un dispositivo ({@code MaceProviderId}). Se registra
 * para diagnóstico: una ausencia de datos depende del proveedor/controlador, no de FFM.
 */
public enum SensorProvider {
    NONE("Ninguno"),
    WINDOWS("Windows"),
    INTEL_MSR("Intel MSR (WinRing0)"),
    AMD_RYZEN_SDK("AMD Ryzen Master SDK"),
    AMD_UPROF("AMD uProf"),
    NVIDIA_NVML("NVIDIA NVML"),
    AMD_ADLX("AMD ADLX"),
    INTEL_IGCL("Intel IGCL");

    private final String label;

    SensorProvider(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
