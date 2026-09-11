package com.corepulse.infrastructure.nativebridge;

import com.corepulse.domain.model.TelemetrySnapshot;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.time.Instant;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba la conversión de {@code TelemetryData} nativo a {@link TelemetrySnapshot} sobre
 * segmentos construidos en memoria nativa con el layout de {@code mace_types.h}; no requiere la DLL.
 */
class FfmSensorsAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Test
    void decodificaTelemetriaConGpuDisponible() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment telemetry = newTelemetry(arena, 61.5f, 45.25f, 70.0f, 180.5f, 1500, true);

            TelemetrySnapshot snapshot = FfmSensorsAdapter.toSnapshot(telemetry, NOW);

            assertEquals(61.5f, snapshot.cpuTemp());
            assertEquals(45.25f, snapshot.cpuWatts());
            assertEquals(70.0f, snapshot.gpuTemp());
            assertEquals(180.5f, snapshot.gpuWatts());
            assertEquals(1500, snapshot.gpuFanRpm());
            assertTrue(snapshot.gpuAvailable());
            assertEquals(NOW, snapshot.timestamp());
        }
    }

    @Test
    void normalizaLosCamposDeGpuCuandoNoEstaDisponible() {
        try (Arena arena = Arena.ofConfined()) {
            // Valores residuales en los campos de GPU: el dominio no debe verlos.
            MemorySegment telemetry = newTelemetry(arena, 42.0f, 25.5f, 999f, 999f, 9999, false);

            TelemetrySnapshot snapshot = FfmSensorsAdapter.toSnapshot(telemetry, NOW);

            assertEquals(42.0f, snapshot.cpuTemp());
            assertEquals(25.5f, snapshot.cpuWatts());
            assertEquals(0f, snapshot.gpuTemp());
            assertEquals(0f, snapshot.gpuWatts());
            assertEquals(0, snapshot.gpuFanRpm());
            assertFalse(snapshot.gpuAvailable());
        }
    }

    @Test
    void cualquierValorDistintoDeCeroEnIsGpuAvailableCuentaComoDisponible() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment telemetry = newTelemetry(arena, 40f, 20f, 50f, 100f, 800, true);
            telemetry.set(JAVA_BYTE, NativeLayouts.TELEMETRY_GPU_AVAILABLE_OFFSET, (byte) 0xFF);

            assertTrue(FfmSensorsAdapter.toSnapshot(telemetry, NOW).gpuAvailable());
        }
    }

    @Test
    void rpmUint32PorEncimaDeIntegerMaxSeSatura() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment telemetry = newTelemetry(arena, 40f, 20f, 50f, 100f, 0, true);
            telemetry.set(JAVA_INT, NativeLayouts.TELEMETRY_GPU_FAN_RPM_OFFSET, 0xFFFF_FFFF);

            assertEquals(Integer.MAX_VALUE, FfmSensorsAdapter.toSnapshot(telemetry, NOW).gpuFanRpm());
        }
    }

    @Test
    void elConstructorValidaSusArgumentos() {
        assertThrows(NullPointerException.class, () -> new FfmSensorsAdapter(null));
    }

    // ------------------------------------------------------------------

    /** Escribe el registro exactamente como lo hace {@code CP_PollTelemetry} en {@code mace_api.c}. */
    private static MemorySegment newTelemetry(Arena arena, float cpuTemp, float cpuWatts, float gpuTemp,
                                              float gpuWatts, int fanRpm, boolean gpuAvailable) {
        MemorySegment telemetry = arena.allocate(NativeLayouts.TELEMETRY_DATA);
        telemetry.fill((byte) 0);
        telemetry.set(JAVA_FLOAT, NativeLayouts.TELEMETRY_CPU_TEMP_OFFSET, cpuTemp);
        telemetry.set(JAVA_FLOAT, NativeLayouts.TELEMETRY_CPU_WATTS_OFFSET, cpuWatts);
        telemetry.set(JAVA_FLOAT, NativeLayouts.TELEMETRY_GPU_TEMP_OFFSET, gpuTemp);
        telemetry.set(JAVA_FLOAT, NativeLayouts.TELEMETRY_GPU_WATTS_OFFSET, gpuWatts);
        telemetry.set(JAVA_INT, NativeLayouts.TELEMETRY_GPU_FAN_RPM_OFFSET, fanRpm);
        telemetry.set(JAVA_BYTE, NativeLayouts.TELEMETRY_GPU_AVAILABLE_OFFSET, (byte) (gpuAvailable ? 1 : 0));
        return telemetry;
    }
}
