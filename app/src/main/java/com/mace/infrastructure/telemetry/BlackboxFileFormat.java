package com.mace.infrastructure.telemetry;

import java.nio.charset.StandardCharsets;

/**
 * Formato binario versionado del archivo histórico de la caja negra ({@code *.mbbx}),
 * little-endian. El sink Java es su único escritor.
 *
 * <pre>
 * Cabecera (64 bytes)
 *   +0  magic "MACEBBX1"     +8  formatVersion u32   +12 headerSize u32 (64)
 *   +16 recordSize u32 (192)  +20 capacityRecords u32 +24 createdEpochMillis u64
 *   +32 committedRecords u64  +40 flags u32           +44 sessionPid u32
 *   +48 lastFileSequence u64  +56 headerCrc32 u32 (bytes 0..55)  +60 reservado
 *
 * Registro (192 bytes)
 *   +0  fileSequence u64 (desde 1)   +8  type u32   +12 payloadSize u32 (&le; 128)
 *   +16 nativeSequence u64 (0 si es un evento Java)  +24 monotonicNanos u64
 *   +32 wallEpochMillis u64          +40 lostRecords u64 (solo GAP)
 *   +48 payload[128]                 +176 crc32 u32 (bytes 0..175)  +180 reservado
 * </pre>
 *
 * <p>Tipos 1–4: registros V2 copiados del anillo (payload binario exacto de
 * {@code mace_types_v2.h}). {@value #TYPE_GAP}: hueco de secuencia (registros sobrescritos en el
 * anillo antes de copiarse; {@code nativeSequence} = primera secuencia perdida). 60–74: eventos
 * de aplicación con texto UTF-8 truncado a 128 bytes.</p>
 */
public final class BlackboxFileFormat {

    private BlackboxFileFormat() {
    }

    public static final byte[] MAGIC = "MACEBBX1".getBytes(StandardCharsets.US_ASCII);
    public static final int FORMAT_VERSION = 1;
    public static final int HEADER_SIZE = 64;
    public static final int RECORD_SIZE = 192;
    public static final int PAYLOAD_CAPACITY = 128;
    public static final String FILE_EXTENSION = ".mbbx";

    public static final int FLAG_CLEAN_CLOSE = 1;
    public static final int FLAG_SIMULATED = 2;

    public static final int TYPE_GAP = 50;

    // Cabecera
    static final int H_MAGIC = 0;
    static final int H_VERSION = 8;
    static final int H_HEADER_SIZE = 12;
    static final int H_RECORD_SIZE = 16;
    static final int H_CAPACITY = 20;
    static final int H_CREATED = 24;
    static final int H_COMMITTED = 32;
    static final int H_FLAGS = 40;
    static final int H_PID = 44;
    static final int H_LAST_SEQUENCE = 48;
    static final int H_CRC = 56;

    // Registro
    static final int R_FILE_SEQUENCE = 0;
    static final int R_TYPE = 8;
    static final int R_PAYLOAD_SIZE = 12;
    static final int R_NATIVE_SEQUENCE = 16;
    static final int R_MONOTONIC = 24;
    static final int R_WALL = 32;
    static final int R_LOST = 40;
    static final int R_PAYLOAD = 48;
    static final int R_CRC = 176;
}
