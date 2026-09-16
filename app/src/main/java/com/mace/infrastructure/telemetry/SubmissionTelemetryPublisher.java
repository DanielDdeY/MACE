package com.mace.infrastructure.telemetry;

import com.mace.domain.model.TelemetrySnapshot;
import com.mace.domain.port.out.TelemetryPublisherPort;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * Adaptador reactivo basado únicamente en la Flow API estándar de Java.
 */
public final class SubmissionTelemetryPublisher implements TelemetryPublisherPort, AutoCloseable {

    private final SubmissionPublisher<TelemetrySnapshot> delegate = new SubmissionPublisher<>();

    @Override
    public void subscribe(Flow.Subscriber<? super TelemetrySnapshot> subscriber) {
        delegate.subscribe(Objects.requireNonNull(subscriber, "subscriber no puede ser null"));
    }

    @Override
    public void publish(TelemetrySnapshot snapshot) {
        delegate.submit(Objects.requireNonNull(snapshot, "snapshot no puede ser null"));
    }

    @Override
    public void close() {
        delegate.close();
    }
}
