package com.mace.infrastructure.nativebridge;

import com.mace.domain.model.CpuReading;
import com.mace.domain.model.CpuVendor;
import com.mace.domain.model.GpuAdapter;
import com.mace.domain.model.GpuAdapterId;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.LongMetricReading;
import com.mace.domain.model.MetricReading;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.PowerScope;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessSample;
import com.mace.domain.model.SensorProvider;
import com.mace.domain.model.SystemMemoryReading;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Codificación de payloads del anillo: tamaño exacto, cabecera V2 y estados. */
class V2RecordsTest {

    private static final GpuAdapter GPU = new GpuAdapter(new GpuAdapterId(0xABCD, -1), 0x1002, 0x7340, 0, 1, 0, 0, "Radeon");

    @Test
    void cpuIdaYVuelta() {
        CpuReading cpu = new CpuReading(CpuVendor.INTEL, SensorProvider.INTEL_MSR, PowerScope.CPU_PACKAGE,
                MetricReading.valid(64.5), MetricReading.unavailable(MetricState.WARMING_UP), MetricReading.valid(12.25), 77L);

        byte[] payload = V2Records.encodeCpu(cpu);

        assertEquals(56, payload.length);
        MemorySegment record = MemorySegment.ofArray(payload);
        assertEquals(56, record.get(JAVA_INT_UNALIGNED, 0));
        assertEquals(2, record.get(JAVA_INT_UNALIGNED, 4));
        assertEquals(cpu, V2Records.decodeCpu(record));
    }

    @Test
    void gpuIdaYVueltaConLuidNegativo() {
        GpuReading gpu = new GpuReading(GPU, SensorProvider.AMD_ADLX, PowerScope.GPU_CHIP,
                MetricReading.valid(70), MetricReading.valid(110.5), MetricReading.unavailable(MetricState.READ_FAILED),
                LongMetricReading.valid(1800), LongMetricReading.valid(40),
                LongMetricReading.valid(3L << 30), LongMetricReading.unavailable(MetricState.UNSUPPORTED), 5L);

        byte[] payload = V2Records.encodeGpu(gpu);

        assertEquals(104, payload.length);
        MemorySegment record = MemorySegment.ofArray(payload);
        assertEquals(GPU.id(), V2Records.readLuid(record, 16));
        assertEquals(gpu, V2Records.decodeGpu(record, GPU));
    }

    @Test
    void memoriaYMuestraDeProcesoIdaYVuelta() {
        SystemMemoryReading memory = new SystemMemoryReading(LongMetricReading.valid(1L << 34),
                LongMetricReading.unavailable(MetricState.READ_FAILED), 9L);
        ProcessSample sample = new ProcessSample(ProcessIdentity.of(0xFFFF_FFFEL, 133_000_000_000_000_000L), 11L,
                LongMetricReading.valid(100), LongMetricReading.unavailable(MetricState.ACCESS_DENIED));

        assertEquals(memory, V2Records.decodeSystemMemory(MemorySegment.ofArray(V2Records.encodeSystemMemory(memory))));
        byte[] samplePayload = V2Records.encodeSample(sample);
        assertEquals(64, samplePayload.length);
        assertEquals(sample, V2Records.decodeSample(MemorySegment.ofArray(samplePayload)));
    }

    @Test
    void unaIdentidadIncompletaNoSeDecodifica() {
        byte[] payload = V2Records.encodeSample(new ProcessSample(ProcessIdentity.of(10, 5), 1L,
                LongMetricReading.valid(1), LongMetricReading.valid(1)));
        MemorySegment.ofArray(payload).set(JAVA_INT_UNALIGNED, 8, 0);

        assertNull(V2Records.decodeSample(MemorySegment.ofArray(payload)));
    }

    @Test
    void rechazaPidQueNoCabeEnUint32() {
        assertThrows(IllegalArgumentException.class, () -> V2Records.encodeSample(new ProcessSample(
                ProcessIdentity.of(0x1_0000_0000L, 5), 1L, LongMetricReading.valid(1), LongMetricReading.valid(1))));
    }
}
