package com.mace.domain.port.out;

import com.mace.domain.model.TelemetrySnapshot;

/**
 * Puerto de salida para el volcado de telemetría a la "caja negra" (persistencia
 * en disco de snapshots críticos).
 *
 * En el MVP la implementación es un no-op; el puerto existe como punto de
 * extensión para la fase futura de caja negra completa.
 */
public interface BlackboxSinkPort {

    /**
     * Persiste un snapshot considerado crítico.
     *
     * @param snapshot lectura de telemetría a volcar
     */
    void dump(TelemetrySnapshot snapshot);
}
