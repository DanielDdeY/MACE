package com.mace.infrastructure.nativebridge;

import com.mace.infrastructure.telemetry.BlackboxRing;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Anillo temporal de la DLL ({@code CP_BlackboxCreateV2(NULL, ...)}) manejado con su token
 * {@code uint64_t} opaco. Toda escritura y lectura es por copia: ningún puntero del mapeo sale
 * de la DLL. No se usa {@code CP_InitSharedTelemetryBuffer}.
 *
 * <p>El token debe cerrarse antes de {@code CP_ShutdownTelemetry} (que de todos modos cierra los
 * anillos como recuperación). Tras cerrarlo, cualquier operación lanza
 * {@link IllegalStateException}; el doble cierre es un no-op en Java.</p>
 */
public final class NativeBlackboxRing implements BlackboxRing {

    static final int MAX_READ_ATTEMPTS = 3;

    private final NativeApi nativeApi;
    private final long handle;
    private final int capacityRecords;
    private boolean closed;

    private NativeBlackboxRing(NativeApi nativeApi, long handle, int capacityRecords) {
        this.nativeApi = nativeApi;
        this.handle = handle;
        this.capacityRecords = capacityRecords;
    }

    /**
     * Crea un anillo privado del proceso (sin nombre de mapeo).
     *
     * @throws NativeLibraryException si la DLL lo rechaza
     */
    public static NativeBlackboxRing create(NativeApi nativeApi, int capacityRecords) {
        Objects.requireNonNull(nativeApi, "nativeApi no puede ser null");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = arena.allocate(JAVA_LONG);
            MemorySegment win32 = arena.allocate(JAVA_INT);
            int status = nativeApi.blackboxCreateV2(MemorySegment.NULL, capacityRecords, handle, win32);
            if (status != NativeStatus.OK) {
                throw new NativeLibraryException(NativeFunctions.SYMBOL_BLACKBOX_CREATE_V2 + " devolvió "
                        + NativeStatus.name(status) + " (Win32 " + win32.get(JAVA_INT, 0) + ")");
            }
            return new NativeBlackboxRing(nativeApi, handle.get(JAVA_LONG, 0), capacityRecords);
        }
    }

    /** Token opaco de la DLL (solo para diagnóstico). */
    public long handle() {
        return handle;
    }

    @Override
    public synchronized long append(int recordType, byte[] payload) {
        ensureOpen();
        Objects.requireNonNull(payload, "payload no puede ser null");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(payload.length, 8);
            MemorySegment.copy(payload, 0, data, JAVA_BYTE, 0, payload.length);
            MemorySegment sequence = arena.allocate(JAVA_LONG);
            int status = nativeApi.blackboxAppendV2(handle, recordType, data, payload.length, sequence);
            if (status != NativeStatus.OK) {
                throw new NativeLibraryException(NativeFunctions.SYMBOL_BLACKBOX_APPEND_V2 + " devolvió "
                        + NativeStatus.name(status));
            }
            return sequence.get(JAVA_LONG, 0);
        }
    }

    @Override
    public synchronized List<RingRecord> read(long fromSequence) {
        ensureOpen();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment required = arena.allocate(JAVA_INT);
            int capacity = 0;
            MemorySegment buffer = MemorySegment.NULL;
            for (int attempt = 0; attempt < MAX_READ_ATTEMPTS; attempt++) {
                int status = nativeApi.blackboxReadV2(handle, fromSequence, buffer, capacity, required);
                int count = required.get(JAVA_INT, 0);
                if (status == NativeStatus.OK) {
                    return decode(buffer, Math.min(count, capacity));
                }
                if (status != NativeStatus.BUFFER_TOO_SMALL) {
                    throw new NativeLibraryException(NativeFunctions.SYMBOL_BLACKBOX_READ_V2 + " devolvió "
                            + NativeStatus.name(status));
                }
                capacity = Math.min(count, capacityRecords);
                buffer = arena.allocate(NativeLayouts.BLACKBOX_RECORD_V2, capacity);
            }
            throw new NativeLibraryException("El anillo siguió creciendo durante " + MAX_READ_ATTEMPTS + " lecturas");
        }
    }

    private static List<RingRecord> decode(MemorySegment buffer, int count) {
        List<RingRecord> records = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            MemorySegment record = buffer.asSlice(i * NativeLayouts.BLACKBOX_RECORD_V2_SIZE,
                    NativeLayouts.BLACKBOX_RECORD_V2_SIZE);
            V2Records.requireHeader(record, NativeLayouts.BLACKBOX_RECORD_V2_SIZE, "MaceBlackboxRecordV2");
            int payloadSize = Math.min(record.get(JAVA_INT, NativeLayouts.BBX_RECORD_PAYLOAD_SIZE_OFFSET),
                    NativeLayouts.BLACKBOX_V2_PAYLOAD_BYTES);
            byte[] payload = new byte[Math.max(0, payloadSize)];
            MemorySegment.copy(record, JAVA_BYTE, NativeLayouts.BBX_RECORD_PAYLOAD_OFFSET, payload, 0, payload.length);
            records.add(new RingRecord(
                    record.get(JAVA_LONG, NativeLayouts.BBX_RECORD_SEQUENCE_OFFSET),
                    record.get(JAVA_INT, NativeLayouts.BBX_RECORD_TYPE_OFFSET),
                    V2Records.readUnsignedLongSaturated(record, NativeLayouts.BBX_RECORD_TIMESTAMP_OFFSET),
                    payload));
        }
        return List.copyOf(records);
    }

    @Override
    public synchronized RingStats stats() {
        ensureOpen();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment header = arena.allocate(NativeLayouts.BLACKBOX_HEADER_V2);
            int status = nativeApi.blackboxGetStatsV2(handle, header, (int) NativeLayouts.BLACKBOX_HEADER_V2_SIZE);
            if (status != NativeStatus.OK) {
                throw new NativeLibraryException(NativeFunctions.SYMBOL_BLACKBOX_STATS_V2 + " devolvió "
                        + NativeStatus.name(status));
            }
            V2Records.requireHeader(header, NativeLayouts.BLACKBOX_HEADER_V2_SIZE, "MaceBlackboxHeaderV2");
            return new RingStats(
                    header.get(JAVA_INT, NativeLayouts.BBX_HEADER_CAPACITY_OFFSET),
                    header.get(JAVA_INT, NativeLayouts.BBX_HEADER_RECORD_COUNT_OFFSET),
                    header.get(JAVA_LONG, NativeLayouts.BBX_HEADER_NEXT_SEQUENCE_OFFSET),
                    header.get(JAVA_LONG, NativeLayouts.BBX_HEADER_OVERWRITTEN_OFFSET));
        }
    }

    /** Cierra el token con {@code CP_BlackboxCloseV2}. Idempotente. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            nativeApi.blackboxCloseV2(handle);
        } catch (IllegalStateException e) {
            // La telemetría ya se apagó: CP_ShutdownTelemetry cerró el anillo como recuperación.
        }
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("El anillo nativo de caja negra está cerrado");
        }
    }
}
