package com.mace.presentation;

import com.mace.infrastructure.nativebridge.NativeFunctions;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Only launched in a disposable JVM; exercises the production 10 s watchdog. */
public final class ShutdownWatchdogProbe {
    public static void main(String[] args) throws Exception {
        NativeFunctions nativeFunctions = NativeFunctions.load();
        if (nativeFunctions.ensureTelemetryInitialized() != 0) throw new IllegalStateException("Native init failed");
        if (args[0].equals("blocked-hook")) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }));
        }
        System.out.println("READY");
        if (System.in.read() != 'G') throw new IllegalStateException("Missing test signal");
        var coordinator = new ShutdownCoordinator();
        AutoCloseable block = () -> new CountDownLatch(1).await();
        coordinator.start(args[0].equals("blocked-cleanup") ? List.of(block, nativeFunctions) : List.of(nativeFunctions),
            failure -> System.exit(failure == null ? 0 : 1));
    }
}
