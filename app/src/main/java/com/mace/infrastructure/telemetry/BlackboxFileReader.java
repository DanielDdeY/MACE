package com.mace.infrastructure.telemetry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.mace.infrastructure.telemetry.BlackboxFileFormat.*;

/**
 * Lector tolerante de archivos {@code *.mbbx}, de solo lectura. Recupera archivos incompletos
 * (sesión interrumpida, corte de energía): lee registros consecutivos mientras su CRC sea válido
 * y se detiene en el primero dañado o truncado, sin modificar el archivo.
 */
public final class BlackboxFileReader {

    private BlackboxFileReader() {
    }

    /**
     * Contenido recuperado.
     *
     * @param path             archivo leído
     * @param headerValid      magic, versión, tamaños y CRC de cabecera correctos
     * @param cleanClose       la sesión cerró el archivo correctamente
     * @param simulated        datos de modo simulado
     * @param committedRecords registros confirmados según la cabecera
     * @param records          registros con CRC válido, en orden
     * @param trailingDamaged  {@code true} si quedaron bytes dañados o truncados al final
     */
    public record Contents(Path path, boolean headerValid, boolean cleanClose, boolean simulated,
                           long committedRecords, List<StoredBlackboxRecord> records, boolean trailingDamaged) {

        public boolean isIncomplete() {
            return !headerValid || !cleanClose || trailingDamaged;
        }

        public long gapCount() {
            return records.stream().filter(StoredBlackboxRecord::isGap).count();
        }
    }

    public static Contents read(Path path) throws IOException {
        Objects.requireNonNull(path, "path no puede ser null");
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size < HEADER_SIZE) {
                return new Contents(path, false, false, false, 0, List.of(), size > 0);
            }
            ByteBuffer header = readAt(channel, 0, HEADER_SIZE);
            byte[] magic = new byte[MAGIC.length];
            header.get(H_MAGIC, magic);
            boolean headerValid = Arrays.equals(magic, MAGIC)
                    && header.getInt(H_VERSION) == FORMAT_VERSION
                    && header.getInt(H_HEADER_SIZE) == HEADER_SIZE
                    && header.getInt(H_RECORD_SIZE) == RECORD_SIZE
                    && header.getInt(H_CRC) == BlackboxFileWriter.crc(header, H_CRC);
            int flags = header.getInt(H_FLAGS);
            long committed = header.getLong(H_COMMITTED);

            List<StoredBlackboxRecord> records = new ArrayList<>();
            long position = HEADER_SIZE;
            long expectedSequence = 1;
            boolean damaged = false;
            while (position + RECORD_SIZE <= size) {
                ByteBuffer record = readAt(channel, position, RECORD_SIZE);
                if (record.getInt(R_CRC) != BlackboxFileWriter.crc(record, R_CRC)
                        || record.getLong(R_FILE_SEQUENCE) != expectedSequence) {
                    damaged = true;
                    break;
                }
                int payloadSize = Math.min(Math.max(record.getInt(R_PAYLOAD_SIZE), 0), PAYLOAD_CAPACITY);
                byte[] payload = new byte[payloadSize];
                record.get(R_PAYLOAD, payload);
                records.add(new StoredBlackboxRecord(record.getLong(R_FILE_SEQUENCE), record.getInt(R_TYPE),
                        record.getLong(R_NATIVE_SEQUENCE), record.getLong(R_MONOTONIC), record.getLong(R_WALL),
                        record.getLong(R_LOST), payload));
                position += RECORD_SIZE;
                expectedSequence++;
            }
            if (!damaged && position != size) {
                damaged = true;
            }
            return new Contents(path, headerValid, headerValid && (flags & FLAG_CLEAN_CLOSE) != 0,
                    (flags & FLAG_SIMULATED) != 0, committed, List.copyOf(records), damaged);
        }
    }

    private static ByteBuffer readAt(FileChannel channel, long position, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        long offset = position;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, offset);
            if (read < 0) {
                break;
            }
            offset += read;
        }
        buffer.clear();
        return buffer;
    }
}
