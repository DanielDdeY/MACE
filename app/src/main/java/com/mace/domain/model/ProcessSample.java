package com.mace.domain.model;

import java.util.Objects;

/**
 * Muestra puntual de memoria de un proceso identificado ({@code MaceProcessSampleV2}).
 *
 * @param identity       proceso muestreado
 * @param monotonicNanos marca monotónica de la muestra (solo para intervalos)
 * @param workingSet     Working Set con estado
 * @param privateBytes   Private Bytes con estado
 */
public record ProcessSample(ProcessIdentity identity, long monotonicNanos,
                            LongMetricReading workingSet, LongMetricReading privateBytes) {

    public ProcessSample {
        Objects.requireNonNull(identity, "identity no puede ser null");
        Objects.requireNonNull(workingSet, "workingSet no puede ser null");
        Objects.requireNonNull(privateBytes, "privateBytes no puede ser null");
    }

    /** Construye una muestra a partir de una fila del inventario y la marca de la enumeración. */
    public static ProcessSample fromInventory(ProcessEntry entry, long monotonicNanos) {
        Objects.requireNonNull(entry, "entry no puede ser null");
        return new ProcessSample(entry.identity(), monotonicNanos,
                entry.memory().workingSetReading(), entry.memory().privateBytesReading());
    }
}
