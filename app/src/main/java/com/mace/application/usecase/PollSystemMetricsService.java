package com.mace.application.usecase;

import com.mace.domain.model.AlertLevel;
import com.mace.domain.model.BlackboxEvent;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.TelemetrySnapshot;
import com.mace.domain.model.ThermalAlert;
import com.mace.domain.port.out.BlackboxSinkPort;
import com.mace.domain.port.out.HardwareSensorsPort;
import com.mace.domain.port.out.TelemetryPublisherPort;
import com.mace.domain.service.ThermalAlertEvaluator;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Caso de uso: orquesta un ciclo de sondeo ("poll") de telemetría de hardware.
 *
 * Flujo de {@link #pollHardware()}:
 * <ol>
 *   <li>Lee CPU, cada GPU y memoria vía {@link HardwareSensorsPort} (un único sondeo físico).</li>
 *   <li>Publica la conversión heredada a los suscriptores de {@link TelemetryPublisherPort},
 *       para que la pantalla actual siga funcionando mientras migra.</li>
 *   <li>Evalúa umbrales térmicos por CPU y por GPU vía {@link ThermalAlertEvaluator}.</li>
 *   <li>Registra el snapshot en la caja negra y cada cambio de nivel de alerta como evento.</li>
 * </ol>
 *
 * Esta clase NO decide ni ejecuta ninguna acción de cierre o suspensión de procesos:
 * solo informa. Cualquier acción correctiva queda exclusivamente en manos del usuario,
 * a través de {@link TerminateProcessService}.
 */
public final class PollSystemMetricsService {

    private static final Logger LOG = System.getLogger(PollSystemMetricsService.class.getName());

    private final HardwareSensorsPort hardwareSensorsPort;
    private final TelemetryPublisherPort telemetryPublisherPort;
    private final BlackboxSinkPort blackboxSinkPort;
    private final ThermalAlertEvaluator thermalAlertEvaluator;
    private final Map<String, AlertLevel> lastLevels = new HashMap<>();

    public PollSystemMetricsService(
            HardwareSensorsPort hardwareSensorsPort,
            TelemetryPublisherPort telemetryPublisherPort,
            BlackboxSinkPort blackboxSinkPort,
            ThermalAlertEvaluator thermalAlertEvaluator
    ) {
        this.hardwareSensorsPort = Objects.requireNonNull(hardwareSensorsPort, "hardwareSensorsPort no puede ser null");
        this.telemetryPublisherPort = Objects.requireNonNull(telemetryPublisherPort, "telemetryPublisherPort no puede ser null");
        this.blackboxSinkPort = Objects.requireNonNull(blackboxSinkPort, "blackboxSinkPort no puede ser null");
        this.thermalAlertEvaluator = Objects.requireNonNull(thermalAlertEvaluator, "thermalAlertEvaluator no puede ser null");
    }

    /**
     * Ejecuta un ciclo de sondeo completo.
     *
     * @return snapshot V2 y alertas térmicas del ciclo
     */
    public synchronized HardwarePoll pollHardware() {
        HardwareSnapshot snapshot = hardwareSensorsPort.fetchHardwareSnapshot();

        telemetryPublisherPort.publish(TelemetrySnapshot.fromHardwareSnapshot(snapshot));

        List<ThermalAlert> alerts = thermalAlertEvaluator.evaluate(snapshot);

        try {
            blackboxSinkPort.record(snapshot);
            recordLevelChanges(alerts);
        } catch (RuntimeException e) {
            // La caja negra nunca interrumpe el sondeo ni la publicación.
            LOG.log(Level.WARNING, "No se pudo registrar el sondeo en la caja negra", e);
        }

        return new HardwarePoll(snapshot, alerts);
    }

    /**
     * Compatibilidad con el planificador actual de la presentación: mismo ciclo que
     * {@link #pollHardware()}.
     *
     * @return las alertas térmicas activas detectadas en este ciclo (puede estar vacía)
     */
    public List<ThermalAlert> pollOnce() {
        return pollHardware().thermalAlerts();
    }

    /** Registra en la caja negra solo las transiciones de nivel por fuente, no cada segundo. */
    private void recordLevelChanges(List<ThermalAlert> alerts) {
        Map<String, AlertLevel> current = new HashMap<>();
        for (ThermalAlert alert : alerts) {
            current.put(alert.source(), alert.level());
            if (lastLevels.get(alert.source()) != alert.level()) {
                blackboxSinkPort.recordEvent(BlackboxEvent.thermal(alert));
            }
        }
        lastLevels.clear();
        lastLevels.putAll(current);
    }
}
