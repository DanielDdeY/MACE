package com.mace.domain;

import com.mace.domain.model.CpuReading;
import com.mace.domain.model.CpuVendor;
import com.mace.domain.model.GpuAdapter;
import com.mace.domain.model.GpuAdapterId;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.ImagePath;
import com.mace.domain.model.LongMetricReading;
import com.mace.domain.model.MemoryMetrics;
import com.mace.domain.model.MetricReading;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.PowerScope;
import com.mace.domain.model.ProcessEntry;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessInventory;
import com.mace.domain.model.ProcessSample;
import com.mace.domain.model.SensorProvider;
import com.mace.domain.model.SystemMemoryReading;

import java.time.Instant;
import java.util.List;

/** Constructores compactos de modelos V2 para las pruebas. */
public final class DomainFixtures {

    public static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    public static final long GIB = 1024L * 1024L * 1024L;
    public static final long MIB = 1024L * 1024L;
    public static final long SECOND = 1_000_000_000L;

    private DomainFixtures() {
    }

    public static CpuReading cpu(MetricReading temperature) {
        return new CpuReading(CpuVendor.INTEL, SensorProvider.INTEL_MSR, PowerScope.CPU_PACKAGE,
                temperature, MetricReading.valid(35), MetricReading.valid(20), 1L);
    }

    public static CpuReading cpu(double temperature) {
        return cpu(MetricReading.valid(temperature));
    }

    public static GpuAdapter gpuAdapter(int luidLow, int index, String description) {
        return new GpuAdapter(new GpuAdapterId(luidLow, 0), 0x10DE, 0x2504, 0, 1, 0, index, description);
    }

    public static GpuReading gpu(GpuAdapter adapter, MetricReading temperature) {
        return new GpuReading(adapter, SensorProvider.NVIDIA_NVML, PowerScope.GPU_BOARD, temperature,
                MetricReading.valid(120), MetricReading.valid(40), LongMetricReading.valid(1500),
                LongMetricReading.valid(42), LongMetricReading.valid(2 * GIB), LongMetricReading.valid(8 * GIB), 1L);
    }

    public static GpuReading gpu(GpuAdapter adapter, double temperature) {
        return gpu(adapter, MetricReading.valid(temperature));
    }

    public static SystemMemoryReading memory() {
        return new SystemMemoryReading(LongMetricReading.valid(16 * GIB), LongMetricReading.valid(8 * GIB), 1L);
    }

    public static HardwareSnapshot snapshot(CpuReading cpu, GpuReading... gpus) {
        return new HardwareSnapshot(NOW, cpu, List.of(gpus), memory());
    }

    public static MetricReading missing(MetricState state) {
        return MetricReading.unavailable(state);
    }

    public static ProcessIdentity identity(long pid, long creation) {
        return ProcessIdentity.of(pid, creation);
    }

    public static ProcessEntry entry(ProcessIdentity identity, String name, long workingSet, long privateBytes) {
        return new ProcessEntry(identity, name, ImagePath.of("C:\\Apps\\" + name),
                new MemoryMetrics(workingSet, privateBytes));
    }

    public static ProcessInventory inventory(long monotonicNanos, ProcessEntry... entries) {
        return new ProcessInventory(List.of(entries), 0, NOW, monotonicNanos);
    }

    public static ProcessSample sample(ProcessIdentity identity, long nanos, long workingSet, long privateBytes) {
        return new ProcessSample(identity, nanos, LongMetricReading.valid(workingSet), LongMetricReading.valid(privateBytes));
    }
}
