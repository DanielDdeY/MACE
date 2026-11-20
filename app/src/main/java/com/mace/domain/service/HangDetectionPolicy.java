package com.mace.domain.service;

import java.time.Duration;
import java.util.Objects;

/**
 * Frecuencia de consulta de ventanas por proceso. Consultar todas las ventanas de todos los
 * procesos cada segundo es costoso; los procesos sin ventana se revisan con menos frecuencia.
 *
 * @param windowedInterval   intervalo para procesos con ventana (respondiendo o colgados)
 * @param noWindowInterval   intervalo para procesos sin ventana visible o con error
 * @param maxChecksPerCycle  consultas máximas por ciclo de monitoreo
 */
public record HangDetectionPolicy(Duration windowedInterval, Duration noWindowInterval, int maxChecksPerCycle) {

    public HangDetectionPolicy {
        Objects.requireNonNull(windowedInterval, "windowedInterval no puede ser null");
        Objects.requireNonNull(noWindowInterval, "noWindowInterval no puede ser null");
        if (windowedInterval.isNegative() || noWindowInterval.isNegative() || maxChecksPerCycle <= 0) {
            throw new IllegalArgumentException("Intervalos no negativos y maxChecksPerCycle > 0");
        }
    }

    /** 5 s con ventana, 30 s sin ventana, hasta 64 consultas por ciclo. */
    public static HangDetectionPolicy defaults() {
        return new HangDetectionPolicy(Duration.ofSeconds(5), Duration.ofSeconds(30), 64);
    }
}
