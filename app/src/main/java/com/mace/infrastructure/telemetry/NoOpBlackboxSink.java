package com.mace.infrastructure.telemetry;

import com.mace.domain.model.BlackboxEvent;
import com.mace.domain.model.BlackboxStats;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.ProcessSample;
import com.mace.domain.port.out.BlackboxSinkPort;

/**
 * Sink sin persistencia: se usa cuando la caja negra está desactivada o no pudo abrirse. Informa
 * {@link BlackboxStats#disabled()} para que la UI no aparente un historial que no existe.
 */
public final class NoOpBlackboxSink implements BlackboxSinkPort {

    @Override
    public void record(HardwareSnapshot snapshot) {
        // Intencionalmente vacío.
    }

    @Override
    public void record(ProcessSample sample) {
        // Intencionalmente vacío.
    }

    @Override
    public void recordEvent(BlackboxEvent event) {
        // Intencionalmente vacío.
    }

    @Override
    public BlackboxStats stats() {
        return BlackboxStats.disabled();
    }
}
