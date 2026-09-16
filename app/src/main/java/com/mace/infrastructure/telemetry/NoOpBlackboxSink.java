package com.mace.infrastructure.telemetry;

import com.mace.domain.model.TelemetrySnapshot;
import com.mace.domain.port.out.BlackboxSinkPort;

/**
 * Implementación del MVP para el hook de caja negra. No persiste información.
 */
public final class NoOpBlackboxSink implements BlackboxSinkPort {
    @Override
    public void dump(TelemetrySnapshot snapshot) {
        // Intencionalmente vacío: la caja negra pertenece a una fase futura.
    }
}
