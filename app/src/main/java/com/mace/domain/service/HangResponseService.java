package com.mace.domain.service;

import com.mace.domain.model.ProcessId;

/**
 * Punto de inserción futuro para la respuesta ante procesos colgados ("hang").
 *
 * <p>Este contrato se prepara {@strong sin activar} para la fase de detector de
 * fugas/cuelgues definida en el MVP. No ejecuta ninguna acción correctiva
 * automática; solo informa al llamante qué respuesta se sugeriría.</p>
 *
 * <p>El dominio nunca termina ni suspende un proceso por detección de cuelgue sin
 * una acción manual explícita del usuario.</p>
 */
public interface HangResponseService {

    /**
     * Respuesta sugerida ante un proceso que no responde.
     */
    enum HangResponse {
        /** No se requiere acción del dominio. */
        NONE,
        /** Notificar al usuario sin terminar el proceso. */
        NOTIFY_USER,
        /** Sugerir al usuario que termine el proceso manualmente. */
        SUGGEST_TERMINATE
    }

    /**
     * Evalúa la respuesta sugerida para un proceso dado su estado de respuesta.
     *
     * @param pid proceso a evaluar
     * @param windowResponsive {@code true} si la ventana responde a mensajes
     * @return respuesta sugerida; nunca implica cierre automático
     * @throws NullPointerException si {@code pid} es {@code null}
     */
    HangResponse evaluate(ProcessId pid, boolean windowResponsive);
}
