package com.corepulse.application.usecase;

import com.corepulse.domain.model.AlertLevel;
import com.corepulse.domain.model.TelemetrySnapshot;
import com.corepulse.domain.model.ThermalAlert;
import com.corepulse.domain.port.out.BlackboxSinkPort;
import com.corepulse.domain.port.out.HardwareSensorsPort;
import com.corepulse.domain.port.out.TelemetryPublisherPort;
import com.corepulse.domain.service.ThermalAlertEvaluator;

import java.util.List;
import java.util.Objects;

/**
 * Caso de uso: orquesta un ciclo de sondeo ("poll") de telemetría de hardware.
 *
 * Flujo:
 * <ol>
 *   <li>Lee el estado actual vía {@link HardwareSensorsPort}.</li>
 *   <li>Publica el snapshot a los suscriptores reactivos vía {@link TelemetryPublisherPort}.</li>
 *   <li>Evalúa umbrales térmicos vía {@link ThermalAlertEvaluator}.</li>
 *   <li>Si existe al menos una alerta CRITICAL, persiste el snapshot vía {@link BlackboxSinkPort}.</li>
 * </ol>
 *
 * Esta clase NO decide ni ejecuta ninguna acción de cierre o suspensión de procesos:
 * solo informa. Cualquier acción correctiva queda exclusivamente en manos del usuario,
 * a través de {@link TerminateProcessService}.
 */
public final class PollSystemMetricsService {

    private final HardwareSensorsPort hardwareSensorsPort;
    private final TelemetryPublisherPort telemetryPublisherPort;
    private final BlackboxSinkPort blackboxSinkPort;
    private final ThermalAlertEvaluator thermalAlertEvaluator;

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
     * @return las alertas térmicas activas detectadas en este ciclo (puede estar vacía)
     */
    public List<ThermalAlert> pollOnce() {
        TelemetrySnapshot snapshot = hardwareSensorsPort.fetchCurrentTelemetry();

        telemetryPublisherPort.publish(snapshot);

        List<ThermalAlert> alerts = thermalAlertEvaluator.evaluate(snapshot);

        boolean hasCriticalAlert = alerts.stream().anyMatch(alert -> alert.level() == AlertLevel.CRITICAL);
        if (hasCriticalAlert) {
            blackboxSinkPort.dump(snapshot);
        }

        return alerts;
    }
}
