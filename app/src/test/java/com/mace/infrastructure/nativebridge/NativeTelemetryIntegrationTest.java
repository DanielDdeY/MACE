package com.mace.infrastructure.nativebridge;

import com.mace.domain.model.TelemetrySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class NativeTelemetryIntegrationTest {

    @Test
    void javaRecibeDatosRealesDesdeLaDll() {

        Path dll = NativeLibraryLocator.locate()
                .orElseThrow(() ->
                        new AssertionError("No se encontró mace_native.dll"));

        System.out.println("DLL encontrada:");
        System.out.println(dll);

        try (NativeFunctions nativeFunctions =
                     NativeFunctions.load(dll)) {

            // 1. Inicializar la telemetría nativa
            int initResult =
                    nativeFunctions.ensureTelemetryInitialized();

            assertEquals(
                    0,
                    initResult,
                    "CP_InitializeTelemetry falló"
            );

            // 2. Reservar memoria con EXACTAMENTE el layout de C
            try (Arena arena = Arena.ofConfined()) {

                MemorySegment telemetry =
                        arena.allocate(
                                NativeLayouts.TELEMETRY_DATA
                        );

                telemetry.fill((byte) 0);

                // 3. C llama a CP_PollTelemetry()
                int result =
                        nativeFunctions.pollTelemetry(telemetry);

                assertEquals(
                        0,
                        result,
                        "CP_PollTelemetry devolvió error"
                );

                // 4. Leer directamente lo que escribió C
                float cpuTemp =
                        telemetry.get(
                                JAVA_FLOAT,
                                NativeLayouts.TELEMETRY_CPU_TEMP_OFFSET
                        );

                float cpuWatts =
                        telemetry.get(
                                JAVA_FLOAT,
                                NativeLayouts.TELEMETRY_CPU_WATTS_OFFSET
                        );

                float gpuTemp =
                        telemetry.get(
                                JAVA_FLOAT,
                                NativeLayouts.TELEMETRY_GPU_TEMP_OFFSET
                        );

                float gpuWatts =
                        telemetry.get(
                                JAVA_FLOAT,
                                NativeLayouts.TELEMETRY_GPU_WATTS_OFFSET
                        );

                int gpuFan =
                        telemetry.get(
                                JAVA_INT,
                                NativeLayouts.TELEMETRY_GPU_FAN_RPM_OFFSET
                        );

                byte gpuAvailable =
                        telemetry.get(
                                JAVA_BYTE,
                                NativeLayouts.TELEMETRY_GPU_AVAILABLE_OFFSET
                        );

                System.out.println();
                System.out.println("=== DATOS RECIBIDOS DIRECTAMENTE DE C ===");
                System.out.println("CPU temperatura : " + cpuTemp);
                System.out.println("CPU watts       : " + cpuWatts);
                System.out.println("GPU temperatura : " + gpuTemp);
                System.out.println("GPU watts       : " + gpuWatts);
                System.out.println("GPU fan         : " + gpuFan);
                System.out.println("GPU disponible  : " + gpuAvailable);

                assertFalse(Float.isNaN(cpuTemp));
                assertFalse(Float.isNaN(cpuWatts));
                assertFalse(Float.isNaN(gpuTemp));
                assertFalse(Float.isNaN(gpuWatts));
            }

            /*
             * 5. Segunda prueba:
             *
             * C -> FFM -> FfmSensorsAdapter -> TelemetrySnapshot
             */
            FfmSensorsAdapter adapter =
                    new FfmSensorsAdapter(nativeFunctions);

            TelemetrySnapshot snapshot =
                    adapter.fetchCurrentTelemetry();

            System.out.println();
            System.out.println("=== OBJETO JAVA FINAL ===");
            System.out.println(snapshot);

            assertNotNull(snapshot);
            assertNotNull(snapshot.timestamp());

            System.out.println();
            System.out.println(
                    "JAVA ESTA RECIBIENDO CORRECTAMENTE LA TELEMETRIA."
            );
        }
    }
}