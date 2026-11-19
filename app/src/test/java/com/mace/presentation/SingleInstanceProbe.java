package com.mace.presentation;

import com.mace.infrastructure.nativebridge.NativeFunctions;
import com.mace.infrastructure.nativebridge.NativeLayouts;
import java.lang.foreign.Arena;
import java.util.List;

/** Disposable real process; only test classes accept a namespace argument. */
public final class SingleInstanceProbe {
    public static void main(String[] args) throws Exception {
        var reservation = WindowsSingleInstance.acquire(args[0]);
        System.out.println(reservation.status() + ":" + reservation.error());
        if (reservation.status() != WindowsSingleInstance.Status.ACQUIRED || args[1].equals("attempt")) return;
        NativeFunctions functions = args[1].equals("native") ? NativeFunctions.load() : null;
        if (functions != null && functions.ensureTelemetryInitialized() != 0)
            throw new IllegalStateException("Native init failed");
        System.out.println("READY");
        while (true) {
            int command = System.in.read();
            switch (command) {
                case 'P' -> {
                    if (functions == null) throw new IllegalStateException("No telemetry in Preview");
                    try (Arena arena = Arena.ofConfined()) {
                        var sample = arena.allocate(NativeLayouts.CPU_TELEMETRY_SIZE);
                        System.out.println("POLL:" + functions.pollCpuTelemetryV2(sample, (int) sample.byteSize()));
                    }
                }
                case 'H' -> Runtime.getRuntime().halt(70);
                case 'C', 'W' -> {
                    System.out.println("CLOSING");
                    AutoCloseable slow = command == 'W' ? () -> new java.util.concurrent.CountDownLatch(1).await()
                            : () -> Thread.sleep(2500);
                    new ShutdownCoordinator().start(functions == null ? List.of(slow) : List.of(slow, functions),
                            error -> System.exit(error == null ? 0 : 1));
                    return;
                }
                case 'E', -1 -> { if (functions != null) functions.close(); return; }
                default -> throw new IllegalStateException("Invalid test command");
            }
        }
    }
}
