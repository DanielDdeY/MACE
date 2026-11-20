package com.mace.infrastructure.telemetry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.zip.CRC32;

import static com.mace.infrastructure.telemetry.BlackboxFileFormat.*;

/**
 * Escritor de un archivo {@code *.mbbx}. Se crea siempre con {@code CREATE_NEW}: nunca abre ni
 * sobrescribe un archivo existente. Cada registro lleva CRC32 y la cabecera se actualiza tras
 * cada escritura con el número de registros confirmados; {@link #close()} marca el cierre limpio.
 *
 * <p>Se usan escrituras posicionales de {@link FileChannel} en lugar de {@code FileChannel.map()}:
 * en Windows un archivo mapeado no puede borrarse ni renombrarse hasta que el GC libera el
 * mapeo, lo que impediría aplicar la política de retención dentro de la misma sesión.</p>
 */
final class BlackboxFileWriter implements AutoCloseable {

    private final Path path;
    private final FileChannel channel;
    private final int capacityRecords;
    private final long createdEpochMillis;
    private final int baseFlags;
    private final long sessionPid;
    private long committed;
    private long lastSequence;
    private boolean closed;

    private BlackboxFileWriter(Path path, FileChannel channel, int capacityRecords, long createdEpochMillis,
                               int baseFlags, long sessionPid) {
        this.path = path;
        this.channel = channel;
        this.capacityRecords = capacityRecords;
        this.createdEpochMillis = createdEpochMillis;
        this.baseFlags = baseFlags;
        this.sessionPid = sessionPid;
    }

    /**
     * @throws java.nio.file.FileAlreadyExistsException si el archivo ya existe (no se toca)
     */
    static BlackboxFileWriter create(Path path, int capacityRecords, long createdEpochMillis, boolean simulated,
                                     long sessionPid) throws IOException {
        Objects.requireNonNull(path, "path no puede ser null");
        if (capacityRecords <= 0) {
            throw new IllegalArgumentException("capacityRecords debe ser positivo");
        }
        FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, StandardOpenOption.READ);
        BlackboxFileWriter writer = new BlackboxFileWriter(path, channel, capacityRecords, createdEpochMillis,
                simulated ? FLAG_SIMULATED : 0, sessionPid);
        try {
            writer.writeHeader(false);
            channel.force(true);
        } catch (IOException e) {
            channel.close();
            throw e;
        }
        return writer;
    }

    Path path() {
        return path;
    }

    boolean isFull() {
        return committed >= capacityRecords;
    }

    long committedRecords() {
        return committed;
    }

    /** Escribe un registro y actualiza la cabecera. */
    void append(int type, long nativeSequence, long monotonicNanos, long wallEpochMillis, long lostRecords,
                byte[] payload) {
        if (closed) {
            throw new IllegalStateException("El archivo de caja negra ya está cerrado: " + path);
        }
        if (isFull()) {
            throw new IllegalStateException("El archivo de caja negra está lleno: " + path);
        }
        int size = Math.min(payload.length, PAYLOAD_CAPACITY);
        ByteBuffer record = ByteBuffer.allocate(RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        long sequence = lastSequence + 1;
        record.putLong(R_FILE_SEQUENCE, sequence);
        record.putInt(R_TYPE, type);
        record.putInt(R_PAYLOAD_SIZE, size);
        record.putLong(R_NATIVE_SEQUENCE, nativeSequence);
        record.putLong(R_MONOTONIC, monotonicNanos);
        record.putLong(R_WALL, wallEpochMillis);
        record.putLong(R_LOST, lostRecords);
        record.put(R_PAYLOAD, payload, 0, size);
        record.putInt(R_CRC, crc(record, R_CRC));
        try {
            writeFully(record, HEADER_SIZE + committed * RECORD_SIZE);
            committed++;
            lastSequence = sequence;
            writeHeader(false);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir en la caja negra " + path, e);
        }
    }

    /** Fuerza los datos a disco. */
    void force() {
        if (closed) {
            return;
        }
        try {
            channel.force(false);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo sincronizar la caja negra " + path, e);
        }
    }

    /** Marca el cierre limpio, sincroniza y cierra. Idempotente. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try (channel) {
            writeHeader(true);
            channel.force(true);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cerrar la caja negra " + path, e);
        }
    }

    private void writeHeader(boolean cleanClose) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        header.put(H_MAGIC, MAGIC);
        header.putInt(H_VERSION, FORMAT_VERSION);
        header.putInt(H_HEADER_SIZE, HEADER_SIZE);
        header.putInt(H_RECORD_SIZE, RECORD_SIZE);
        header.putInt(H_CAPACITY, capacityRecords);
        header.putLong(H_CREATED, createdEpochMillis);
        header.putLong(H_COMMITTED, committed);
        header.putInt(H_FLAGS, baseFlags | (cleanClose ? FLAG_CLEAN_CLOSE : 0));
        header.putInt(H_PID, (int) sessionPid);
        header.putLong(H_LAST_SEQUENCE, lastSequence);
        header.putInt(H_CRC, crc(header, H_CRC));
        writeFully(header, 0);
    }

    private void writeFully(ByteBuffer buffer, long position) throws IOException {
        buffer.position(0).limit(buffer.capacity());
        long offset = position;
        while (buffer.hasRemaining()) {
            offset += channel.write(buffer, offset);
        }
    }

    static int crc(ByteBuffer buffer, int length) {
        CRC32 crc = new CRC32();
        crc.update(buffer.array(), buffer.arrayOffset(), length);
        return (int) crc.getValue();
    }
}
