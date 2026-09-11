package com.corepulse.infrastructure.nativebridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifica que los layouts FFM coincidan byte a byte con {@code sizeof}/{@code offsetof}
 * de las estructuras de {@code native/include/mace_types.h} compiladas con MSVC x64
 * ({@code #pragma pack(push, 8)}).
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
        assertEquals(24L, NativeLayouts.TELEMETRY_DATA_SIZE, "sizeof(TelemetryData)");
        assertEquals(4L, NativeLayouts.TELEMETRY_DATA.byteAlignment(), "alignof(TelemetryData)");

        assertEquals(0L, NativeLayouts.TELEMETRY_CPU_TEMP_OFFSET);
        assertEquals(4L, NativeLayouts.TELEMETRY_CPU_WATTS_OFFSET);
        assertEquals(8L, NativeLayouts.TELEMETRY_GPU_TEMP_OFFSET);
        assertEquals(12L, NativeLayouts.TELEMETRY_GPU_WATTS_OFFSET);
        assertEquals(16L, NativeLayouts.TELEMETRY_GPU_FAN_RPM_OFFSET);
        assertEquals(20L, NativeLayouts.TELEMETRY_GPU_AVAILABLE_OFFSET);
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
}
