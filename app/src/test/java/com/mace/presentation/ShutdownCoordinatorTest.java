package com.mace.presentation;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ShutdownCoordinatorTest {
    private static ShutdownCoordinator coordinator() {
        return new ShutdownCoordinator(java.time.Duration.ofSeconds(10), code -> {});
    }
    @BeforeAll static void startFx() throws Exception {
        try { Platform.startup(() -> {}); }
        catch (IllegalStateException alreadyStarted) { /* Shared toolkit. */ }
        var ready = new CompletableFuture<Void>();
        Platform.runLater(() -> { Platform.setImplicitExit(false); ready.complete(null); });
        ready.get(5, TimeUnit.SECONDS);
    }

    @Test void nativeWaitDoesNotBlockFxAndStopCannotCloseTwice() throws Exception {
        var coordinator = coordinator();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var done = new CompletableFuture<Throwable>();
        var first = new CompletableFuture<Boolean>();
        AutoCloseable nativeClose = () -> {
            assertFalse(Platform.isFxApplicationThread());
            calls.incrementAndGet(); entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
        };
        try {
            Platform.runLater(() -> first.complete(coordinator.start(List.of(nativeClose), done::complete)));
            assertTrue(first.get(2, TimeUnit.SECONDS));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var responsive = new CompletableFuture<Boolean>();
            Platform.runLater(() -> responsive.complete(!coordinator.start(List.of(nativeClose), done::complete)));
            assertTrue(responsive.get(2, TimeUnit.SECONDS), "FX must process events while native close waits");
            assertFalse(done.isDone());
        } finally { release.countDown(); }
        assertNull(done.get(3, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
    }

    @Test void failureStillClosesRemainingResourcesInOrderOffFx() throws Exception {
        var coordinator = coordinator();
        var order = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var done = new CompletableFuture<Throwable>();
        var failure = new IllegalStateException("test failure");
        assertTrue(coordinator.start(List.of(
            () -> { order.add("monitor"); throw failure; },
            () -> { assertFalse(Platform.isFxApplicationThread()); order.add("publisher"); },
            () -> order.add("native")), done::complete));
        assertSame(failure, done.get(3, TimeUnit.SECONDS));
        assertEquals(List.of("monitor", "publisher", "native"), order);
    }

    @Test void concurrentExitRequestsHaveOneCleanupOwner() throws Exception {
        var coordinator = coordinator();
        var calls = new AtomicInteger();
        var done = new CompletableFuture<Throwable>();
        var unexpected = new AtomicReference<Throwable>();
        Thread[] threads = new Thread[12];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try { coordinator.start(List.of(calls::incrementAndGet), done::complete); }
                catch (Throwable error) { unexpected.set(error); }
            });
            threads[i].start();
        }
        for (Thread thread : threads) { thread.join(2000); assertFalse(thread.isAlive()); }
        assertNull(done.get(3, TimeUnit.SECONDS));
        assertNull(unexpected.get());
        assertEquals(1, calls.get());
    }

    @Test void watchdogFiresOnceWhileCleanupIsBlocked() throws Exception {
        var forced = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var count = new AtomicInteger();
        var done = new CompletableFuture<Throwable>();
        var coordinator = new ShutdownCoordinator(java.time.Duration.ofMillis(150), code -> {
            assertEquals(70, code); assertFalse(Platform.isFxApplicationThread());
            count.incrementAndGet(); forced.countDown();
        });
        try {
            coordinator.start(List.of(() -> release.await(3, TimeUnit.SECONDS)), done::complete);
            assertFalse(coordinator.start(List.of(), done::complete));
            assertTrue(forced.await(2, TimeUnit.SECONDS));
            assertFalse(done.isDone()); assertEquals(1, count.get());
        } finally { release.countDown(); }
        done.get(2, TimeUnit.SECONDS);
    }

    @Test void watchdogAlsoCoversBlockedCompletionAfterCleanup() throws Exception {
        var forced = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var completed = new CountDownLatch(1);
        var coordinator = new ShutdownCoordinator(java.time.Duration.ofMillis(150), code -> forced.countDown());
        try {
            coordinator.start(List.of(), failure -> {
                completed.countDown();
                try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            });
            assertTrue(completed.await(2, TimeUnit.SECONDS));
            assertTrue(forced.await(2, TimeUnit.SECONDS));
        } finally { release.countDown(); }
    }
}
