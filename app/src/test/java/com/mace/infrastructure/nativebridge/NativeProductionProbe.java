package com.mace.infrastructure.nativebridge;

import com.mace.infrastructure.InfrastructureAdapters;
import com.mace.domain.model.MetricState;
import java.lang.foreign.Arena;
import java.nio.file.Path;
import java.util.Optional;

/** Controlled child process: no UI, blackbox, services, real helper or forced termination. */
public final class NativeProductionProbe {
    public static void main(String[] args) throws Exception {
        if (NativeLoadProfile.isDevelopment()) throw new AssertionError("Expected production metadata");
        String mode = args[0];
        if (mode.equals("valid")) {
            try (var functions = NativeFunctions.load(); var arena = Arena.ofConfined()) {
                if (!functions.libraryPath().equals(Path.of(System.getProperty("mace.native.lib"))))
                    throw new AssertionError("Unexpected DLL selection");
                // Isolated production DLL has no adjacent helper. Environment must not supply it.
                if (functions.ensureTelemetryInitialized() != NativeStatus.OK) throw new AssertionError("Initialization");
                var cpu = arena.allocate(NativeLayouts.CPU_TELEMETRY);
                long deadline = System.nanoTime() + 5_000_000_000L;
                do {
                    if (functions.pollCpuTelemetryV2(cpu, (int) NativeLayouts.CPU_TELEMETRY_SIZE) != NativeStatus.OK)
                        throw new AssertionError("Poll");
                    var reading = V2Records.decodeCpu(cpu);
                    if (reading.temperatureCelsius().state() == MetricState.HELPER_UNAVAILABLE && reading.usagePercent().isValid()) {
                        System.out.println("PASS production pins; foreign environment ignored; helper unavailable; Windows CPU valid");
                        return;
                    }
                    Thread.sleep(25);
                } while (System.nanoTime() < deadline);
                throw new AssertionError("Expected independent Windows metrics and absent adjacent helper");
            }
        }
        try {
            switch (mode) {
                case "explicit" -> NativeFunctions.load(Path.of(args[1]), Optional.empty());
                case "alternate" -> NativeFunctions.load(Path.of(args[1]), Optional.of("00".repeat(32)));
                case "auto" -> InfrastructureAdapters.create(InfrastructureAdapters.Mode.AUTO);
                default -> NativeFunctions.load();
            }
            throw new AssertionError("Production accepted an invalid configuration");
        } catch (NativeLibraryException rejected) {
            if (!NativeLoadProfile.PRODUCTION_FAILURE.equals(rejected.getMessage()) || rejected.getCause() != null)
                throw new AssertionError("Production failure must be generic without cause");
            System.out.println("PASS production closed; generic message; no cause");
        }
    }
}
