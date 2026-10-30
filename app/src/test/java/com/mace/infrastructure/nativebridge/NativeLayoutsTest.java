package com.mace.infrastructure.nativebridge;

import org.junit.jupiter.api.Test;

import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.StructLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica que los layouts FFM coincidan byte a byte con {@code sizeof}/{@code offsetof}
 * de {@code native/include/mace_types.h}, {@code mace_types_v2.h} y {@code mace_extensions.h}
 * compiladas con MSVC x64 ({@code #pragma pack(push, 8)}). Los valores esperados son los mismos
 * que comprueba {@code native/tests/test_abi_layouts.c}.
 */
class NativeLayoutsTest {

    @Test
    void processDataCoincideConSizeofYOffsetofDeC() {
        assertEquals(1584L, NativeLayouts.PROCESS_DATA_SIZE, "sizeof(ProcessData)");
        assertEquals(8L, NativeLayouts.PROCESS_DATA.byteAlignment(), "alignof(ProcessData)");

        assertEquals(0L, NativeLayouts.PROCESS_PID_OFFSET, "offsetof(pid)");
        assertEquals(4L, NativeLayouts.PROCESS_NAME_OFFSET, "offsetof(name)");
        assertEquals(524L, NativeLayouts.PROCESS_PATH_OFFSET, "offsetof(path)");
        assertEquals(1568L, NativeLayouts.PROCESS_WORKING_SET_OFFSET, "offsetof(working_set_bytes)");
        assertEquals(1576L, NativeLayouts.PROCESS_PRIVATE_BYTES_OFFSET, "offsetof(private_bytes)");
    }

    @Test
    void telemetryDataCoincideConSizeofYOffsetofDeC() {
        assertEquals(28L, NativeLayouts.TELEMETRY_DATA_SIZE, "sizeof(TelemetryData)");
        assertEquals(4L, NativeLayouts.TELEMETRY_DATA.byteAlignment(), "alignof(TelemetryData)");

        assertEquals(0L, NativeLayouts.TELEMETRY_CPU_TEMP_OFFSET);
        assertEquals(4L, NativeLayouts.TELEMETRY_CPU_WATTS_OFFSET);
        assertEquals(8L, NativeLayouts.TELEMETRY_GPU_TEMP_OFFSET);
        assertEquals(12L, NativeLayouts.TELEMETRY_GPU_WATTS_OFFSET);
        assertEquals(16L, NativeLayouts.TELEMETRY_GPU_FAN_RPM_OFFSET);
        assertEquals(20L, NativeLayouts.TELEMETRY_GPU_AVAILABLE_OFFSET);
        assertEquals(21L, NativeLayouts.TELEMETRY_CPU_SENSOR_AVAILABLE_OFFSET);
        assertEquals(24L, NativeLayouts.TELEMETRY_CPU_USAGE_OFFSET);
    }

    @Test
    void blackboxSharedHeaderCoincideConSizeofYOffsetofDeC() {
        assertEquals(16L, NativeLayouts.BLACKBOX_SHARED_HEADER_SIZE, "sizeof(BlackboxSharedHeader)");
        assertEquals(4L, NativeLayouts.BLACKBOX_SHARED_HEADER.byteAlignment());

        assertEquals(0L, NativeLayouts.BLACKBOX_MAGIC_OFFSET);
        assertEquals(4L, NativeLayouts.BLACKBOX_CAPACITY_OFFSET);
        assertEquals(8L, NativeLayouts.BLACKBOX_WRITE_CURSOR_OFFSET);
        assertEquals(12L, NativeLayouts.BLACKBOX_RECORD_COUNT_OFFSET);
        assertEquals(0x4D414345, NativeLayouts.BLACKBOX_MAGIC_HEADER, "'MACE'");
    }

    @Test
    void wcharTEsDeDieciseisBits() {
        assertEquals(2L, NativeLayouts.WCHAR_T.byteSize());
        assertEquals(260L * 2L, NativeLayouts.PROCESS_PATH_OFFSET - NativeLayouts.PROCESS_NAME_OFFSET,
                "name[260] ocupa 520 bytes");
    }

    // ------------------------------------------------------------------
    // Registros V2
    // ------------------------------------------------------------------

    @Test
    void metricasV2() {
        assertLayout(NativeLayouts.METRIC_F32, 8, 4, "value", 0, "state", 4);
        assertLayout(NativeLayouts.METRIC_U32, 8, 4, "value", 0, "state", 4);
        assertLayout(NativeLayouts.METRIC_U64, 16, 8, "value", 0, "state", 8, "reserved", 12);
    }

    @Test
    void abiInfoV2() {
        assertLayout(NativeLayouts.ABI_INFO, 64, 8,
                "struct_size", 0, "version", 4, "abi_major", 8, "abi_minor", 12,
                "process_data_size", 16, "telemetry_data_size", 20, "blackbox_header_size", 24,
                "wchar_size", 28, "pointer_size", 32, "reserved0", 36, "implemented_capabilities", 40,
                "build_major", 48, "build_minor", 52, "build_patch", 56, "reserved1", 60);
    }

    @Test
    void inventarioGpuV2() {
        assertLayout(NativeLayouts.ADAPTER_LUID, 8, 4, "low_part", 0, "high_part", 4);
        assertLayout(NativeLayouts.GPU_ADAPTER_INFO, 296, 4,
                "struct_size", 0, "version", 4, "luid", 8, "vendor_id", 16, "device_id", 20,
                "subsystem_id", 24, "revision", 28, "dxgi_flags", 32, "adapter_index", 36, "description", 40);
    }

    @Test
    void cpuTelemetryV2() {
        assertLayout(NativeLayouts.CPU_TELEMETRY, 56, 8,
                "struct_size", 0, "version", 4, "timestamp_monotonic_ns", 8, "cpu_vendor", 16,
                "provider_id", 20, "temperature_c", 24, "power_w", 32, "usage_percent", 40,
                "power_scope", 48, "reserved", 52);
    }

    @Test
    void gpuTelemetryV2() {
        assertLayout(NativeLayouts.GPU_TELEMETRY, 104, 8,
                "struct_size", 0, "version", 4, "timestamp_monotonic_ns", 8, "luid", 16, "provider_id", 24,
                "power_scope", 28, "temperature_c", 32, "power_w", 40, "usage_percent", 48, "fan_rpm", 56,
                "fan_percent", 64, "video_memory_used_bytes", 72, "video_memory_total_bytes", 88);
    }

    @Test
    void memoriaDelSistemaV2() {
        assertLayout(NativeLayouts.SYSTEM_MEMORY, 48, 8,
                "struct_size", 0, "version", 4, "timestamp_monotonic_ns", 8, "total_bytes", 16, "available_bytes", 32);
    }

    @Test
    void procesosV2() {
        assertLayout(NativeLayouts.PROCESS_IDENTITY, 16, 8, "pid", 0, "reserved", 4, "creation_time_100ns_utc", 8);
        assertLayout(NativeLayouts.PROCESS_INFO, 576, 8,
                "struct_size", 0, "version", 4, "identity", 8, "working_set_bytes", 24, "private_bytes", 40, "name", 56);
        assertLayout(NativeLayouts.PROCESS_SAMPLE, 64, 8,
                "struct_size", 0, "version", 4, "identity", 8, "timestamp_monotonic_ns", 24,
                "working_set_bytes", 32, "private_bytes", 48);
        assertLayout(NativeLayouts.RESPONSIVENESS, 40, 8,
                "struct_size", 0, "version", 4, "identity", 8, "windows_examined", 24, "windows_hung", 28,
                "outcome", 32, "win32_error", 36);
    }

    @Test
    void cajaNegraV2() {
        assertLayout(NativeLayouts.BLACKBOX_HEADER_V2, 48, 8,
                "struct_size", 0, "version", 4, "magic", 8, "record_size", 12, "capacity_records", 16,
                "write_cursor", 20, "record_count", 24, "reserved", 28, "next_sequence", 32, "overwritten_records", 40);
        assertLayout(NativeLayouts.BLACKBOX_RECORD_V2, 160, 8,
                "struct_size", 0, "version", 4, "record_type", 8, "payload_size", 12, "sequence", 16,
                "timestamp_monotonic_ns", 24, "payload", 32);
        assertEquals(0x4D414332, NativeLayouts.BLACKBOX_V2_MAGIC, "'MAC2'");
    }

    @Test
    void lasConstantesDeOffsetCoincidenConLosLayouts() {
        assertEquals(56L, NativeLayouts.GPU_FAN_RPM_OFFSET);
        assertEquals(88L, NativeLayouts.GPU_VRAM_TOTAL_OFFSET);
        assertEquals(4L, NativeLayouts.METRIC_32_STATE_OFFSET);
        assertEquals(8L, NativeLayouts.METRIC_64_STATE_OFFSET);
        assertEquals(56L, NativeLayouts.PROCESS_INFO_NAME_OFFSET);
        assertEquals(32L, NativeLayouts.RESPONSIVENESS_OUTCOME_OFFSET);
        assertEquals(40L, NativeLayouts.BBX_HEADER_OVERWRITTEN_OFFSET);
        assertEquals(32L, NativeLayouts.BBX_RECORD_PAYLOAD_OFFSET);
    }

    /** {@code fields} alterna nombre y offset esperado. */
    private static void assertLayout(StructLayout layout, long size, long alignment, Object... fields) {
        assertEquals(size, layout.byteSize(), "sizeof(" + layout.name().orElse("?") + ")");
        assertEquals(alignment, layout.byteAlignment(), "alignof(" + layout.name().orElse("?") + ")");
        for (int i = 0; i < fields.length; i += 2) {
            String field = (String) fields[i];
            long expected = ((Number) fields[i + 1]).longValue();
            assertEquals(expected, layout.byteOffset(PathElement.groupElement(field)),
                    layout.name().orElse("?") + "." + field);
        }
    }
}
