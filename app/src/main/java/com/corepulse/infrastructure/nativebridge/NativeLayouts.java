package com.corepulse.infrastructure.nativebridge;

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
 * {@code native/include/mace_types.h}.
 *
 * <p>El header nativo usa {@code #pragma pack(push, 8)}; como ningún miembro tiene
 * alineación natural mayor a 8 bytes, el layout coincide con el natural de MSVC x64.
 * Cada {@link StructLayout} de esta clase incluye el <em>padding explícito</em> que el
 * compilador de C inserta, de modo que {@code byteSize()} y los offsets sean idénticos a
 * {@code sizeof} / {@code offsetof} en C.</p>
 *
 * <pre>
 * ProcessData (1584 bytes, alineación 8)
 *   +0     uint32_t pid
 *   +4     wchar_t  name[260]        (520 bytes)
 *   +524   wchar_t  path[520]        (1040 bytes)
 *   +1564  [padding 4]
 *   +1568  uint64_t working_set_bytes
 *   +1576  uint64_t private_bytes
 *
 * TelemetryData (24 bytes, alineación 4)
 *   +0     float    cpu_temp
 *   +4     float    cpu_watts
 *   +8     float    gpu_temp
 *   +12    float    gpu_watts
 *   +16    uint32_t gpu_fan_rpm
 *   +20    uint8_t  is_gpu_available
 *   +21    uint8_t  _padding[3]
 *
 * BlackboxSharedHeader (16 bytes, alineación 4)
 *   +0     uint32_t magic_header
 *   +4     uint32_t ring_buffer_capacity
 *   +8     uint32_t write_cursor
 *   +12    uint32_t record_count
 * </pre>
 *
 * <p>Los offsets se exponen como constantes para leer los campos con
 * {@link java.lang.foreign.MemorySegment#get(ValueLayout.OfInt, long)} y afines, sin
 * depender de la forma de las coordenadas de {@code VarHandle} entre versiones del JDK.</p>
 */
public final class NativeLayouts {

    private NativeLayouts() {
    }

    /** Capacidad de {@code wchar_t name[260]} (MAX_PATH) en {@code ProcessData}. */
    public static final int PROCESS_NAME_CHARS = 260;

    /** Capacidad de {@code wchar_t path[520]} en {@code ProcessData}. */
    public static final int PROCESS_PATH_CHARS = 520;

    /** {@code wchar_t} en Windows x64: 16 bits, UTF-16LE (orden de bytes nativo de la plataforma). */
    public static final ValueLayout.OfChar WCHAR_T = JAVA_CHAR;

    /** Valor de {@code BlackboxSharedHeader.magic_header} escrito por {@code memory_map_stub.c} ('MACE'). */
    public static final int BLACKBOX_MAGIC_HEADER = 0x4D414345;

    // ------------------------------------------------------------------
    // ProcessData
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
    // TelemetryData
    // ------------------------------------------------------------------

    public static final StructLayout TELEMETRY_DATA = MemoryLayout.structLayout(
            JAVA_FLOAT.withName("cpu_temp"),
            JAVA_FLOAT.withName("cpu_watts"),
            JAVA_FLOAT.withName("gpu_temp"),
            JAVA_FLOAT.withName("gpu_watts"),
            JAVA_INT.withName("gpu_fan_rpm"),
            JAVA_BYTE.withName("is_gpu_available"),
            MemoryLayout.paddingLayout(3)
    ).withName("TelemetryData");

    /** {@code sizeof(TelemetryData)} = 24. */
    public static final long TELEMETRY_DATA_SIZE = TELEMETRY_DATA.byteSize();

    public static final long TELEMETRY_CPU_TEMP_OFFSET = TELEMETRY_DATA.byteOffset(PathElement.groupElement("cpu_temp"));
    public static final long TELEMETRY_CPU_WATTS_OFFSET = TELEMETRY_DATA.byteOffset(PathElement.groupElement("cpu_watts"));
    public static final long TELEMETRY_GPU_TEMP_OFFSET = TELEMETRY_DATA.byteOffset(PathElement.groupElement("gpu_temp"));
    public static final long TELEMETRY_GPU_WATTS_OFFSET = TELEMETRY_DATA.byteOffset(PathElement.groupElement("gpu_watts"));
    public static final long TELEMETRY_GPU_FAN_RPM_OFFSET =
            TELEMETRY_DATA.byteOffset(PathElement.groupElement("gpu_fan_rpm"));
    public static final long TELEMETRY_GPU_AVAILABLE_OFFSET =
            TELEMETRY_DATA.byteOffset(PathElement.groupElement("is_gpu_available"));

    // ------------------------------------------------------------------
    // BlackboxSharedHeader (extensión futura: caja negra en memoria compartida)
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
}
