package com.corepulse.infrastructure.mock;

import com.corepulse.domain.model.TelemetrySnapshot;
import com.corepulse.domain.port.out.HardwareSensorsPort;

import java.time.Clock;
import java.util.Objects;
import java.util.Random;

/**
 * Implementación simulada de {@link HardwareSensorsPort} para desarrollar y probar la UI y el
 * dominio sin la DLL nativa (ni siquiera hace falta Windows).
 *
 * <p>Genera series plausibles: cada lectura es un paseo aleatorio con reversión a la media del
 * {@link Profile} elegido, los vatios se derivan de la temperatura y el ventilador de GPU se
 * detiene por debajo de 40 °C (fan-stop). Con la misma semilla la secuencia es determinista,
 * lo que permite pruebas repetibles.</p>
 *
 * <p>Es seguro para uso concurrente (una sola instancia puede sondearse desde un hilo de fondo).</p>
 */
public final class MockSensorsAdapter implements HardwareSensorsPort {

    /** Régimen térmico alrededor del cual oscila la simulación. */
    public enum Profile {
        /** Escritorio en reposo/ofimática: sin alertas la mayor parte del tiempo. */
        NORMAL(52f, 46f, 4.0f),
        /** Carga sostenida (juego, compilación): roza el umbral WARNING (75 °C). */
        HEAVY_LOAD(78f, 76f, 3.0f),
        /** Sobrecalentamiento: supera el umbral CRITICAL (85 °C). */
        CRITICAL(89f, 88f, 2.0f);

        private final float cpuMean;
        private final float gpuMean;
        private final float jitter;

        Profile(float cpuMean, float gpuMean, float jitter) {
            this.cpuMean = cpuMean;
            this.gpuMean = gpuMean;
            this.jitter = jitter;
        }

        public float cpuMean() {
            return cpuMean;
        }

        public float gpuMean() {
            return gpuMean;
        }
    }

    public static final float CPU_TEMP_MIN = 30f;
    public static final float CPU_TEMP_MAX = 99f;
    public static final float GPU_TEMP_MIN = 28f;
    public static final float GPU_TEMP_MAX = 95f;
    public static final float CPU_WATTS_MAX = 180f;
    public static final float GPU_WATTS_MAX = 320f;
    public static final int GPU_FAN_RPM_MAX = 3600;

    private static final float MEAN_REVERSION = 0.12f;
    private static final float FAN_STOP_BELOW_CELSIUS = 40f;

    private final Profile profile;
    private final boolean gpuAvailable;
    private final Random random;
    private final Clock clock;

    private float cpuTemp;
    private float gpuTemp;

    /** Perfil {@link Profile#NORMAL}, GPU presente, semilla aleatoria. */
    public MockSensorsAdapter() {
        this(Profile.NORMAL, true, System.nanoTime(), Clock.systemUTC());
    }

    public MockSensorsAdapter(Profile profile) {
        this(profile, true, System.nanoTime(), Clock.systemUTC());
    }

    /**
     * @param profile      régimen térmico simulado
     * @param gpuAvailable {@code false} para simular un equipo sin NVML (campos de GPU en cero)
     * @param seed         semilla del generador; misma semilla ⇒ misma secuencia
     * @param clock        reloj para las marcas de tiempo
     */
    public MockSensorsAdapter(Profile profile, boolean gpuAvailable, long seed, Clock clock) {
        this.profile = Objects.requireNonNull(profile, "profile no puede ser null");
        this.gpuAvailable = gpuAvailable;
        this.random = new Random(seed);
        this.clock = Objects.requireNonNull(clock, "clock no puede ser null");
        this.cpuTemp = profile.cpuMean;
        this.gpuTemp = profile.gpuMean;
    }

    /** Equipo sin GPU NVIDIA: {@code gpuAvailable == false} en cada lectura. */
    public static MockSensorsAdapter withoutGpu() {
        return new MockSensorsAdapter(Profile.NORMAL, false, System.nanoTime(), Clock.systemUTC());
    }

    public Profile profile() {
        return profile;
    }

    public boolean gpuAvailable() {
        return gpuAvailable;
    }

    @Override
    public synchronized TelemetrySnapshot fetchCurrentTelemetry() {
        cpuTemp = step(cpuTemp, profile.cpuMean, CPU_TEMP_MIN, CPU_TEMP_MAX);
        float cpuWatts = clamp(8f + (cpuTemp - CPU_TEMP_MIN) * 1.8f + gaussian(2.5f), 5f, CPU_WATTS_MAX);

        if (!gpuAvailable) {
            return new TelemetrySnapshot(round1(cpuTemp), round1(cpuWatts), 0f, 0f, 0, false, clock.instant());
        }

        gpuTemp = step(gpuTemp, profile.gpuMean, GPU_TEMP_MIN, GPU_TEMP_MAX);
        float gpuWatts = clamp(12f + (gpuTemp - GPU_TEMP_MIN) * 4.0f + gaussian(6f), 5f, GPU_WATTS_MAX);
        int gpuFanRpm = gpuTemp < FAN_STOP_BELOW_CELSIUS
                ? 0
                : (int) clamp(650f + (gpuTemp - FAN_STOP_BELOW_CELSIUS) * 48f + gaussian(60f), 0f, GPU_FAN_RPM_MAX);

        return new TelemetrySnapshot(
                round1(cpuTemp), round1(cpuWatts),
                round1(gpuTemp), round1(gpuWatts),
                gpuFanRpm, true, clock.instant());
    }

    private float step(float current, float mean, float min, float max) {
        float next = current + MEAN_REVERSION * (mean - current) + gaussian(profile.jitter);
        return clamp(next, min, max);
    }

    private float gaussian(float sigma) {
        return (float) random.nextGaussian() * sigma;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float round1(float value) {
        return Math.round(value * 10f) / 10f;
    }
}
