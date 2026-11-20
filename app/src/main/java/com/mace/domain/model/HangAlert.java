package com.mace.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Proceso con al menos una ventana visible que no responde. Solo informa: la decisión de
 * finalizarlo pertenece exclusivamente al usuario.
 *
 * @param identity        proceso afectado
 * @param processName     nombre del ejecutable
 * @param windowsHung     ventanas colgadas
 * @param windowsExamined ventanas visibles examinadas
 * @param detectedAt      instante de la consulta
 */
public record HangAlert(ProcessIdentity identity, String processName, int windowsHung,
                        int windowsExamined, Instant detectedAt) {

    public HangAlert {
        Objects.requireNonNull(identity, "identity no puede ser null");
        Objects.requireNonNull(detectedAt, "detectedAt no puede ser null");
        processName = processName == null || processName.isBlank() ? "PID " + identity.pid().value() : processName;
    }
}
