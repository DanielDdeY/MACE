package com.mace.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultado de un ciclo del monitor a 1 Hz: el <strong>único</strong> flujo que consumen el
 * dashboard y el Micro-HUD. Abrir o cerrar el HUD no genera sondeos adicionales; ambos reciben
 * el mismo objeto con el mismo {@code sequence}.
 *
 * @param sequence         número de publicación, creciente desde 1
 * @param hardwareSequence número del sondeo físico que produjo {@code hardware}; un refresco solo
 *                         de procesos repite el valor anterior. Las gráficas y el HUD añaden un
 *                         punto únicamente cuando este número cambia
 * @param hardware         CPU, GPU y memoria del sistema
 * @param thermalAlerts  alertas térmicas calculadas en el dominio (umbrales 75/85 °C)
 * @param processes      inventario de procesos, o vacío si la enumeración falló en este ciclo
 * @param leakAlerts     posibles fugas activas
 * @param hangAlerts     procesos con ventanas colgadas
 * @param responsiveness último estado de ventanas conocido por identidad
 * @param blackbox       estado de la caja negra
 */
public record MonitoringSnapshot(
        long sequence,
        long hardwareSequence,
        HardwareSnapshot hardware,
        List<ThermalAlert> thermalAlerts,
        Optional<ProcessInventory> processes,
        List<LeakAlert> leakAlerts,
        List<HangAlert> hangAlerts,
        Map<ProcessIdentity, ResponsivenessResult> responsiveness,
        BlackboxStats blackbox
) {

    public MonitoringSnapshot {
        if (sequence <= 0 || hardwareSequence <= 0) {
            throw new IllegalArgumentException("sequence y hardwareSequence deben ser positivos");
        }
        Objects.requireNonNull(hardware, "hardware no puede ser null");
        Objects.requireNonNull(processes, "processes no puede ser null");
        Objects.requireNonNull(blackbox, "blackbox no puede ser null");
        thermalAlerts = List.copyOf(Objects.requireNonNull(thermalAlerts, "thermalAlerts no puede ser null"));
        leakAlerts = List.copyOf(Objects.requireNonNull(leakAlerts, "leakAlerts no puede ser null"));
        hangAlerts = List.copyOf(Objects.requireNonNull(hangAlerts, "hangAlerts no puede ser null"));
        responsiveness = Map.copyOf(Objects.requireNonNull(responsiveness, "responsiveness no puede ser null"));
    }

    public Optional<ResponsivenessResult> responsivenessOf(ProcessIdentity identity) {
        return Optional.ofNullable(responsiveness.get(identity));
    }

    /** Nivel más alto entre las alertas térmicas del ciclo. */
    public AlertLevel highestThermalLevel() {
        return thermalAlerts.stream().map(ThermalAlert::level)
                .max(Enum::compareTo).orElse(AlertLevel.NONE);
    }
}
