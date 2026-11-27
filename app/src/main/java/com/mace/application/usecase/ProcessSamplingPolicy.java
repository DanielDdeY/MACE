package com.mace.application.usecase;

import java.time.Duration;
import java.util.Objects;

/**
 * Frecuencia con la que el monitor copia muestras de procesos a la caja negra. El detector de
 * fugas sigue recibiendo el inventario completo cada segundo; esta política solo limita lo que
 * se persiste, para que el historial cubra horas y no minutos.
 *
 * @param allProcessesInterval   intervalo para guardar una muestra de todos los procesos listados
 * @param alertedProcessInterval intervalo para los procesos con alerta activa de fuga o cuelgue
 */
public record ProcessSamplingPolicy(Duration allProcessesInterval, Duration alertedProcessInterval) {

    public ProcessSamplingPolicy {
        Objects.requireNonNull(allProcessesInterval, "allProcessesInterval no puede ser null");
        Objects.requireNonNull(alertedProcessInterval, "alertedProcessInterval no puede ser null");
        if (allProcessesInterval.isNegative() || alertedProcessInterval.isNegative()) {
            throw new IllegalArgumentException("Los intervalos no pueden ser negativos");
        }
    }

    /**
     * Todos los procesos cada 60 s y los que tienen alerta cada 5 s. Con ~100 procesos y una GPU
     * son unos 5 registros por segundo: cada archivo de 8 192 registros cubre ~25 minutos.
     */
    public static ProcessSamplingPolicy defaults() {
        return new ProcessSamplingPolicy(Duration.ofSeconds(60), Duration.ofSeconds(5));
    }
}
