package com.mace.application.usecase;

import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.ThermalAlert;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de un sondeo de hardware: el snapshot V2 y las alertas térmicas del dominio.
 *
 * @param snapshot      CPU, GPU y memoria
 * @param thermalAlerts alertas accionables (WARNING/CRITICAL)
 */
public record HardwarePoll(HardwareSnapshot snapshot, List<ThermalAlert> thermalAlerts) {

    public HardwarePoll {
        Objects.requireNonNull(snapshot, "snapshot no puede ser null");
        thermalAlerts = List.copyOf(Objects.requireNonNull(thermalAlerts, "thermalAlerts no puede ser null"));
    }
}
