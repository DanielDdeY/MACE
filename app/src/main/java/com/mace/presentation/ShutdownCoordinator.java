package com.mace.presentation;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One cleanup owner; no waits or native calls on the caller (JavaFX) thread. */
final class ShutdownCoordinator {
    private final AtomicBoolean started = new AtomicBoolean();

    boolean start(List<AutoCloseable> resources, Consumer<Throwable> completed) {
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
