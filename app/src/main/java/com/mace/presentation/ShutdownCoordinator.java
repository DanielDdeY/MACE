package com.mace.presentation;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

/** One cleanup owner; no waits or native calls on the caller (JavaFX) thread. */
final class ShutdownCoordinator {
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean armed = new AtomicBoolean();
    private final long budgetNanos;
    private final IntConsumer forceExit;
    static final int TIMEOUT_EXIT_CODE = 70;

    ShutdownCoordinator() {
        this(Duration.ofSeconds(10), status -> {
            // Do not log/flush here: even stderr or a shutdown hook could block.
            Runtime.getRuntime().halt(status);
        });
    }

    // Package-private injection: tests must never halt the shared test JVM.
    ShutdownCoordinator(Duration budget, IntConsumer forceExit) {
        if (budget.isZero() || budget.isNegative()) throw new IllegalArgumentException("Invalid shutdown budget");
        this.budgetNanos = budget.toNanos();
        this.forceExit = java.util.Objects.requireNonNull(forceExit);
    }

    void armWatchdog() {
        if (!armed.compareAndSet(false, true)) return;
        long deadline = System.nanoTime() + budgetNanos;
        Thread watchdog = new Thread(() -> {
            long remaining;
            while ((remaining = deadline - System.nanoTime()) > 0) {
                LockSupport.parkNanos(remaining);
                Thread.interrupted(); // Interrupts/spurious wakeups do not extend the deadline.
            }
            forceExit.accept(TIMEOUT_EXIT_CODE);
        }, "mace-shutdown-deadline");
        watchdog.setDaemon(true);
        watchdog.start();
        // Remains armed until the process actually exits, including stuck hooks
        // or a blocked completion callback after resource cleanup finished.
    }

    boolean start(List<AutoCloseable> resources, Consumer<Throwable> completed) {
        armWatchdog();
        if (!started.compareAndSet(false, true)) return false;
        Thread worker = new Thread(() -> {
            Throwable failure = null;
            for (AutoCloseable resource : resources) {
                try { resource.close(); }
                catch (Throwable error) {
                    if (failure == null) failure = error;
                    else if (failure != error) failure.addSuppressed(error);
                }
            }
            completed.accept(failure);
        }, "mace-shutdown");
        // Keep the JVM alive until adapters have made their bounded native close.
        worker.setDaemon(false);
        worker.start();
        return true;
    }

    boolean isStarted() { return started.get(); }
}
