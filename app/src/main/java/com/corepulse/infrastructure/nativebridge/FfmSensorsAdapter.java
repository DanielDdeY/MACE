package com.corepulse.infrastructure.nativebridge;

import com.corepulse.domain.model.TelemetrySnapshot;
import com.corepulse.domain.port.out.HardwareSensorsPort;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Adaptador de salida que implementa {@link HardwareSensorsPort} leyendo la telemetría real
 * (CPU vía RAPL/MSR, GPU vía NVML) a través de la DLL nativa.
 *
 * <p>Cada llamada usa un {@code Arena.ofConfined()} propio: los 24 bytes de
 * {@code TelemetryData} viven fuera del heap solo durante la invocación y se liberan de forma
 * determinista al salir del bloque, sin generar presión en el GC.</p>
 *
 * <p>El adaptador solo lee e informa; no toma ninguna acción sobre procesos.</p>
 */
public final class FfmSensorsAdapter implements HardwareSensorsPort {

    private final NativeFunctions nativeFunctions;
    private final Clock clock;

    /**
     * @param nativeFunctions enlace FFM ya cargado (la telemetría se inicializa aquí si hace falta)
     * @throws NativeLibraryException si {@code CP_InitializeTelemetry} falla
     */
    public FfmSensorsAdapter(NativeFunctions nativeFunctions) {
        this(nativeFunctions, Clock.systemUTC());
    }

    public FfmSensorsAdapter(NativeFunctions nativeFunctions, Clock clock) {
        this.nativeFunctions = Objects.requireNonNull(nativeFunctions, "nativeFunctions no puede ser null");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser null");
        int result = nativeFunctions.ensureTelemetryInitialized();
        if (result != 0) {
            throw new NativeLibraryException(NativeFunctions.SYMBOL_INITIALIZE_TELEMETRY
                    + " devolvió el código " + result);
        }
    }

    /**
     * @return lectura actual de sensores con marca de tiempo del reloj configurado
     * @throws NativeLibraryException si la DLL reporta error o la llamada nativa falla
     */
    @Override
    public TelemetrySnapshot fetchCurrentTelemetry() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment telemetry = arena.allocate(NativeLayouts.TELEMETRY_DATA);
            telemetry.fill((byte) 0);
            int result = nativeFunctions.pollTelemetry(telemetry);
            if (result != 0) {
                throw new NativeLibraryException(NativeFunctions.SYMBOL_POLL_TELEMETRY
                        + " devolvió el código " + result);
            }
            return toSnapshot(telemetry, clock.instant());
        }
    }

    /**
     * Convierte un registro {@code TelemetryData} nativo en el modelo de dominio.
     * Cuando la GPU no está disponible, sus campos se normalizan a cero para que el dominio no
     * reciba valores residuales.
     *
     * @param telemetry segmento con al menos {@link NativeLayouts#TELEMETRY_DATA_SIZE} bytes
     * @param timestamp instante a asociar a la lectura
     */
    static TelemetrySnapshot toSnapshot(MemorySegment telemetry, Instant timestamp) {
        Objects.requireNonNull(telemetry, "telemetry no puede ser null");
        float cpuTemp = telemetry.get(JAVA_FLOAT, NativeLayouts.TELEMETRY_CPU_TEMP_OFFSET);
        float cpuWatts = telemetry.get(JAVA_FLOAT, NativeLayouts.TELEMETRY_CPU_WATTS_OFFSET);
        boolean gpuAvailable = telemetry.get(JAVA_BYTE, NativeLayouts.TELEMETRY_GPU_AVAILABLE_OFFSET) != 0;

        float gpuTemp = 0f;
        float gpuWatts = 0f;
        int gpuFanRpm = 0;
        if (gpuAvailable) {
            gpuTemp = telemetry.get(JAVA_FLOAT, NativeLayouts.TELEMETRY_GPU_TEMP_OFFSET);
            gpuWatts = telemetry.get(JAVA_FLOAT, NativeLayouts.TELEMETRY_GPU_WATTS_OFFSET);
            gpuFanRpm = toNonNegativeInt(telemetry.get(JAVA_INT, NativeLayouts.TELEMETRY_GPU_FAN_RPM_OFFSET));
        }
        return new TelemetrySnapshot(cpuTemp, cpuWatts, gpuTemp, gpuWatts, gpuFanRpm, gpuAvailable, timestamp);
    }

    /** Un {@code uint32_t} mayor que {@code Integer.MAX_VALUE} se satura en lugar de volverse negativo. */
    private static int toNonNegativeInt(int unsignedValue) {
        return unsignedValue < 0 ? Integer.MAX_VALUE : unsignedValue;
    }
}
