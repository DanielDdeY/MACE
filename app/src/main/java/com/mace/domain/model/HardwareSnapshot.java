package com.mace.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fotografía V2 del hardware en un sondeo: CPU, <strong>cada</strong> GPU identificada y
 * memoria global, con estado individual por métrica. Sustituye a {@link TelemetrySnapshot},
 * que solo admite una GPU y dos booleanos generales.
 *
 * <p>Una lista de GPU vacía significa «sin GPU física detectada»; una GPU presente sin
 * métricas válidas significa «GPU detectada, sensor no disponible».</p>
 *
 * @param timestamp instante de pared (reloj Java) del sondeo
 * @param cpu       lectura de CPU
 * @param gpus      lecturas por GPU, en orden de enumeración DXGI
 * @param memory    memoria física global
 */
public record HardwareSnapshot(Instant timestamp, CpuReading cpu, List<GpuReading> gpus, SystemMemoryReading memory) {

    public HardwareSnapshot {
        Objects.requireNonNull(timestamp, "timestamp no puede ser null");
        Objects.requireNonNull(cpu, "cpu no puede ser null");
        Objects.requireNonNull(memory, "memory no puede ser null");
        gpus = List.copyOf(Objects.requireNonNull(gpus, "gpus no puede ser null"));
    }

    public Optional<GpuReading> gpu(GpuAdapterId id) {
        return gpus.stream().filter(reading -> reading.adapterId().equals(id)).findFirst();
    }

    public boolean hasGpus() {
        return !gpus.isEmpty();
    }
}
