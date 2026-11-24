package com.mace.infrastructure.telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Anillo Java con la misma semántica que {@code blackbox_buffer.c}, para el modo simulado y las
 * pruebas sin DLL. Seguro para uso concurrente.
 */
public final class InMemoryBlackboxRing implements BlackboxRing {

    public static final int MAX_CAPACITY = 65_536;
    public static final int MAX_PAYLOAD_BYTES = 128;

    private final RingRecord[] slots;
    private final LongSupplier nanoTime;
    private int writeCursor;
    private int recordCount;
    private long nextSequence = 1;
    private long overwritten;
    private boolean closed;

    public InMemoryBlackboxRing(int capacityRecords) {
        this(capacityRecords, System::nanoTime);
    }

    public InMemoryBlackboxRing(int capacityRecords, LongSupplier nanoTime) {
        if (capacityRecords <= 0 || capacityRecords > MAX_CAPACITY) {
            throw new IllegalArgumentException("capacityRecords debe estar en [1, " + MAX_CAPACITY + "]");
        }
        this.slots = new RingRecord[capacityRecords];
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime no puede ser null");
    }

    @Override
    public synchronized long append(int recordType, byte[] payload) {
        ensureOpen();
        Objects.requireNonNull(payload, "payload no puede ser null");
        if (recordType < RECORD_CPU || recordType > RECORD_SYSTEM_MEMORY) {
            throw new IllegalArgumentException("Tipo de registro no admitido: " + recordType);
        }
        if (payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload debe tener entre 1 y " + MAX_PAYLOAD_BYTES + " bytes");
        }
        long sequence = nextSequence++;
        slots[writeCursor] = new RingRecord(sequence, recordType, nanoTime.getAsLong(), payload.clone());
        writeCursor = (writeCursor + 1) % slots.length;
        if (recordCount < slots.length) {
            recordCount++;
        } else {
            overwritten++;
        }
        return sequence;
    }

    @Override
    public synchronized List<RingRecord> read(long fromSequence) {
        ensureOpen();
        long oldest = nextSequence - recordCount;
        long start = fromSequence == 0 || fromSequence < oldest ? oldest : fromSequence;
        if (start >= nextSequence) {
            return List.of();
        }
        int oldestIndex = (writeCursor + slots.length - recordCount) % slots.length;
        List<RingRecord> records = new ArrayList<>((int) (nextSequence - start));
        for (long sequence = start; sequence < nextSequence; sequence++) {
            RingRecord stored = slots[(int) ((oldestIndex + (sequence - oldest)) % slots.length)];
            records.add(new RingRecord(stored.sequence(), stored.recordType(), stored.monotonicNanos(),
                    stored.payload().clone()));
        }
        return List.copyOf(records);
    }

    @Override
    public synchronized RingStats stats() {
        ensureOpen();
        return new RingStats(slots.length, recordCount, nextSequence, overwritten);
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("El anillo de caja negra está cerrado");
        }
    }
}
