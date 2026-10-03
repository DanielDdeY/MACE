package com.mace.infrastructure.nativebridge;

import com.mace.domain.model.ProcessEntry;
import com.mace.domain.model.TelemetrySnapshot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeBridgeIntegrationTest {

    private static NativeFunctions nativeFunctions;
    private static FfmSensorsAdapter sensorsAdapter;
    private static FfmProcessAdapter processAdapter;

    @BeforeAll
    static void setUp() {
        Path dllPath = resolveDllPath();

        // Usa el método factoría estático de la clase
        if (dllPath != null && Files.isRegularFile(dllPath)) {
            nativeFunctions = NativeFunctions.load(dllPath);
        } else {
            nativeFunctions = NativeFunctions.load();
        }

        sensorsAdapter = new FfmSensorsAdapter(nativeFunctions);
        processAdapter = new FfmProcessAdapter(nativeFunctions);
    }

    @AfterAll
    static void tearDown() {
        if (nativeFunctions != null) {
            nativeFunctions.close();
        }
    }

    private static Path resolveDllPath() {
        List<Path> candidatePaths = List.of(
                Path.of("lib", "mace_native.dll"),
                Path.of("lib", "corepulse_native.dll"),
                Path.of("mace_native.dll"),
                Path.of("corepulse_native.dll"),
                Path.of("..", "out", "build", "x64-Debug", "mace_native.dll"),
                Path.of("..", "native", "out", "build", "x64-Debug", "mace_native.dll"),
                Path.of("..", "native", "build", "mace_native.dll"),
                Path.of("..", "native", "build", "Release", "mace_native.dll")
        );

        for (Path candidate : candidatePaths) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    @Test
    @DisplayName("Debe recibir telemetria valida desde C")
    void shouldFetchTelemetryFromNativeDll() {
        TelemetrySnapshot snapshot = sensorsAdapter.fetchCurrentTelemetry();

        assertNotNull(snapshot, "El snapshot no debe ser nulo");
        System.out.println("\n=== TELEMETRIA RECIBIDA EN JAVA ===");
        System.out.printf("CPU Temp : %.1f °C%n", snapshot.cpuTemp());
        System.out.printf("CPU Power: %.1f W%n", snapshot.cpuWatts());
        System.out.printf("GPU Detectada: %s%n", snapshot.gpuAvailable() ? "SI" : "NO (Aislamiento correcto)");

        assertTrue(snapshot.cpuTemp() >= 0.0f, "La temperatura de CPU debe ser coherente");
    }

    @Test
    @DisplayName("Debe listar procesos de usuario convertidos a Records de Java")
    void shouldListUserlandProcessesFromNativeDll() {
        List<ProcessEntry> processes = processAdapter.listUserlandProcesses();

        assertNotNull(processes, "La lista de procesos no debe ser nula");
        assertFalse(processes.isEmpty(), "Debe listar al menos un proceso de usuario");

        System.out.println("\n=== PROCESOS RECIBIDOS EN JAVA (PRIMEROS 5) ===");
        processes.stream().limit(5).forEach(proc -> {
            System.out.printf("PID: %-6d | RAM: %8.2f MB | Nombre: %s%n",
                    proc.pid().value(),
                    proc.memory().workingSetBytes() / (1024.0 * 1024.0),
                    proc.name());
        });

        boolean hasSystem32 = processes.stream()
                .anyMatch(p -> p.fullPath().toLowerCase().contains("\\system32\\"));
        assertFalse(hasSystem32, "El filtro de C debe excluir procesos de System32");
    }
}