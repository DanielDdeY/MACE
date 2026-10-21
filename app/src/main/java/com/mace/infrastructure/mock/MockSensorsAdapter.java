package com.mace.infrastructure.mock;

import com.mace.domain.model.CpuReading;
import com.mace.domain.model.CpuVendor;
import com.mace.domain.model.GpuAdapter;
import com.mace.domain.model.GpuAdapterId;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.LongMetricReading;
import com.mace.domain.model.MetricReading;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.PowerScope;
import com.mace.domain.model.SensorProvider;
import com.mace.domain.model.SystemMemoryReading;
import com.mace.domain.port.out.HardwareSensorsPort;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.LongSupplier;

/**
 * Implementación simulada de {@link HardwareSensorsPort} con el mismo contrato V2 que la DLL:
 * estado individual por métrica, varias GPU con LUID, proveedor y ámbito de potencia. Permite
 * desarrollar y probar la UI sin la DLL (ni siquiera hace falta Windows) y no requiere
 * administrador.
 *
 * <p>Cada lectura es un paseo aleatorio con reversión a la media del {@link MockScenario}. Con la
 * misma semilla la secuencia es determinista. Es seguro para uso concurrente.</p>
 */
public final class MockSensorsAdapter implements HardwareSensorsPort {

    public static final float CPU_TEMP_MIN = 30f;
    public static final float CPU_TEMP_MAX = 99f;
    public static final float GPU_TEMP_MIN = 28f;
    public static final float GPU_TEMP_MAX = 95f;
    public static final float CPU_WATTS_MAX = 180f;
    public static final float GPU_WATTS_MAX = 320f;
    public static final int GPU_FAN_RPM_MAX = 3600;

    private static final long GIB = 1024L * 1024L * 1024L;
    private static final float MEAN_REVERSION = 0.12f;
    private static final float FAN_STOP_BELOW_CELSIUS = 40f;

    private enum GpuKind { DEDICATED_FULL, INTEGRATED_NO_FAN, INACTIVE, NO_SENSOR }

    private record SimulatedGpu(GpuAdapter adapter, GpuKind kind, SensorProvider provider, PowerScope scope,
                                float meanOffset, long vramTotal) {
    }

    private final MockScenario scenario;
    private final Random random;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final List<SimulatedGpu> gpus;
    private final float[] gpuTemps;
    private final float cpuMean;
    private final float gpuMean;
    private final float jitter;

    private float cpuTemp;
    private int polls;

    /** Escenario y semilla de las propiedades del sistema. */
    public MockSensorsAdapter() {
        this(MockScenario.fromSystemProperty(), MockScenario.seedFromSystemProperty(), Clock.systemUTC(), System::nanoTime);
    }

    public MockSensorsAdapter(MockScenario scenario) {
        this(scenario, MockScenario.DEFAULT_SEED, Clock.systemUTC(), System::nanoTime);
    }

    /**
     * @param scenario perfil simulado
     * @param seed     semilla; misma semilla ⇒ misma secuencia
     * @param clock    reloj para las marcas de pared
     * @param nanoTime fuente de las marcas monotónicas
     */
    public MockSensorsAdapter(MockScenario scenario, long seed, Clock clock, LongSupplier nanoTime) {
        this.scenario = Objects.requireNonNull(scenario, "scenario no puede ser null");
        this.random = new Random(seed);
        this.clock = Objects.requireNonNull(clock, "clock no puede ser null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime no puede ser null");
        this.gpus = gpusFor(scenario);
        switch (scenario) {
            case THERMAL_WARNING -> {
                cpuMean = 78f;
                gpuMean = 77f;
                jitter = 0.8f;
            }
            case THERMAL_CRITICAL -> {
                cpuMean = 89f;
                gpuMean = 88f;
                jitter = 0.6f;
            }
            default -> {
                cpuMean = 52f;
                gpuMean = 46f;
                jitter = 2.5f;
            }
        }
        this.cpuTemp = cpuMean;
        this.gpuTemps = new float[gpus.size()];
        for (int i = 0; i < gpuTemps.length; i++) {
            gpuTemps[i] = gpuMean + gpus.get(i).meanOffset();
        }
    }

    public MockScenario scenario() {
        return scenario;
    }

    @Override
    public List<GpuAdapter> listGpuAdapters() {
        return gpus.stream().map(SimulatedGpu::adapter).toList();
    }

    @Override
    public synchronized HardwareSnapshot fetchHardwareSnapshot() {
        polls++;
        long nanos = nanoTime.getAsLong();
        CpuReading cpu = nextCpu(nanos);
        List<GpuReading> readings = new ArrayList<>(gpus.size());
        for (int i = 0; i < gpus.size(); i++) {
            readings.add(nextGpu(i, nanos));
        }
        long available = (long) ((7.0 + random.nextGaussian() * 0.3) * GIB);
        SystemMemoryReading memory = new SystemMemoryReading(LongMetricReading.valid(16L * GIB),
                LongMetricReading.valid(Math.max(GIB, Math.min(15L * GIB, available))), nanos);
        return new HardwareSnapshot(clock.instant(), cpu, readings, memory);
    }

    // ------------------------------------------------------------------
    // CPU
    // ------------------------------------------------------------------

    private CpuReading nextCpu(long nanos) {
        cpuTemp = step(cpuTemp, cpuMean, CPU_TEMP_MIN, CPU_TEMP_MAX);
        float watts = clamp(8f + (cpuTemp - CPU_TEMP_MIN) * 1.8f + gaussian(2.5f), 5f, CPU_WATTS_MAX);
        float usage = clamp((cpuTemp - CPU_TEMP_MIN) / (CPU_TEMP_MAX - CPU_TEMP_MIN) * 100f + gaussian(4f), 0f, 100f);
        MetricReading temperature = MetricReading.valid(round1(cpuTemp));
        MetricReading power = MetricReading.valid(round1(watts));
        MetricReading usagePercent = MetricReading.valid(round1(usage));
        boolean warming = scenario == MockScenario.WARMING_UP && polls <= MockScenario.WARM_UP_POLLS;
        if (warming) {
            power = MetricReading.unavailable(MetricState.WARMING_UP);
            usagePercent = MetricReading.unavailable(MetricState.WARMING_UP);
        }
        return switch (scenario) {
            case CPU_NO_TEMPERATURE -> new CpuReading(CpuVendor.INTEL, SensorProvider.WINDOWS, PowerScope.UNKNOWN,
                    MetricReading.unavailable(MetricState.NO_PROVIDER), MetricReading.unavailable(MetricState.NO_PROVIDER),
                    usagePercent, nanos);
            case AMD_NO_POWER -> new CpuReading(CpuVendor.AMD, SensorProvider.AMD_RYZEN_SDK, PowerScope.UNKNOWN,
                    temperature, MetricReading.unavailable(MetricState.NO_PROVIDER), usagePercent, nanos);
            case ACCESS_DENIED -> new CpuReading(CpuVendor.AMD, SensorProvider.AMD_RYZEN_SDK, PowerScope.UNKNOWN,
                    MetricReading.unavailable(MetricState.ACCESS_DENIED), MetricReading.unavailable(MetricState.NO_PROVIDER),
                    usagePercent, nanos);
            default -> new CpuReading(CpuVendor.INTEL, SensorProvider.INTEL_MSR, PowerScope.CPU_PACKAGE,
                    temperature, power, usagePercent, nanos);
        };
    }

    // ------------------------------------------------------------------
    // GPU
    // ------------------------------------------------------------------

    private GpuReading nextGpu(int index, long nanos) {
        SimulatedGpu gpu = gpus.get(index);
        if (gpu.kind() == GpuKind.INACTIVE) {
            GpuReading inactive = GpuReading.unavailable(gpu.adapter(), MetricState.DEVICE_INACTIVE, nanos);
            return new GpuReading(gpu.adapter(), gpu.provider(), gpu.scope(), inactive.temperatureCelsius(),
                    inactive.powerWatts(), inactive.usagePercent(), inactive.fanRpm(), inactive.fanPercent(),
                    inactive.videoMemoryUsedBytes(), inactive.videoMemoryTotalBytes(), nanos);
        }
        if (gpu.kind() == GpuKind.NO_SENSOR) {
            return GpuReading.unavailable(gpu.adapter(), MetricState.NO_PROVIDER, nanos);
        }
        float temp = step(gpuTemps[index], gpuMean + gpu.meanOffset(), GPU_TEMP_MIN, GPU_TEMP_MAX);
        gpuTemps[index] = temp;
        boolean integrated = gpu.kind() == GpuKind.INTEGRATED_NO_FAN;
        float watts = integrated
                ? clamp(3f + (temp - GPU_TEMP_MIN) * 0.35f + gaussian(0.8f), 1f, 45f)
                : clamp(12f + (temp - GPU_TEMP_MIN) * 4.0f + gaussian(6f), 5f, GPU_WATTS_MAX);
        float usage = clamp((temp - GPU_TEMP_MIN) / (GPU_TEMP_MAX - GPU_TEMP_MIN) * 100f + gaussian(5f), 0f, 100f);
        long vramUsed = (long) (gpu.vramTotal() * clamp(0.25f + usage / 200f + gaussian(0.02f), 0.05f, 0.95f));

        LongMetricReading fanRpm;
        LongMetricReading fanPercent;
        LongMetricReading vramTotal;
        if (integrated) {
            fanRpm = LongMetricReading.unavailable(MetricState.UNSUPPORTED);
            fanPercent = LongMetricReading.unavailable(MetricState.UNSUPPORTED);
            vramTotal = LongMetricReading.unavailable(MetricState.UNSUPPORTED);
        } else {
            long rpm = temp < FAN_STOP_BELOW_CELSIUS
                    ? 0 : (long) clamp(650f + (temp - FAN_STOP_BELOW_CELSIUS) * 48f + gaussian(60f), 0f, GPU_FAN_RPM_MAX);
            // Un ventilador detenido (fan-stop) es una lectura válida de 0 RPM, no una ausencia.
            fanRpm = LongMetricReading.valid(rpm);
            fanPercent = LongMetricReading.valid(Math.round(rpm * 100.0 / GPU_FAN_RPM_MAX));
            vramTotal = LongMetricReading.valid(gpu.vramTotal());
        }
        return new GpuReading(gpu.adapter(), gpu.provider(), gpu.scope(),
                MetricReading.valid(round1(temp)), MetricReading.valid(round1(watts)), MetricReading.valid(round1(usage)),
                fanRpm, fanPercent, LongMetricReading.valid(vramUsed), vramTotal, nanos);
    }

    private static List<SimulatedGpu> gpusFor(MockScenario scenario) {
        SimulatedGpu nvidia = new SimulatedGpu(adapter(0x0000_1A2B, 0x10DE, 0x2504, 0, "NVIDIA GeForce RTX 3060"),
                GpuKind.DEDICATED_FULL, SensorProvider.NVIDIA_NVML, PowerScope.GPU_BOARD, 0f, 12L * GIB);
        SimulatedGpu radeon = new SimulatedGpu(adapter(0x0000_2C3D, 0x1002, 0x7340, 0, "AMD Radeon RX 5500 XT"),
                GpuKind.DEDICATED_FULL, SensorProvider.AMD_ADLX, PowerScope.GPU_CHIP, 2f, 8L * GIB);
        SimulatedGpu intel = new SimulatedGpu(adapter(0x0000_0E4F, 0x8086, 0x9A49, 0, "Intel(R) Iris(R) Xe Graphics"),
                GpuKind.INTEGRATED_NO_FAN, SensorProvider.INTEL_IGCL, PowerScope.GPU_CHIP, 4f, 2L * GIB);
        return switch (scenario) {
            case AMD_NO_POWER, ACCESS_DENIED -> List.of(radeon);
            case IGPU_NO_FAN -> List.of(intel);
            case DUAL_GPU -> List.of(intel, reindex(nvidia, 1, 0f));
            case GPU_INACTIVE -> List.of(intel, new SimulatedGpu(reindex(nvidia, 1, 0f).adapter(), GpuKind.INACTIVE,
                    SensorProvider.NVIDIA_NVML, PowerScope.GPU_BOARD, 0f, 6L * GIB));
            case GPU_NO_SENSOR -> List.of(new SimulatedGpu(adapter(0x0000_3E5F, 0x1A03, 0x2000, 0, "ASPEED Graphics Family"),
                    GpuKind.NO_SENSOR, SensorProvider.NONE, PowerScope.UNKNOWN, 0f, 0L));
            case NO_GPU -> List.of();
            default -> List.of(nvidia);
        };
    }

    private static SimulatedGpu reindex(SimulatedGpu gpu, int index, float meanOffset) {
        GpuAdapter a = gpu.adapter();
        return new SimulatedGpu(new GpuAdapter(a.id(), a.vendorId(), a.deviceId(), a.subsystemId(), a.revision(),
                a.dxgiFlags(), index, a.description()), gpu.kind(), gpu.provider(), gpu.scope(), meanOffset, gpu.vramTotal());
    }

    private static GpuAdapter adapter(int luidLow, int vendor, int device, int index, String description) {
        return new GpuAdapter(new GpuAdapterId(luidLow, 0), vendor, device, 0, 1, 0, index, description);
    }

    // ------------------------------------------------------------------

    private float step(float current, float mean, float min, float max) {
        float next = current + MEAN_REVERSION * (mean - current) + gaussian(jitter);
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
