package com.mace.domain.model;

import java.util.Objects;

/**
 * Resumen de un archivo de sesión de la caja negra.
 *
 * @param id               identificador estable de la sesión (nombre del archivo)
 * @param location         ubicación completa para mostrarla al usuario
 * @param simulated        {@code true} si se grabó en modo simulado
 * @param originKnown      {@code false} si la cabecera no permite saber si es real o simulada
 * @param state            estado recuperable del archivo
 * @param records          registros con CRC válido
 * @param committedRecords registros confirmados según la cabecera
 * @param gaps             huecos de secuencia marcados
 * @param lostRecords      registros que el anillo temporal perdió antes de copiarse
 */
public record BlackboxSessionSummary(String id, String location, boolean simulated, boolean originKnown,
                                     BlackboxSessionState state, long records, long committedRecords,
                                     long gaps, long lostRecords) {

    public BlackboxSessionSummary {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id no puede estar vacío");
        }
        Objects.requireNonNull(location, "location no puede ser null");
        Objects.requireNonNull(state, "state no puede ser null");
    }

    /** Archivo que no pudo leerse: solo se conocen su id y su ubicación. */
    public static BlackboxSessionSummary unreadable(String id, String location) {
        return new BlackboxSessionSummary(id, location, false, false, BlackboxSessionState.READ_ERROR, 0, 0, 0, 0);
    }

    public BlackboxSessionSummary withState(BlackboxSessionState newState) {
        return new BlackboxSessionSummary(id, location, simulated, originKnown, newState, records,
                committedRecords, gaps, lostRecords);
    }

    /** «Real», «Simulada» u «Origen N/D». */
    public String originLabel() {
        return !originKnown ? "Origen N/D" : simulated ? "Simulada" : "Real";
    }
}
