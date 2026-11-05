package com.mace.domain.model;

import java.util.Objects;

/**
 * GPU física enumerada por DXGI ({@code MaceGpuAdapterInfoV2}). Los adaptadores de software
 * se excluyen en la capa nativa. La GPU integrada y la dedicada aparecen como entradas
 * separadas con LUID distinto.
 *
 * @param id           LUID de la sesión actual
 * @param vendorId     identificador PCI del fabricante
 * @param deviceId     identificador PCI del dispositivo
 * @param subsystemId  identificador PCI del subsistema
 * @param revision     revisión PCI
 * @param dxgiFlags    {@code DXGI_ADAPTER_FLAG} tal cual
 * @param adapterIndex índice de enumeración DXGI (tampoco es persistente)
 * @param description  descripción del controlador (p. ej. «AMD Radeon RX 5500 XT»)
 */
public record GpuAdapter(
        GpuAdapterId id,
        int vendorId,
        int deviceId,
        int subsystemId,
        int revision,
        int dxgiFlags,
        int adapterIndex,
        String description
) {

    public GpuAdapter {
        Objects.requireNonNull(id, "id no puede ser null");
        description = description == null || description.isBlank() ? "GPU sin descripción" : description;
    }

    public GpuVendor vendor() {
        return GpuVendor.fromPciVendorId(vendorId);
    }

    /** Nombre corto para alertas y etiquetas: «GPU 0 · AMD Radeon RX 5500 XT». */
    public String displayName() {
        return "GPU " + adapterIndex + " · " + description;
    }
}
