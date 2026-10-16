package com.mace.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Fotografía inmutable del estado de hardware en la telemetría V2.
 *
 * <p>A diferencia de {@link com.mace.domain.model.TelemetrySnapshot}, cada métrica lleva
 * su propio {@link MetricState}, lo que permite distinguir "sensor ausente" de "valor
 * real en cero". Esta clase es aditiva y no reemplaza a {@link
 * com.mace.domain.model.TelemetrySnapshot} durante la migración.</p>
 *
 * @param timestamp instante exacto de la captura
 * @param abiVersion versión de la ABI nativa que produjo este snapshot
 * @param cpu telemetría de CPU
 * @param gpus telemetría de cada GPU detectada (vacía si no hay GPU)
 * @param systemMemory telemetría de memoria del sistema
 */
public record HardwareSnapshot(
        Instant timestamp,
        String abiVersion,
        CpuTelemetry cpu,
        List<GpuTelemetry> gpus,
        SystemMemoryTelemetry systemMemory
) {

    public HardwareSnapshot {
        Objects.requireNonNull(timestamp, "timestamp no puede ser null");
        Objects.requireNonNull(abiVersion, "abiVersion no puede ser null");
        Objects.requireNonNull(cpu, "cpu no puede ser null");
        Objects.requireNonNull(gpus, "gpus no puede ser null");
        Objects.requireNonNull(systemMemory, "systemMemory no puede ser null");
    }
}
