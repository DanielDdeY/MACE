package com.mace.infrastructure.telemetry;

import java.util.List;

/**
 * Anillo temporal en RAM con la semántica de {@code CP_Blackbox*V2}: registros de tamaño acotado,
 * secuencias crecientes desde 1 y sobrescritura del más antiguo cuando se llena. No es un
 * historial persistente; {@link PersistentBlackboxSink} copia su contenido a disco.
 */
public interface BlackboxRing extends AutoCloseable {

    /** Tipos admitidos por el anillo nativo ({@code MACE_RECORD_*}). */
    int RECORD_CPU = 1;
    int RECORD_GPU = 2;
    int RECORD_PROCESS_SAMPLE = 3;
    int RECORD_SYSTEM_MEMORY = 4;

    /**
     * @param recordType tipo {@code MACE_RECORD_*}
     * @param payload    registro V2 completo, con {@code struct_size}/{@code version} exactos
     * @return secuencia asignada
     */
    long append(int recordType, byte[] payload);

    /**
     * Copia los registros conservados desde {@code fromSequence} (0 = desde el más antiguo). Si
     * esa secuencia ya se sobrescribió, la lectura empieza en el más antiguo conservado: el
     * llamador detecta el hueco comparando secuencias.
     */
    List<RingRecord> read(long fromSequence);

    RingStats stats();

    /** Libera el anillo. Idempotente. */
    @Override
    void close();

    /**
     * Registro copiado del anillo.
     *
     * @param sequence       secuencia asignada por el anillo
     * @param recordType     tipo {@code MACE_RECORD_*}
     * @param monotonicNanos marca monotónica de la escritura
     * @param payload        copia de la carga útil
     */
    record RingRecord(long sequence, int recordType, long monotonicNanos, byte[] payload) {
    }

    /**
     * Estadísticas del anillo.
     *
     * @param capacityRecords    capacidad en registros
     * @param recordCount        registros conservados
     * @param nextSequence       próxima secuencia a asignar
     * @param overwrittenRecords registros sobrescritos desde la creación
     */
    record RingStats(int capacityRecords, int recordCount, long nextSequence, long overwrittenRecords) {

        public long oldestSequence() {
            return nextSequence - recordCount;
        }
    }
}
