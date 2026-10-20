package com.mace.infrastructure.nativebridge;

import com.mace.domain.model.CpuReading;
import com.mace.domain.model.GpuAdapter;
import com.mace.domain.model.GpuAdapterId;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.LongMetricReading;
import com.mace.domain.model.MetricReading;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessSample;
import com.mace.domain.model.ResponsivenessResult;
import com.mace.domain.model.SystemMemoryReading;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Códec de los registros V2 de {@code mace_types_v2.h}: decodifica lo que escribe la DLL y
 * codifica lecturas del dominio con el mismo formato binario (para el anillo de caja negra,
 * que exige {@code struct_size} y {@code version} exactos).
 *
 * <p>Usa accesos no alineados para operar tanto sobre memoria nativa como sobre
 * {@code byte[]} ({@link MemorySegment#ofArray(byte[])}). Reglas:</p>
 * <ul>
 *   <li>{@code uint32_t} → {@link Integer#toUnsignedLong(int)}; {@code uint64_t} con el bit alto
 *       encendido se satura a {@link Long#MAX_VALUE} en lugar de volverse negativo.</li>
 *   <li>Un {@code float} marcado {@code VALID} pero no finito se trata como {@code READ_FAILED}.</li>
 *   <li>Al codificar, una métrica no válida se escribe como 0 con su estado; los campos
 *       reservados se escriben a 0.</li>
 * </ul>
 */
public final class V2Records {

    private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT_UNALIGNED;
    private static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG_UNALIGNED;
    private static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT_UNALIGNED;

    private V2Records() {
    }

    /** @throws NativeLibraryException si {@code struct_size}/{@code version} no son los del contrato */
    public static void requireHeader(MemorySegment record, long expectedSize, String structName) {
        Objects.requireNonNull(record, "record no puede ser null");
        if (record.byteSize() < expectedSize) {
            throw new NativeLibraryException(structName + ": segmento de " + record.byteSize()
                    + " bytes, se requieren " + expectedSize);
        }
        int size = record.get(INT, NativeLayouts.STRUCT_SIZE_OFFSET);
        int version = record.get(INT, NativeLayouts.STRUCT_VERSION_OFFSET);
        if (Integer.toUnsignedLong(size) != expectedSize || version != NativeLayouts.V2_VERSION) {
            throw new NativeLibraryException(structName + " con struct_size=" + Integer.toUnsignedString(size)
                    + " version=" + Integer.toUnsignedString(version) + " (se esperaba " + expectedSize + "/"
                    + NativeLayouts.V2_VERSION + ")");
        }
    }

    // ------------------------------------------------------------------
    // Primitivas
    // ------------------------------------------------------------------

    public static long readUnsignedLongSaturated(MemorySegment segment, long offset) {
        long raw = segment.get(LONG, offset);
        return raw < 0 ? Long.MAX_VALUE : raw;
    }

    public static MetricReading readF32(MemorySegment segment, long offset) {
        MetricState state = NativeCodes.metricState(segment.get(INT, offset + NativeLayouts.METRIC_32_STATE_OFFSET));
        if (!state.isValid()) {
            return MetricReading.unavailable(state);
        }
        float value = segment.get(FLOAT, offset);
        return Float.isFinite(value) ? MetricReading.valid(value) : MetricReading.unavailable(MetricState.READ_FAILED);
    }

    public static LongMetricReading readU32(MemorySegment segment, long offset) {
        MetricState state = NativeCodes.metricState(segment.get(INT, offset + NativeLayouts.METRIC_32_STATE_OFFSET));
        if (!state.isValid()) {
            return LongMetricReading.unavailable(state);
        }
        return LongMetricReading.valid(Integer.toUnsignedLong(segment.get(INT, offset)));
    }

    public static LongMetricReading readU64(MemorySegment segment, long offset) {
        MetricState state = NativeCodes.metricState(segment.get(INT, offset + NativeLayouts.METRIC_64_STATE_OFFSET));
        if (!state.isValid()) {
            return LongMetricReading.unavailable(state);
        }
        return LongMetricReading.valid(readUnsignedLongSaturated(segment, offset));
    }

    public static void writeF32(MemorySegment segment, long offset, MetricReading reading) {
        segment.set(FLOAT, offset, reading.isValid() ? (float) reading.value() : 0f);
        segment.set(INT, offset + NativeLayouts.METRIC_32_STATE_OFFSET, NativeCodes.metricStateCode(reading.state()));
    }

    public static void writeU32(MemorySegment segment, long offset, LongMetricReading reading) {
        segment.set(INT, offset, reading.isValid() ? (int) Math.min(reading.value(), 0xFFFF_FFFFL) : 0);
        segment.set(INT, offset + NativeLayouts.METRIC_32_STATE_OFFSET, NativeCodes.metricStateCode(reading.state()));
    }

    public static void writeU64(MemorySegment segment, long offset, LongMetricReading reading) {
        segment.set(LONG, offset, reading.isValid() ? reading.value() : 0L);
        segment.set(INT, offset + NativeLayouts.METRIC_64_STATE_OFFSET, NativeCodes.metricStateCode(reading.state()));
        segment.set(INT, offset + NativeLayouts.METRIC_64_STATE_OFFSET + 4, 0);
    }

    public static GpuAdapterId readLuid(MemorySegment segment, long offset) {
        return new GpuAdapterId(segment.get(INT, offset + NativeLayouts.LUID_LOW_PART_OFFSET),
                segment.get(INT, offset + NativeLayouts.LUID_HIGH_PART_OFFSET));
    }

    public static void writeLuid(MemorySegment segment, long offset, GpuAdapterId id) {
        segment.set(INT, offset + NativeLayouts.LUID_LOW_PART_OFFSET, id.lowPart());
        segment.set(INT, offset + NativeLayouts.LUID_HIGH_PART_OFFSET, id.highPart());
    }

    /**
     * @return la identidad, o {@code null} si el PID o la fecha de creación son 0 (la DLL nunca
     *         entrega identidades así; un registro con ellas se descarta)
     */
    public static ProcessIdentity readIdentity(MemorySegment segment, long offset) {
        long pid = Integer.toUnsignedLong(segment.get(INT, offset + NativeLayouts.IDENTITY_PID_OFFSET));
        long creation = segment.get(LONG, offset + NativeLayouts.IDENTITY_CREATION_OFFSET);
        if (pid == 0 || creation == 0) {
            return null;
        }
        return ProcessIdentity.of(pid, creation);
    }

    /** Escribe la identidad con {@code reserved = 0}. */
    public static void writeIdentity(MemorySegment segment, long offset, ProcessIdentity identity) {
        long pid = identity.pid().value();
        if (pid > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("El PID excede uint32_t: " + pid);
        }
        segment.set(INT, offset + NativeLayouts.IDENTITY_PID_OFFSET, (int) pid);
        segment.set(INT, offset + NativeLayouts.IDENTITY_RESERVED_OFFSET, 0);
        segment.set(LONG, offset + NativeLayouts.IDENTITY_CREATION_OFFSET, identity.creationTime100nsUtc());
    }

    // ------------------------------------------------------------------
    // Decodificación de registros completos
    // ------------------------------------------------------------------

    public static CpuReading decodeCpu(MemorySegment record) {
        requireHeader(record, NativeLayouts.CPU_TELEMETRY_SIZE, "MaceCpuTelemetryV2");
        return new CpuReading(
                NativeCodes.cpuVendor(record.get(INT, NativeLayouts.CPU_VENDOR_OFFSET)),
                NativeCodes.provider(record.get(INT, NativeLayouts.CPU_PROVIDER_OFFSET)),
                NativeCodes.powerScope(record.get(INT, NativeLayouts.CPU_POWER_SCOPE_OFFSET)),
                readF32(record, NativeLayouts.CPU_TEMPERATURE_OFFSET),
                readF32(record, NativeLayouts.CPU_POWER_OFFSET),
                readF32(record, NativeLayouts.CPU_USAGE_OFFSET),
                readUnsignedLongSaturated(record, NativeLayouts.CPU_TIMESTAMP_OFFSET));
    }

    /** @param adapter adaptador consultado; el llamador ya verificó que el LUID coincide */
    public static GpuReading decodeGpu(MemorySegment record, GpuAdapter adapter) {
        requireHeader(record, NativeLayouts.GPU_TELEMETRY_SIZE, "MaceGpuTelemetryV2");
        return new GpuReading(
                adapter,
                NativeCodes.provider(record.get(INT, NativeLayouts.GPU_PROVIDER_OFFSET)),
                NativeCodes.powerScope(record.get(INT, NativeLayouts.GPU_POWER_SCOPE_OFFSET)),
                readF32(record, NativeLayouts.GPU_TEMPERATURE_OFFSET),
                readF32(record, NativeLayouts.GPU_POWER_OFFSET),
                readF32(record, NativeLayouts.GPU_USAGE_OFFSET),
                readU32(record, NativeLayouts.GPU_FAN_RPM_OFFSET),
                readU32(record, NativeLayouts.GPU_FAN_PERCENT_OFFSET),
                readU64(record, NativeLayouts.GPU_VRAM_USED_OFFSET),
                readU64(record, NativeLayouts.GPU_VRAM_TOTAL_OFFSET),
                readUnsignedLongSaturated(record, NativeLayouts.GPU_TIMESTAMP_OFFSET));
    }

    public static SystemMemoryReading decodeSystemMemory(MemorySegment record) {
        requireHeader(record, NativeLayouts.SYSTEM_MEMORY_SIZE, "MaceSystemMemoryV2");
        return new SystemMemoryReading(
                readU64(record, NativeLayouts.MEMORY_TOTAL_OFFSET),
                readU64(record, NativeLayouts.MEMORY_AVAILABLE_OFFSET),
                readUnsignedLongSaturated(record, NativeLayouts.MEMORY_TIMESTAMP_OFFSET));
    }

    /** @return la muestra, o {@code null} si su identidad está incompleta */
    public static ProcessSample decodeSample(MemorySegment record) {
        requireHeader(record, NativeLayouts.PROCESS_SAMPLE_SIZE, "MaceProcessSampleV2");
        ProcessIdentity identity = readIdentity(record, NativeLayouts.SAMPLE_IDENTITY_OFFSET);
        if (identity == null) {
            return null;
        }
        return new ProcessSample(identity,
                readUnsignedLongSaturated(record, NativeLayouts.SAMPLE_TIMESTAMP_OFFSET),
                readU64(record, NativeLayouts.SAMPLE_WORKING_SET_OFFSET),
                readU64(record, NativeLayouts.SAMPLE_PRIVATE_BYTES_OFFSET));
    }

    /** @param requested identidad consultada (la DLL la repite en el registro) */
    public static ResponsivenessResult decodeResponsiveness(MemorySegment record, ProcessIdentity requested) {
        requireHeader(record, NativeLayouts.RESPONSIVENESS_SIZE, "MaceResponsivenessV2");
        int examined = record.get(INT, NativeLayouts.RESPONSIVENESS_EXAMINED_OFFSET);
        int hung = record.get(INT, NativeLayouts.RESPONSIVENESS_HUNG_OFFSET);
        int safeExamined = examined < 0 ? Integer.MAX_VALUE : examined;
        int safeHung = hung < 0 ? safeExamined : Math.min(hung, safeExamined);
        return new ResponsivenessResult(requested,
                NativeCodes.responsivenessOutcome(record.get(INT, NativeLayouts.RESPONSIVENESS_OUTCOME_OFFSET)),
                safeExamined, safeHung,
                record.get(INT, NativeLayouts.RESPONSIVENESS_WIN32_ERROR_OFFSET));
    }

    // ------------------------------------------------------------------
    // Codificación (payloads del anillo de caja negra)
    // ------------------------------------------------------------------

    public static byte[] encodeCpu(CpuReading cpu) {
        byte[] bytes = new byte[(int) NativeLayouts.CPU_TELEMETRY_SIZE];
        MemorySegment record = MemorySegment.ofArray(bytes);
        writeHeader(record, NativeLayouts.CPU_TELEMETRY_SIZE);
        record.set(LONG, NativeLayouts.CPU_TIMESTAMP_OFFSET, cpu.monotonicNanos());
        record.set(INT, NativeLayouts.CPU_VENDOR_OFFSET, NativeCodes.cpuVendorCode(cpu.vendor()));
        record.set(INT, NativeLayouts.CPU_PROVIDER_OFFSET, NativeCodes.providerCode(cpu.provider()));
        writeF32(record, NativeLayouts.CPU_TEMPERATURE_OFFSET, cpu.temperatureCelsius());
        writeF32(record, NativeLayouts.CPU_POWER_OFFSET, cpu.powerWatts());
        writeF32(record, NativeLayouts.CPU_USAGE_OFFSET, cpu.usagePercent());
        record.set(INT, NativeLayouts.CPU_POWER_SCOPE_OFFSET, NativeCodes.powerScopeCode(cpu.powerScope()));
        return bytes;
    }

    public static byte[] encodeGpu(GpuReading gpu) {
        byte[] bytes = new byte[(int) NativeLayouts.GPU_TELEMETRY_SIZE];
        MemorySegment record = MemorySegment.ofArray(bytes);
        writeHeader(record, NativeLayouts.GPU_TELEMETRY_SIZE);
        record.set(LONG, NativeLayouts.GPU_TIMESTAMP_OFFSET, gpu.monotonicNanos());
        writeLuid(record, NativeLayouts.GPU_LUID_OFFSET, gpu.adapterId());
        record.set(INT, NativeLayouts.GPU_PROVIDER_OFFSET, NativeCodes.providerCode(gpu.provider()));
        record.set(INT, NativeLayouts.GPU_POWER_SCOPE_OFFSET, NativeCodes.powerScopeCode(gpu.powerScope()));
        writeF32(record, NativeLayouts.GPU_TEMPERATURE_OFFSET, gpu.temperatureCelsius());
        writeF32(record, NativeLayouts.GPU_POWER_OFFSET, gpu.powerWatts());
        writeF32(record, NativeLayouts.GPU_USAGE_OFFSET, gpu.usagePercent());
        writeU32(record, NativeLayouts.GPU_FAN_RPM_OFFSET, gpu.fanRpm());
        writeU32(record, NativeLayouts.GPU_FAN_PERCENT_OFFSET, gpu.fanPercent());
        writeU64(record, NativeLayouts.GPU_VRAM_USED_OFFSET, gpu.videoMemoryUsedBytes());
        writeU64(record, NativeLayouts.GPU_VRAM_TOTAL_OFFSET, gpu.videoMemoryTotalBytes());
        return bytes;
    }

    public static byte[] encodeSystemMemory(SystemMemoryReading memory) {
        byte[] bytes = new byte[(int) NativeLayouts.SYSTEM_MEMORY_SIZE];
        MemorySegment record = MemorySegment.ofArray(bytes);
        writeHeader(record, NativeLayouts.SYSTEM_MEMORY_SIZE);
        record.set(LONG, NativeLayouts.MEMORY_TIMESTAMP_OFFSET, memory.monotonicNanos());
        writeU64(record, NativeLayouts.MEMORY_TOTAL_OFFSET, memory.totalBytes());
        writeU64(record, NativeLayouts.MEMORY_AVAILABLE_OFFSET, memory.availableBytes());
        return bytes;
    }

    public static byte[] encodeSample(ProcessSample sample) {
        byte[] bytes = new byte[(int) NativeLayouts.PROCESS_SAMPLE_SIZE];
        MemorySegment record = MemorySegment.ofArray(bytes);
        writeHeader(record, NativeLayouts.PROCESS_SAMPLE_SIZE);
        writeIdentity(record, NativeLayouts.SAMPLE_IDENTITY_OFFSET, sample.identity());
        record.set(LONG, NativeLayouts.SAMPLE_TIMESTAMP_OFFSET, sample.monotonicNanos());
        writeU64(record, NativeLayouts.SAMPLE_WORKING_SET_OFFSET, sample.workingSet());
        writeU64(record, NativeLayouts.SAMPLE_PRIVATE_BYTES_OFFSET, sample.privateBytes());
        return bytes;
    }

    private static void writeHeader(MemorySegment record, long size) {
        record.set(INT, NativeLayouts.STRUCT_SIZE_OFFSET, (int) size);
        record.set(INT, NativeLayouts.STRUCT_VERSION_OFFSET, NativeLayouts.V2_VERSION);
    }
}
