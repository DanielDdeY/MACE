package com.mace.domain.port.out;

import com.mace.domain.model.TelemetrySnapshot;

/**
 * Puerto de salida hacia los sensores de hardware (CPU/GPU).
 *
 * El dominio consume telemetría a través de esta interfaz sin conocer si la
 * implementación real usa la DLL nativa (FFM) o un mock simulado.
 */
public interface HardwareSensorsPort {

    /**
     * Lee el estado térmico y energético actual del sistema.
     *
     * @return snapshot inmutable con la telemetría del instante de la llamada
     */
    TelemetrySnapshot fetchCurrentTelemetry();
}
