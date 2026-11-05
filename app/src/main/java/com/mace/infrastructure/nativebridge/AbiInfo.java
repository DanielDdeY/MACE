package com.mace.infrastructure.nativebridge;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Copia Java de {@code MaceAbiInfoV2} devuelta por {@code CP_GetAbiInfo}.
 *
 * <p>Un bit en {@code capabilities} solo indica que la función está implementada en la DLL,
 * <strong>no</strong> que el sensor esté físicamente disponible en este equipo.</p>
 */
public record AbiInfo(
        int structSize,
        int version,
        int abiMajor,
        int abiMinor,
        int processDataSize,
        int telemetryDataSize,
        int blackboxHeaderSize,
        int wcharSize,
        int pointerSize,
        int reserved0,
        long capabilities,
        int buildMajor,
        int buildMinor,
        int buildPatch,
        int reserved1
) {

    /** Decodifica los 64 bytes escritos por {@code CP_GetAbiInfo}. */
    public static AbiInfo read(MemorySegment record) {
        Objects.requireNonNull(record, "record no puede ser null");
        if (record.byteSize() < NativeLayouts.ABI_INFO_SIZE) {
            throw new IllegalArgumentException("MaceAbiInfoV2 requiere " + NativeLayouts.ABI_INFO_SIZE + " bytes");
        }
        return new AbiInfo(
                record.get(JAVA_INT, NativeLayouts.ABI_STRUCT_SIZE_OFFSET),
                record.get(JAVA_INT, NativeLayouts.ABI_VERSION_OFFSET),
                record.get(JAVA_INT, NativeLayouts.ABI_MAJOR_OFFSET),
                record.get(JAVA_INT, NativeLayouts.ABI_MINOR_OFFSET),
                record.get(JAVA_INT, NativeLayouts.ABI_PROCESS_DATA_SIZE_OFFSET),
                record.get(JAVA_INT, NativeLayouts.ABI_TELEMETRY_DATA_SIZE_OFFSET),
                record.get(JAVA_INT, NativeLayouts.ABI_BLACKBOX_HEADER_SIZE_OFFSET),
                record.get(JAVA_INT, NativeLayouts.ABI_WCHAR_SIZE_OFFSET),
                record.get(JAVA_INT, NativeLayouts.ABI_POINTER_SIZE_OFFSET),
                record.get(JAVA_INT, NativeLayouts.ABI_RESERVED0_OFFSET),
                record.get(JAVA_LONG, NativeLayouts.ABI_CAPABILITIES_OFFSET),
                record.get(JAVA_INT, NativeLayouts.ABI_BUILD_MAJOR_OFFSET),
                record.get(JAVA_INT, NativeLayouts.ABI_BUILD_MINOR_OFFSET),
                record.get(JAVA_INT, NativeLayouts.ABI_BUILD_PATCH_OFFSET),
                record.get(JAVA_INT, NativeLayouts.ABI_RESERVED1_OFFSET));
    }

    /** Versión de compilación, p. ej. «1.4.2». */
    public String buildVersion() {
        return Integer.toUnsignedString(buildMajor) + "." + Integer.toUnsignedString(buildMinor)
                + "." + Integer.toUnsignedString(buildPatch);
    }

    /** Versión ABI, p. ej. «1.4». */
    public String abiVersion() {
        return Integer.toUnsignedString(abiMajor) + "." + Integer.toUnsignedString(abiMinor);
    }

    public boolean hasCapability(long bit) {
        return (capabilities & bit) == bit;
    }
}
