package com.mace.infrastructure.telemetry;

import com.mace.domain.DomainFixtures;
import com.mace.domain.model.BlackboxStats;
import com.mace.domain.model.MonitoringSnapshot;
import com.mace.domain.model.TelemetrySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dashboard y Micro-HUD comparten el mismo flujo; abrir o cerrar el HUD no altera los datos. */
class SubmissionTelemetryPublisherTest {

    private static MonitoringSnapshot snapshot(long sequence) {
        return new MonitoringSnapshot(sequence, sequence, DomainFixtures.snapshot(DomainFixtures.cpu(50)), List.of(),
                Optional.empty(), List.of(), List.of(), Map.of(), BlackboxStats.disabled());
    }

    @Test
    void dashboardYHudRecibenElMismoObjetoYCerrarElHudNoAfectaAlDashboard() throws InterruptedException {
        try (SubmissionTelemetryPublisher publisher = new SubmissionTelemetryPublisher()) {
            Collector dashboard = new Collector(2);
            Collector hud = new Collector(1);
            publisher.subscribeMonitoring(dashboard);
            publisher.subscribeMonitoring(hud);

            MonitoringSnapshot first = snapshot(1);
            publisher.publish(first);
            assertTrue(hud.await());
            hud.cancel();

            publisher.publish(snapshot(2));
            assertTrue(dashboard.await());

            assertSame(first, hud.received.get(0));
            assertSame(first, dashboard.received.get(0));
            assertEquals(List.of(1L, 2L), dashboard.received.stream().map(MonitoringSnapshot::sequence).toList());
            assertEquals(1, hud.received.size(), "el HUD cerrado no recibe más ni genera sondeos");
        }
    }

    @Test
    void publicarTrasCerrarSeIgnora() {
        SubmissionTelemetryPublisher publisher = new SubmissionTelemetryPublisher();
        publisher.close();

        assertDoesNotThrow(() -> publisher.publish(snapshot(1)));
        assertDoesNotThrow(() -> publisher.publish(new TelemetrySnapshot(1, 1, 1, 1, 1, 1, true, DomainFixtures.NOW)));
    }

    private static final class Collector implements Flow.Subscriber<MonitoringSnapshot> {
        private final List<MonitoringSnapshot> received = new CopyOnWriteArrayList<>();
        private final CountDownLatch latch;
        private Flow.Subscription subscription;

        Collector(int expected) {
            latch = new CountDownLatch(expected);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(MonitoringSnapshot item) {
            received.add(item);
            latch.countDown();
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onComplete() {
        }

        boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }

        void cancel() {
            subscription.cancel();
        }
    }
}
