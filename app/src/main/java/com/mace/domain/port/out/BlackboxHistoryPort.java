package com.mace.domain.port.out;

import com.mace.domain.model.BlackboxSession;
import com.mace.domain.model.BlackboxSessionSummary;

import java.util.List;

/**
 * Puerto de salida de solo lectura sobre el historial persistente de la caja negra. La escritura
 * sigue perteneciendo en exclusiva a {@link BlackboxSinkPort}; consultar nunca modifica archivos.
 */
public interface BlackboxHistoryPort {

    /**
     * @return sesiones guardadas (en cualquier orden); un archivo ilegible aparece como
     *         {@code READ_ERROR} en lugar de interrumpir el listado
     * @throws BlackboxHistoryException si no se puede acceder a la carpeta
     */
    List<BlackboxSessionSummary> listSessions();

    /**
     * @param sessionId {@link BlackboxSessionSummary#id()} obtenido de {@link #listSessions()}
     * @throws BlackboxHistoryException si la sesión no existe o no puede leerse
     */
    BlackboxSession readSession(String sessionId);
}
