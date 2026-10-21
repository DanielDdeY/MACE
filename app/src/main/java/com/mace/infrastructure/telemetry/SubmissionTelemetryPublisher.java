package com.mace.infrastructure.telemetry;

import com.mace.domain.model.MonitoringSnapshot;
import com.mace.domain.model.TelemetrySnapshot;
import com.mace.domain.port.out.MonitoringPublisherPort;
import com.mace.domain.port.out.TelemetryPublisherPort;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * Adaptador reactivo basado únicamente en la Flow API estándar de Java. Publica dos flujos:
 * el heredado ({@link TelemetrySnapshot}, mientras la presentación migra) y el de
 * {@link MonitoringSnapshot}, que comparten dashboard y Micro-HUD.
 *
 * <p>Suscribirse o cancelar no genera ni detiene sondeos: el monitor publica una vez por ciclo
 * y cada suscriptor recibe el mismo objeto. {@link #close()} completa ambos flujos; publicar
 * después se ignora.</p>
 */
public final class SubmissionTelemetryPublisher implements TelemetryPublisherPort, MonitoringPublisherPort, AutoCloseable {

    private final SubmissionPublisher<TelemetrySnapshot> delegate = new SubmissionPublisher<>();
    private final SubmissionPublisher<MonitoringSnapshot> monitoring = new SubmissionPublisher<>();

    @Override
    public void subscribe(Flow.Subscriber<? super TelemetrySnapshot> subscriber) {
        delegate.subscribe(Objects.requireNonNull(subscriber, "subscriber no puede ser null"));
    }

    @Override
    public void publish(TelemetrySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot no puede ser null");
        try {
            delegate.submit(snapshot);
        } catch (IllegalStateException closed) {
            // Publicador cerrado durante el apagado: el snapshot se descarta.
        }
    }

    @Override
    public void subscribeMonitoring(Flow.Subscriber<? super MonitoringSnapshot> subscriber) {
        monitoring.subscribe(Objects.requireNonNull(subscriber, "subscriber no puede ser null"));
    }

    @Override
    public void publish(MonitoringSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot no puede ser null");
        try {
            monitoring.submit(snapshot);
        } catch (IllegalStateException closed) {
            // Publicador cerrado durante el apagado: el snapshot se descarta.
        }
    }

    /** Suscriptores activos del flujo de monitoreo (diagnóstico del HUD). */
    public int monitoringSubscriberCount() {
        return monitoring.getNumberOfSubscribers();
    }

    @Override
    public void close() {
        delegate.close();
        monitoring.close();
    }
}
