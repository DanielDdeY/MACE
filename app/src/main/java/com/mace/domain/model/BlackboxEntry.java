package com.mace.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Registro de una sesión de la caja negra, ya interpretado para mostrarlo.
 *
 * @param sequence       posición del registro en el archivo (desde 1)
 * @param kind           clase de registro
 * @param eventType      tipo de evento si {@code kind == EVENT}; {@code null} en otro caso
 * @param time           instante de pared; {@code null} si el registro tiene una hora inválida
 * @param nativeSequence secuencia del anillo nativo; en un hueco, la primera perdida
 * @param lostRecords    registros perdidos (solo huecos)
 * @param summary        texto legible: lectura decodificada, texto del evento o descripción del hueco
 */
public record BlackboxEntry(long sequence, BlackboxEntryKind kind, BlackboxEventType eventType, Instant time,
                            long nativeSequence, long lostRecords, String summary) {

    public BlackboxEntry {
        Objects.requireNonNull(kind, "kind no puede ser null");
        if ((kind == BlackboxEntryKind.EVENT) != (eventType != null)) {
            throw new IllegalArgumentException("Solo los eventos llevan eventType");
        }
        summary = summary == null ? "" : summary;
    }

    public Optional<BlackboxEventType> event() {
        return Optional.ofNullable(eventType);
    }

    public Optional<Instant> timeOptional() {
        return Optional.ofNullable(time);
    }

    public boolean isGap() {
        return kind == BlackboxEntryKind.GAP;
    }

    /** Etiqueta para la columna «Tipo». */
    public String typeLabel() {
        if (eventType == null) {
            return kind.label();
        }
        return switch (eventType) {
            case SESSION_START -> "Inicio de sesión";
            case SESSION_END -> "Fin de sesión";
            case THERMAL_ALERT -> "Alerta térmica";
            case LEAK_ALERT -> "Alerta de memoria";
            case HANG_ALERT -> "Programa sin respuesta";
            case TERMINATION_REQUEST -> "Solicitud de finalización";
            case TERMINATION_RESULT -> "Resultado de finalización";
        };
    }
}
