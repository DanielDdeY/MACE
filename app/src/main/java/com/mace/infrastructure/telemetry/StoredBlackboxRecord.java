package com.mace.infrastructure.telemetry;

import java.nio.charset.StandardCharsets;

/**
 * Registro leído (o por escribir) del archivo histórico.
 *
 * @param fileSequence   secuencia dentro del archivo (desde 1)
 * @param type           tipo (1–4 V2, {@value BlackboxFileFormat#TYPE_GAP} hueco, 60–74 eventos)
 * @param nativeSequence secuencia del anillo; en un hueco, la primera secuencia perdida
 * @param monotonicNanos marca monotónica
 * @param wallEpochMillis instante de pared en milisegundos UTC
 * @param lostRecords    registros perdidos (solo huecos)
 * @param payload        carga útil (&le; 128 bytes)
 */
public record StoredBlackboxRecord(long fileSequence, int type, long nativeSequence, long monotonicNanos,
                                   long wallEpochMillis, long lostRecords, byte[] payload) {

    public boolean isGap() {
        return type == BlackboxFileFormat.TYPE_GAP;
    }

    /** Texto del payload para eventos de aplicación. */
    public String text() {
        return new String(payload, StandardCharsets.UTF_8);
    }
}
