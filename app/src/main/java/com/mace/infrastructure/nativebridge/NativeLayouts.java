package com.mace.infrastructure.nativebridge;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Espejo exacto, en Java FFM (JEP 454), de las estructuras declaradas en
 * {@code native/include/mace_types.h}, {@code mace_types_v2.h} y {@code mace_extensions.h}.
 *
 * <p>Los headers nativos usan {@code #pragma pack(push, 8)}; como ningún miembro tiene
 * alineación natural mayor a 8 bytes, el layout coincide con el natural de MSVC x64.
 * Cada {@link StructLayout} de esta clase incluye el <em>padding explícito</em> que el
 * compilador de C inserta, de modo que {@code byteSize()} y los offsets sean idénticos a
 * {@code sizeof} / {@code offsetof} en C (comprobado en {@code NativeLayoutsTest} contra los
 * mismos valores que {@code native/tests/test_abi_layouts.c}).</p>
 *
 * <p>Tipos: {@code uint32_t}/{@code float} = 4 B, {@code uint64_t}/puntero = 8 B y
 * {@code wchar_t} = 2 B UTF-16LE. Los enteros sin signo se leen con {@code JAVA_INT}/
 * {@code JAVA_LONG} y se convierten con {@link Integer#toUnsignedLong(int)} o saturación.</p>
 *
 * <pre>
 * ProcessData (1584 bytes, alineación 8)       — heredado
 *   +0 pid · +4 name[260] · +524 path[520] · +1564 [pad 4] · +1568 working_set · +1576 private
 *
 * TelemetryData (28 bytes, alineación 4)        — heredado
 *   +0 cpu_temp · +4 cpu_watts · +8 gpu_temp · +12 gpu_watts · +16 gpu_fan_rpm
 *   +20 is_gpu_available · +21 is_cpu_sensor_available · +22 _padding[2] · +24 cpu_usage
 *
 * BlackboxSharedHeader (16 bytes, alineación 4) — heredado
 *
 * MaceAbiInfoV2 64 · MaceGpuAdapterInfoV2 296 · MaceCpuTelemetryV2 56 · MaceGpuTelemetryV2 104
 * MaceSystemMemoryV2 48 · MaceProcessIdentityV2 16 · MaceProcessInfoV2 576
 * MaceProcessSampleV2 64 · MaceResponsivenessV2 40
 * MaceBlackboxHeaderV2 48 · MaceBlackboxRecordV2 160
 * </pre>
 *
 * <p>Los offsets se exponen como constantes para leer los campos con
 * {@link java.lang.foreign.MemorySegment#get(ValueLayout.OfInt, long)} y afines, sin
 * depender de la forma de las coordenadas de {@code VarHandle} entre versiones del JDK.</p>
 */
public final class NativeLayouts {

    private NativeLayouts() {
    }

    /** Versión de estructura de todos los registros V2 ({@code MACE_V2_CONTRACT_VERSION}). */
    public static final int V2_VERSION = 2;

    /** Capacidad de {@code wchar_t name[260]} (MAX_PATH) en {@code ProcessData}. */
    public static final int PROCESS_NAME_CHARS = 260;

    /** Capacidad de {@code wchar_t path[520]} en {@code ProcessData}. */
    public static final int PROCESS_PATH_CHARS = 520;

    /** Capacidad de {@code wchar_t description[128]} en {@code MaceGpuAdapterInfoV2}. */
    public static final int GPU_DESCRIPTION_CHARS = 128;

    /** Carga útil fija de cada registro del anillo V2 ({@code MACE_BLACKBOX_V2_PAYLOAD_BYTES}). */
    public static final int BLACKBOX_V2_PAYLOAD_BYTES = 128;

    /** {@code wchar_t} en Windows x64: 16 bits, UTF-16LE (orden de bytes nativo de la plataforma). */
    public static final ValueLayout.OfChar WCHAR_T = JAVA_CHAR;

    /** Valor de {@code BlackboxSharedHeader.magic_header} escrito por {@code memory_map_stub.c} ('MACE'). */
    public static final int BLACKBOX_MAGIC_HEADER = 0x4D414345;

    /** {@code MACE_BLACKBOX_V2_MAGIC} ('MAC2'). */
    public static final int BLACKBOX_V2_MAGIC = 0x4D414332;

    // ------------------------------------------------------------------
    // ProcessData (heredado)
    // ------------------------------------------------------------------

    public static final StructLayout PROCESS_DATA = MemoryLayout.structLayout(
            JAVA_INT.withName("pid"),
            MemoryLayout.sequenceLayout(PROCESS_NAME_CHARS, WCHAR_T).withName("name"),
            MemoryLayout.sequenceLayout(PROCESS_PATH_CHARS, WCHAR_T).withName("path"),
            MemoryLayout.paddingLayout(4),
            JAVA_LONG.withName("working_set_bytes"),
            JAVA_LONG.withName("private_bytes")
    ).withName("ProcessData");

    /** {@code sizeof(ProcessData)} = 1584. */
    public static final long PROCESS_DATA_SIZE = PROCESS_DATA.byteSize();

    public static final long PROCESS_PID_OFFSET = PROCESS_DATA.byteOffset(PathElement.groupElement("pid"));
    public static final long PROCESS_NAME_OFFSET = PROCESS_DATA.byteOffset(PathElement.groupElement("name"));
    public static final long PROCESS_PATH_OFFSET = PROCESS_DATA.byteOffset(PathElement.groupElement("path"));
    public static final long PROCESS_WORKING_SET_OFFSET =
            PROCESS_DATA.byteOffset(PathElement.groupElement("working_set_bytes"));
    public static final long PROCESS_PRIVATE_BYTES_OFFSET =
            PROCESS_DATA.byteOffset(PathElement.groupElement("private_bytes"));

    // ------------------------------------------------------------------
    // TelemetryData (heredado)
    // ------------------------------------------------------------------

    public static final StructLayout TELEMETRY_DATA = MemoryLayout.structLayout(
            JAVA_FLOAT.withName("cpu_temp"),
            JAVA_FLOAT.withName("cpu_watts"),
            JAVA_FLOAT.withName("gpu_temp"),
            JAVA_FLOAT.withName("gpu_watts"),
            JAVA_INT.withName("gpu_fan_rpm"),
            JAVA_BYTE.withName("is_gpu_available"),
            JAVA_BYTE.withName("is_cpu_sensor_available"),
            MemoryLayout.paddingLayout(2),
            JAVA_FLOAT.withName("cpu_usage")
    ).withName("TelemetryData");

    /** {@code sizeof(TelemetryData)} = 28. */
    public static final long TELEMETRY_DATA_SIZE = TELEMETRY_DATA.byteSize();

    public static final long TELEMETRY_CPU_TEMP_OFFSET = TELEMETRY_DATA.byteOffset(PathElement.groupElement("cpu_temp"));
    public static final long TELEMETRY_CPU_WATTS_OFFSET = TELEMETRY_DATA.byteOffset(PathElement.groupElement("cpu_watts"));
    public static final long TELEMETRY_GPU_TEMP_OFFSET = TELEMETRY_DATA.byteOffset(PathElement.groupElement("gpu_temp"));
    public static final long TELEMETRY_GPU_WATTS_OFFSET = TELEMETRY_DATA.byteOffset(PathElement.groupElement("gpu_watts"));
    public static final long TELEMETRY_GPU_FAN_RPM_OFFSET =
            TELEMETRY_DATA.byteOffset(PathElement.groupElement("gpu_fan_rpm"));
    public static final long TELEMETRY_GPU_AVAILABLE_OFFSET =
            TELEMETRY_DATA.byteOffset(PathElement.groupElement("is_gpu_available"));
    public static final long TELEMETRY_CPU_SENSOR_AVAILABLE_OFFSET =
            TELEMETRY_DATA.byteOffset(PathElement.groupElement("is_cpu_sensor_available"));
    public static final long TELEMETRY_CPU_USAGE_OFFSET =
            TELEMETRY_DATA.byteOffset(PathElement.groupElement("cpu_usage"));

    // ------------------------------------------------------------------
    // BlackboxSharedHeader (heredado; no se usa como caja negra V2)
    // ------------------------------------------------------------------

    public static final StructLayout BLACKBOX_SHARED_HEADER = MemoryLayout.structLayout(
            JAVA_INT.withName("magic_header"),
            JAVA_INT.withName("ring_buffer_capacity"),
            JAVA_INT.withName("write_cursor"),
            JAVA_INT.withName("record_count")
    ).withName("BlackboxSharedHeader");

    /** {@code sizeof(BlackboxSharedHeader)} = 16. */
    public static final long BLACKBOX_SHARED_HEADER_SIZE = BLACKBOX_SHARED_HEADER.byteSize();

    public static final long BLACKBOX_MAGIC_OFFSET =
            BLACKBOX_SHARED_HEADER.byteOffset(PathElement.groupElement("magic_header"));
    public static final long BLACKBOX_CAPACITY_OFFSET =
            BLACKBOX_SHARED_HEADER.byteOffset(PathElement.groupElement("ring_buffer_capacity"));
    public static final long BLACKBOX_WRITE_CURSOR_OFFSET =
            BLACKBOX_SHARED_HEADER.byteOffset(PathElement.groupElement("write_cursor"));
    public static final long BLACKBOX_RECORD_COUNT_OFFSET =
            BLACKBOX_SHARED_HEADER.byteOffset(PathElement.groupElement("record_count"));

    // ------------------------------------------------------------------
    // Métricas V2: valor + estado
    // ------------------------------------------------------------------

    /** {@code MaceMetricF32V2} (8 bytes, alineación 4). */
    public static final StructLayout METRIC_F32 = MemoryLayout.structLayout(
            JAVA_FLOAT.withName("value"),
            JAVA_INT.withName("state")
    ).withName("MaceMetricF32V2");

    /** {@code MaceMetricU32V2} (8 bytes, alineación 4). */
    public static final StructLayout METRIC_U32 = MemoryLayout.structLayout(
            JAVA_INT.withName("value"),
            JAVA_INT.withName("state")
    ).withName("MaceMetricU32V2");

    /** {@code MaceMetricU64V2} (16 bytes, alineación 8). */
    public static final StructLayout METRIC_U64 = MemoryLayout.structLayout(
            JAVA_LONG.withName("value"),
            JAVA_INT.withName("state"),
            JAVA_INT.withName("reserved")
    ).withName("MaceMetricU64V2");

    /** Desplazamiento del estado dentro de cualquier métrica F32/U32 (4) o U64 (8). */
    public static final long METRIC_32_STATE_OFFSET = METRIC_F32.byteOffset(PathElement.groupElement("state"));
    public static final long METRIC_64_STATE_OFFSET = METRIC_U64.byteOffset(PathElement.groupElement("state"));

    // ------------------------------------------------------------------
    // MaceAbiInfoV2
    // ------------------------------------------------------------------

    public static final StructLayout ABI_INFO = MemoryLayout.structLayout(
            JAVA_INT.withName("struct_size"),
            JAVA_INT.withName("version"),
            JAVA_INT.withName("abi_major"),
            JAVA_INT.withName("abi_minor"),
            JAVA_INT.withName("process_data_size"),
            JAVA_INT.withName("telemetry_data_size"),
            JAVA_INT.withName("blackbox_header_size"),
            JAVA_INT.withName("wchar_size"),
            JAVA_INT.withName("pointer_size"),
            JAVA_INT.withName("reserved0"),
            JAVA_LONG.withName("implemented_capabilities"),
            JAVA_INT.withName("build_major"),
            JAVA_INT.withName("build_minor"),
            JAVA_INT.withName("build_patch"),
            JAVA_INT.withName("reserved1")
    ).withName("MaceAbiInfoV2");

    public static final long ABI_INFO_SIZE = ABI_INFO.byteSize();
    public static final long ABI_STRUCT_SIZE_OFFSET = abiOffset("struct_size");
    public static final long ABI_VERSION_OFFSET = abiOffset("version");
    public static final long ABI_MAJOR_OFFSET = abiOffset("abi_major");
    public static final long ABI_MINOR_OFFSET = abiOffset("abi_minor");
    public static final long ABI_PROCESS_DATA_SIZE_OFFSET = abiOffset("process_data_size");
    public static final long ABI_TELEMETRY_DATA_SIZE_OFFSET = abiOffset("telemetry_data_size");
    public static final long ABI_BLACKBOX_HEADER_SIZE_OFFSET = abiOffset("blackbox_header_size");
    public static final long ABI_WCHAR_SIZE_OFFSET = abiOffset("wchar_size");
    public static final long ABI_POINTER_SIZE_OFFSET = abiOffset("pointer_size");
    public static final long ABI_RESERVED0_OFFSET = abiOffset("reserved0");
    public static final long ABI_CAPABILITIES_OFFSET = abiOffset("implemented_capabilities");
    public static final long ABI_BUILD_MAJOR_OFFSET = abiOffset("build_major");
    public static final long ABI_BUILD_MINOR_OFFSET = abiOffset("build_minor");
    public static final long ABI_BUILD_PATCH_OFFSET = abiOffset("build_patch");
    public static final long ABI_RESERVED1_OFFSET = abiOffset("reserved1");

    // ------------------------------------------------------------------
    // GPU: LUID e inventario
    // ------------------------------------------------------------------

    /** {@code MaceAdapterLuidV2} (8 bytes, alineación 4). */
    public static final StructLayout ADAPTER_LUID = MemoryLayout.structLayout(
            JAVA_INT.withName("low_part"),
            JAVA_INT.withName("high_part")
    ).withName("MaceAdapterLuidV2");

    public static final long LUID_SIZE = ADAPTER_LUID.byteSize();
    public static final long LUID_LOW_PART_OFFSET = ADAPTER_LUID.byteOffset(PathElement.groupElement("low_part"));
    public static final long LUID_HIGH_PART_OFFSET = ADAPTER_LUID.byteOffset(PathElement.groupElement("high_part"));

    public static final StructLayout GPU_ADAPTER_INFO = MemoryLayout.structLayout(
            JAVA_INT.withName("struct_size"),
            JAVA_INT.withName("version"),
            ADAPTER_LUID.withName("luid"),
            JAVA_INT.withName("vendor_id"),
            JAVA_INT.withName("device_id"),
            JAVA_INT.withName("subsystem_id"),
            JAVA_INT.withName("revision"),
            JAVA_INT.withName("dxgi_flags"),
            JAVA_INT.withName("adapter_index"),
            MemoryLayout.sequenceLayout(GPU_DESCRIPTION_CHARS, WCHAR_T).withName("description")
    ).withName("MaceGpuAdapterInfoV2");

    public static final long GPU_ADAPTER_INFO_SIZE = GPU_ADAPTER_INFO.byteSize();
    public static final long GPU_ADAPTER_LUID_OFFSET = gpuAdapterOffset("luid");
    public static final long GPU_ADAPTER_VENDOR_ID_OFFSET = gpuAdapterOffset("vendor_id");
    public static final long GPU_ADAPTER_DEVICE_ID_OFFSET = gpuAdapterOffset("device_id");
    public static final long GPU_ADAPTER_SUBSYSTEM_ID_OFFSET = gpuAdapterOffset("subsystem_id");
    public static final long GPU_ADAPTER_REVISION_OFFSET = gpuAdapterOffset("revision");
    public static final long GPU_ADAPTER_DXGI_FLAGS_OFFSET = gpuAdapterOffset("dxgi_flags");
    public static final long GPU_ADAPTER_INDEX_OFFSET = gpuAdapterOffset("adapter_index");
    public static final long GPU_ADAPTER_DESCRIPTION_OFFSET = gpuAdapterOffset("description");

    // ------------------------------------------------------------------
    // MaceCpuTelemetryV2
    // ------------------------------------------------------------------

    public static final StructLayout CPU_TELEMETRY = MemoryLayout.structLayout(
            JAVA_INT.withName("struct_size"),
            JAVA_INT.withName("version"),
            JAVA_LONG.withName("timestamp_monotonic_ns"),
            JAVA_INT.withName("cpu_vendor"),
            JAVA_INT.withName("provider_id"),
            METRIC_F32.withName("temperature_c"),
            METRIC_F32.withName("power_w"),
            METRIC_F32.withName("usage_percent"),
            JAVA_INT.withName("power_scope"),
            JAVA_INT.withName("reserved")
    ).withName("MaceCpuTelemetryV2");

    public static final long CPU_TELEMETRY_SIZE = CPU_TELEMETRY.byteSize();
    public static final long CPU_TIMESTAMP_OFFSET = cpuOffset("timestamp_monotonic_ns");
    public static final long CPU_VENDOR_OFFSET = cpuOffset("cpu_vendor");
    public static final long CPU_PROVIDER_OFFSET = cpuOffset("provider_id");
    public static final long CPU_TEMPERATURE_OFFSET = cpuOffset("temperature_c");
    public static final long CPU_POWER_OFFSET = cpuOffset("power_w");
    public static final long CPU_USAGE_OFFSET = cpuOffset("usage_percent");
    public static final long CPU_POWER_SCOPE_OFFSET = cpuOffset("power_scope");
    public static final long CPU_RESERVED_OFFSET = cpuOffset("reserved");

    // ------------------------------------------------------------------
    // MaceGpuTelemetryV2
    // ------------------------------------------------------------------

    public static final StructLayout GPU_TELEMETRY = MemoryLayout.structLayout(
            JAVA_INT.withName("struct_size"),
            JAVA_INT.withName("version"),
            JAVA_LONG.withName("timestamp_monotonic_ns"),
            ADAPTER_LUID.withName("luid"),
            JAVA_INT.withName("provider_id"),
            JAVA_INT.withName("power_scope"),
            METRIC_F32.withName("temperature_c"),
            METRIC_F32.withName("power_w"),
            METRIC_F32.withName("usage_percent"),
            METRIC_U32.withName("fan_rpm"),
            METRIC_U32.withName("fan_percent"),
            METRIC_U64.withName("video_memory_used_bytes"),
            METRIC_U64.withName("video_memory_total_bytes")
    ).withName("MaceGpuTelemetryV2");

    public static final long GPU_TELEMETRY_SIZE = GPU_TELEMETRY.byteSize();
    public static final long GPU_TIMESTAMP_OFFSET = gpuOffset("timestamp_monotonic_ns");
    public static final long GPU_LUID_OFFSET = gpuOffset("luid");
    public static final long GPU_PROVIDER_OFFSET = gpuOffset("provider_id");
    public static final long GPU_POWER_SCOPE_OFFSET = gpuOffset("power_scope");
    public static final long GPU_TEMPERATURE_OFFSET = gpuOffset("temperature_c");
    public static final long GPU_POWER_OFFSET = gpuOffset("power_w");
    public static final long GPU_USAGE_OFFSET = gpuOffset("usage_percent");
    public static final long GPU_FAN_RPM_OFFSET = gpuOffset("fan_rpm");
    public static final long GPU_FAN_PERCENT_OFFSET = gpuOffset("fan_percent");
    public static final long GPU_VRAM_USED_OFFSET = gpuOffset("video_memory_used_bytes");
    public static final long GPU_VRAM_TOTAL_OFFSET = gpuOffset("video_memory_total_bytes");

    // ------------------------------------------------------------------
    // MaceSystemMemoryV2
    // ------------------------------------------------------------------

    public static final StructLayout SYSTEM_MEMORY = MemoryLayout.structLayout(
            JAVA_INT.withName("struct_size"),
            JAVA_INT.withName("version"),
            JAVA_LONG.withName("timestamp_monotonic_ns"),
            METRIC_U64.withName("total_bytes"),
            METRIC_U64.withName("available_bytes")
    ).withName("MaceSystemMemoryV2");

    public static final long SYSTEM_MEMORY_SIZE = SYSTEM_MEMORY.byteSize();
    public static final long MEMORY_TIMESTAMP_OFFSET = SYSTEM_MEMORY.byteOffset(PathElement.groupElement("timestamp_monotonic_ns"));
    public static final long MEMORY_TOTAL_OFFSET = SYSTEM_MEMORY.byteOffset(PathElement.groupElement("total_bytes"));
    public static final long MEMORY_AVAILABLE_OFFSET = SYSTEM_MEMORY.byteOffset(PathElement.groupElement("available_bytes"));

    // ------------------------------------------------------------------
    // Procesos V2
    // ------------------------------------------------------------------

    /** {@code MaceProcessIdentityV2} (16 bytes, alineación 8). */
    public static final StructLayout PROCESS_IDENTITY = MemoryLayout.structLayout(
            JAVA_INT.withName("pid"),
            JAVA_INT.withName("reserved"),
            JAVA_LONG.withName("creation_time_100ns_utc")
    ).withName("MaceProcessIdentityV2");

    public static final long PROCESS_IDENTITY_SIZE = PROCESS_IDENTITY.byteSize();
    public static final long IDENTITY_PID_OFFSET = PROCESS_IDENTITY.byteOffset(PathElement.groupElement("pid"));
    public static final long IDENTITY_RESERVED_OFFSET = PROCESS_IDENTITY.byteOffset(PathElement.groupElement("reserved"));
    public static final long IDENTITY_CREATION_OFFSET =
            PROCESS_IDENTITY.byteOffset(PathElement.groupElement("creation_time_100ns_utc"));

    public static final StructLayout PROCESS_INFO = MemoryLayout.structLayout(
            JAVA_INT.withName("struct_size"),
            JAVA_INT.withName("version"),
            PROCESS_IDENTITY.withName("identity"),
            METRIC_U64.withName("working_set_bytes"),
            METRIC_U64.withName("private_bytes"),
            MemoryLayout.sequenceLayout(PROCESS_NAME_CHARS, WCHAR_T).withName("name")
    ).withName("MaceProcessInfoV2");

    public static final long PROCESS_INFO_SIZE = PROCESS_INFO.byteSize();
    public static final long PROCESS_INFO_IDENTITY_OFFSET = PROCESS_INFO.byteOffset(PathElement.groupElement("identity"));
    public static final long PROCESS_INFO_WORKING_SET_OFFSET =
            PROCESS_INFO.byteOffset(PathElement.groupElement("working_set_bytes"));
    public static final long PROCESS_INFO_PRIVATE_BYTES_OFFSET =
            PROCESS_INFO.byteOffset(PathElement.groupElement("private_bytes"));
    public static final long PROCESS_INFO_NAME_OFFSET = PROCESS_INFO.byteOffset(PathElement.groupElement("name"));

    public static final StructLayout PROCESS_SAMPLE = MemoryLayout.structLayout(
            JAVA_INT.withName("struct_size"),
            JAVA_INT.withName("version"),
            PROCESS_IDENTITY.withName("identity"),
            JAVA_LONG.withName("timestamp_monotonic_ns"),
            METRIC_U64.withName("working_set_bytes"),
            METRIC_U64.withName("private_bytes")
    ).withName("MaceProcessSampleV2");

    public static final long PROCESS_SAMPLE_SIZE = PROCESS_SAMPLE.byteSize();
    public static final long SAMPLE_IDENTITY_OFFSET = PROCESS_SAMPLE.byteOffset(PathElement.groupElement("identity"));
    public static final long SAMPLE_TIMESTAMP_OFFSET =
            PROCESS_SAMPLE.byteOffset(PathElement.groupElement("timestamp_monotonic_ns"));
    public static final long SAMPLE_WORKING_SET_OFFSET =
            PROCESS_SAMPLE.byteOffset(PathElement.groupElement("working_set_bytes"));
    public static final long SAMPLE_PRIVATE_BYTES_OFFSET =
            PROCESS_SAMPLE.byteOffset(PathElement.groupElement("private_bytes"));

    public static final StructLayout RESPONSIVENESS = MemoryLayout.structLayout(
            JAVA_INT.withName("struct_size"),
            JAVA_INT.withName("version"),
            PROCESS_IDENTITY.withName("identity"),
            JAVA_INT.withName("windows_examined"),
            JAVA_INT.withName("windows_hung"),
            JAVA_INT.withName("outcome"),
            JAVA_INT.withName("win32_error")
    ).withName("MaceResponsivenessV2");

    public static final long RESPONSIVENESS_SIZE = RESPONSIVENESS.byteSize();
    public static final long RESPONSIVENESS_IDENTITY_OFFSET = RESPONSIVENESS.byteOffset(PathElement.groupElement("identity"));
    public static final long RESPONSIVENESS_EXAMINED_OFFSET =
            RESPONSIVENESS.byteOffset(PathElement.groupElement("windows_examined"));
    public static final long RESPONSIVENESS_HUNG_OFFSET = RESPONSIVENESS.byteOffset(PathElement.groupElement("windows_hung"));
    public static final long RESPONSIVENESS_OUTCOME_OFFSET = RESPONSIVENESS.byteOffset(PathElement.groupElement("outcome"));
    public static final long RESPONSIVENESS_WIN32_ERROR_OFFSET =
            RESPONSIVENESS.byteOffset(PathElement.groupElement("win32_error"));

    // ------------------------------------------------------------------
    // Caja negra V2
    // ------------------------------------------------------------------

    public static final StructLayout BLACKBOX_HEADER_V2 = MemoryLayout.structLayout(
            JAVA_INT.withName("struct_size"),
            JAVA_INT.withName("version"),
            JAVA_INT.withName("magic"),
            JAVA_INT.withName("record_size"),
            JAVA_INT.withName("capacity_records"),
            JAVA_INT.withName("write_cursor"),
            JAVA_INT.withName("record_count"),
            JAVA_INT.withName("reserved"),
            JAVA_LONG.withName("next_sequence"),
            JAVA_LONG.withName("overwritten_records")
    ).withName("MaceBlackboxHeaderV2");

    public static final long BLACKBOX_HEADER_V2_SIZE = BLACKBOX_HEADER_V2.byteSize();
    public static final long BBX_HEADER_MAGIC_OFFSET = bbxHeaderOffset("magic");
    public static final long BBX_HEADER_RECORD_SIZE_OFFSET = bbxHeaderOffset("record_size");
    public static final long BBX_HEADER_CAPACITY_OFFSET = bbxHeaderOffset("capacity_records");
    public static final long BBX_HEADER_RECORD_COUNT_OFFSET = bbxHeaderOffset("record_count");
    public static final long BBX_HEADER_NEXT_SEQUENCE_OFFSET = bbxHeaderOffset("next_sequence");
    public static final long BBX_HEADER_OVERWRITTEN_OFFSET = bbxHeaderOffset("overwritten_records");

    public static final StructLayout BLACKBOX_RECORD_V2 = MemoryLayout.structLayout(
            JAVA_INT.withName("struct_size"),
            JAVA_INT.withName("version"),
            JAVA_INT.withName("record_type"),
            JAVA_INT.withName("payload_size"),
            JAVA_LONG.withName("sequence"),
            JAVA_LONG.withName("timestamp_monotonic_ns"),
            MemoryLayout.sequenceLayout(BLACKBOX_V2_PAYLOAD_BYTES, JAVA_BYTE).withName("payload")
    ).withName("MaceBlackboxRecordV2");

    public static final long BLACKBOX_RECORD_V2_SIZE = BLACKBOX_RECORD_V2.byteSize();
    public static final long BBX_RECORD_TYPE_OFFSET = bbxRecordOffset("record_type");
    public static final long BBX_RECORD_PAYLOAD_SIZE_OFFSET = bbxRecordOffset("payload_size");
    public static final long BBX_RECORD_SEQUENCE_OFFSET = bbxRecordOffset("sequence");
    public static final long BBX_RECORD_TIMESTAMP_OFFSET = bbxRecordOffset("timestamp_monotonic_ns");
    public static final long BBX_RECORD_PAYLOAD_OFFSET = bbxRecordOffset("payload");

    /** Desplazamientos comunes a todos los registros V2. */
    public static final long STRUCT_SIZE_OFFSET = 0L;
    public static final long STRUCT_VERSION_OFFSET = 4L;

    private static long abiOffset(String field) {
        return ABI_INFO.byteOffset(PathElement.groupElement(field));
    }

    private static long gpuAdapterOffset(String field) {
        return GPU_ADAPTER_INFO.byteOffset(PathElement.groupElement(field));
    }

    private static long cpuOffset(String field) {
        return CPU_TELEMETRY.byteOffset(PathElement.groupElement(field));
    }

    private static long gpuOffset(String field) {
        return GPU_TELEMETRY.byteOffset(PathElement.groupElement(field));
    }

    private static long bbxHeaderOffset(String field) {
        return BLACKBOX_HEADER_V2.byteOffset(PathElement.groupElement(field));
    }

    private static long bbxRecordOffset(String field) {
        return BLACKBOX_RECORD_V2.byteOffset(PathElement.groupElement(field));
    }
}
