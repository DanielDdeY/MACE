package com.mace.presentation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ShutdownWatchdogProcessTest {
    @Test void blockedCleanupHaltsJvmAndOwnedHelper() throws Exception { run("blocked-cleanup", 70, 9000); }
    @Test void blockedShutdownHookCannotDefeatDeadline() throws Exception { run("blocked-hook", 70, 9000); }
    @Test void normalCleanupExitsWithoutUsingWatchdog() throws Exception { run("normal", 0, 0); }

    private void run(String mode, int expectedCode, long minimumMs) throws Exception {
        String helper = System.getenv("MACE_WATCHDOG_FAKE_HELPER");
        Assumptions.assumeTrue(helper != null && System.getenv("MACE_NATIVE_LIB") != null,
            "Requires compiled native DLL and fake helper; no hardware simulator is packaged");
        var command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
            "--enable-native-access=ALL-UNNAMED", "-cp",
            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
            ShutdownWatchdogProbe.class.getName(), mode);
        command.environment().put("MACE_LHM_HELPER", helper);
        command.environment().remove("MACE_LHM_HELPER_SHA256");
        command.environment().put("MACE_SIM_SCENARIO", "valid");
        command.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process child = command.start();
        ProcessHandle owned = null;
        try {
            var ready = CompletableFuture.supplyAsync(() -> {
                try { return child.inputReader().readLine(); }
                catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            });
            assertEquals("READY", ready.get(8, TimeUnit.SECONDS));
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (owned == null && System.nanoTime() < until) {
                owned = child.descendants().filter(process -> process.info().command()
                    .map(name -> Path.of(name).getFileName().toString().equalsIgnoreCase(Path.of(helper).getFileName().toString()))
                    .orElse(false)).findFirst().orElse(null);
                if (owned == null) Thread.sleep(10);
            }
            assertNotNull(owned, "Probe must own a helper before testing shutdown");
            assertTrue(owned.isAlive());
            long before = System.nanoTime();
            child.getOutputStream().write('G'); child.getOutputStream().flush();
            assertTrue(child.waitFor(15, TimeUnit.SECONDS));
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);
            assertEquals(expectedCode, child.exitValue());
            assertTrue(elapsed >= minimumMs && elapsed < 14000, "shutdown elapsed: " + elapsed);
            owned.onExit().get(3, TimeUnit.SECONDS);
            assertFalse(owned.isAlive(), "Closing the JVM's job handle must terminate its helper");
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(3, TimeUnit.SECONDS); }
            if (owned != null && owned.isAlive()) owned.onExit().get(3, TimeUnit.SECONDS);
        }
    }
}
