package com.mace.presentation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import java.io.BufferedReader;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SingleInstanceProcessTest {
    @Test void nameSeparatesUsersAndSessionsWithoutExposingSid() throws Exception {
        String name = WindowsSingleInstance.objectName("MACE.Instance.v1", "S-1-5-21-123", 1);
        assertTrue(name.startsWith("Local\\MACE.Instance.v1."));
        assertFalse(name.contains("S-1-5"));
        assertNotEquals(name, WindowsSingleInstance.objectName("MACE.Instance.v1", "S-1-5-21-124", 1));
        assertNotEquals(name, WindowsSingleInstance.objectName("MACE.Instance.v1", "S-1-5-21-123", 2));
    }

    @Test void simultaneousContendersHaveExactlyOneWinner() throws Exception {
        String scope = scope();
        try (var first = launch(scope, "preview"); var second = launch(scope, "preview")) {
            String a = first.line(), b = second.line();
            assertTrue((a.equals("ACQUIRED:0") && b.equals("BUSY:183"))
                    || (b.equals("ACQUIRED:0") && a.equals("BUSY:183")), a + " / " + b);
            var winner = a.equals("ACQUIRED:0") ? first : second;
            var loser = winner == first ? second : first;
            assertEquals("READY", winner.line());
            loser.expectExit(0);
            noTelemetryChildren(loser);
            winner.send('E'); winner.expectExit(0);
        }
        assertAvailable(scope);
    }

    @Test void nativeWinnerUnaffectedBySecondNativeOrPreview() throws Exception {
        requireNative();
        String scope = scope();
        try (var owner = launch(scope, "native")) {
            owner.ready();
            ProcessHandle helper = helper(owner);
            for (String mode : new String[]{"native", "preview"}) {
                try (var other = launch(scope, mode, true)) {
                    assertEquals("BUSY:183", other.line()); other.expectExit(0);
                    noTelemetryChildren(other);
                }
                assertTrue(helper.isAlive());
                owner.send('P'); assertEquals("POLL:0", owner.line());
            }
            owner.send('E'); owner.expectExit(0); gone(helper);
        }
        assertAvailable(scope);
    }

    @Test void previewWinnerRejectsNativeBeforeLoadingItsDll() throws Exception {
        String scope = scope();
        try (var owner = launch(scope, "preview")) {
            owner.ready();
            try (var other = launch(scope, "native", true)) {
                assertEquals("BUSY:183", other.line()); other.expectExit(0);
            }
            noTelemetryChildren(owner);
            owner.send('E'); owner.expectExit(0);
        }
    }

    @Test void forcedKillReleasesReservationAndKillsOnlyOwnedHelper() throws Exception { death(false); }
    @Test void runtimeHaltReleasesReservationAndKillsOwnedHelper() throws Exception { death(true); }

    @Test void reservationRemainsDuringSlowCloseAndIsReleasedAtProcessExit() throws Exception {
        requireNative();
        String scope = scope();
        try (var owner = launch(scope, "native")) {
            owner.ready(); ProcessHandle helper = helper(owner);
            owner.send('C'); assertEquals("CLOSING", owner.line());
            try (var other = launch(scope, "native", true)) {
                assertEquals("BUSY:183", other.line()); other.expectExit(0);
            }
            assertTrue(owner.process.isAlive()); assertTrue(helper.isAlive());
            owner.expectExit(0); gone(helper);
        }
        assertAvailable(scope);
    }

    @Test void watchdogRetainsReservationUntilHaltAndReleasesItWithTheJob() throws Exception {
        requireNative(); String scope = scope();
        try (var owner = launch(scope, "native")) {
            owner.ready(); ProcessHandle helper = helper(owner);
            long before = System.nanoTime();
            owner.send('W'); assertEquals("CLOSING", owner.line());
            try (var other = launch(scope, "native", true)) {
                assertEquals("BUSY:183", other.line()); other.expectExit(0);
            }
            assertTrue(helper.isAlive());
            assertTrue(owner.process.waitFor(14, TimeUnit.SECONDS));
            assertEquals(70, owner.process.exitValue());
            assertTrue(System.nanoTime() - before >= TimeUnit.SECONDS.toNanos(9));
            gone(helper);
        }
        assertAvailable(scope);
    }

    private void death(boolean halt) throws Exception {
        requireNative(); String scope = scope();
        try (var owner = launch(scope, "native")) {
            owner.ready(); ProcessHandle helper = helper(owner);
            if (halt) { owner.send('H'); owner.expectExit(70); }
            else { owner.process.destroyForcibly(); assertTrue(owner.process.waitFor(5, TimeUnit.SECONDS)); }
            gone(helper);
        }
        assertAvailable(scope);
    }
    private static void requireNative() {
        Assumptions.assumeTrue(System.getenv("MACE_NATIVE_LIB") != null
                && System.getenv("MACE_WATCHDOG_FAKE_HELPER") != null, "Requires development DLL and simulated helper");
    }
    private static void noTelemetryChildren(Probe probe) {
        // Windows can attach a console host to java.exe; it is not a telemetry helper.
        var unexpected = probe.process.descendants().map(p -> p.info().command().orElse("unknown"))
                .filter(c -> !Path.of(c).getFileName().toString().equalsIgnoreCase("conhost.exe")).toList();
        assertTrue(unexpected.isEmpty(), unexpected.toString());
    }
    private static String scope() { return "MACE.Test." + UUID.randomUUID(); }
    private static Probe launch(String scope, String mode) throws Exception { return launch(scope, mode, false); }
    private static Probe launch(String scope, String mode, boolean rejectNativeLoad) throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "--enable-native-access=ALL-UNNAMED", "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                SingleInstanceProbe.class.getName(), scope, mode);
        if (rejectNativeLoad) builder.environment().put("MACE_NATIVE_LIB", "Z:\\missing-test-dll.dll");
        String helper = System.getenv("MACE_WATCHDOG_FAKE_HELPER");
        if (helper != null) builder.environment().put("MACE_LHM_HELPER", helper);
        builder.environment().remove("MACE_LHM_HELPER_SHA256");
        builder.environment().put("MACE_SIM_SCENARIO", "valid");
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        return new Probe(builder.start());
    }
    private static void assertAvailable(String scope) throws Exception {
        try (var next = launch(scope, "attempt")) { assertEquals("ACQUIRED:0", next.line()); next.expectExit(0); }
    }
    private static ProcessHandle helper(Probe owner) throws Exception {
        String expected = Path.of(System.getenv("MACE_WATCHDOG_FAKE_HELPER")).getFileName().toString();
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            var child = owner.process.descendants().filter(p -> p.info().command()
                    .map(c -> Path.of(c).getFileName().toString().equalsIgnoreCase(expected)).orElse(false)).findFirst();
            if (child.isPresent()) return child.get();
            Thread.sleep(10);
        } while (System.nanoTime() < until);
        fail("Missing owned helper"); return null;
    }
    private static void gone(ProcessHandle child) throws Exception {
        child.onExit().get(4, TimeUnit.SECONDS); assertFalse(child.isAlive());
    }
    private static final class Probe implements AutoCloseable {
        final Process process;
        final BufferedReader reader;
        Probe(Process process) { this.process = process; reader = process.inputReader(); }
        String line() throws Exception {
            return CompletableFuture.supplyAsync(() -> {
                try { return reader.readLine(); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }).get(8, TimeUnit.SECONDS);
        }
        void ready() throws Exception { assertEquals("ACQUIRED:0", line()); assertEquals("READY", line()); }
        void send(char command) throws Exception { process.getOutputStream().write(command); process.getOutputStream().flush(); }
        void expectExit(int code) throws Exception { assertTrue(process.waitFor(8, TimeUnit.SECONDS)); assertEquals(code, process.exitValue()); }
        public void close() throws Exception {
            if (process.isAlive()) { process.destroyForcibly(); assertTrue(process.waitFor(4, TimeUnit.SECONDS)); }
        }
    }
}
