package com.mace.application.usecase;

import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.port.out.HardwareSensorsV2Port;
import com.mace.domain.port.out.TelemetryPublisherV2Port;

import java.util.Objects;

/**
 * Caso de uso: orquesta un ciclo de sondeo de telemetría de hardware V2.
 *
 * <p>Flujo:</p>
 * <ol>
 *   <li>Lee el estado actual con estado por métrica vía {@link HardwareSensorsV2Port}.</li>
 *   <li>Publica el {@link HardwareSnapshot} a los suscriptores reactivos vía
 *       {@link TelemetryPublisherV2Port}.</li>
 * </ol>
 *
 * <p>Esta clase NO decide ni ejecuta ninguna acción de cierre o suspensión de procesos:
 * solo lee y publica. La evaluación de alertas V2 (que solo considerará temperatura
 * {@link com.mace.domain.model.MetricState#VALID}) y la caja negra V2 se conectan en
 * una fase posterior coordinada, reutilizando los puntos de extensión existentes.</p>
 */
public final class PollSystemMetricsV2Service {

    private final HardwareSensorsV2Port hardwareSensorsV2Port;
    private final TelemetryPublisherV2Port telemetryPublisherV2Port;

    public PollSystemMetricsV2Service(
            HardwareSensorsV2Port hardwareSensorsV2Port,
            TelemetryPublisherV2Port telemetryPublisherV2Port
    ) {
        this.hardwareSensorsV2Port = Objects.requireNonNull(hardwareSensorsV2Port,
                "hardwareSensorsV2Port no puede ser null");
        this.telemetryPublisherV2Port = Objects.requireNonNull(telemetryPublisherV2Port,
                "telemetryPublisherV2Port no puede ser null");
    }

    /**
     * Ejecuta un ciclo de sondeo V2 completo (lectura + publicación).
     *
     * @return el snapshot V2 leído en este ciclo
     */
    public HardwareSnapshot pollOnce() {
        HardwareSnapshot snapshot = hardwareSensorsV2Port.fetchCurrentTelemetryV2();
        telemetryPublisherV2Port.publish(snapshot);
        return snapshot;
    }
}
