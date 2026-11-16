package com.mace.domain.model;

/**
 * Proveedor que aportó las lecturas de un dispositivo ({@code MaceProviderId}). Se registra
 * para diagnóstico: una ausencia de datos depende del proveedor/controlador, no de FFM.
 */
public enum SensorProvider {
    NONE("Ninguno"),
    WINDOWS("Windows"),
    INTEL_MSR("Intel MSR (obsoleto)"), // Reservado ABI 2; sin implementación activa.
    AMD_RYZEN_SDK("AMD CPU (obsoleto)"), // Reservado ABI 3; sin implementación activa.
    AMD_UPROF("AMD uProf (reservado)"), // Reservado ABI 4; nunca implementado.
    NVIDIA_NVML("NVIDIA NVML"),
    AMD_ADLX("AMD GPU (obsoleto)"), // Reservado ABI 6; sin implementación activa.
    INTEL_IGCL("Intel IGCL"),
    LHM("LibreHardwareMonitor");

    private final String label;

    SensorProvider(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
