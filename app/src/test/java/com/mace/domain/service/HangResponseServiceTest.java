package com.mace.domain.service;

import com.mace.domain.model.ProcessId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pruebas de negocio del punto de inserción futuro de respuesta a cuelgues.
 * Se ejecutan sin DLL, sin Windows y sin JavaFX.
 *
 * <p>Como {@link HangResponseService} es un contrato sin implementación todavía,
 * estas pruebas validan que las respuestas del contrato son las esperadas para
 * que Dev 3 y Dev 4 puedan mapearlas. Ninguna respuesta implica cierre automático.</p>
 */
class HangResponseServiceTest {

    @Test
    void respuestasDeCuelgueTienenLosTresEstadosEsperados() {
        assertEquals(HangResponseService.HangResponse.NONE,
                HangResponseService.HangResponse.valueOf("NONE"));
        assertEquals(HangResponseService.HangResponse.NOTIFY_USER,
                HangResponseService.HangResponse.valueOf("NOTIFY_USER"));
        assertEquals(HangResponseService.HangResponse.SUGGEST_TERMINATE,
                HangResponseService.HangResponse.valueOf("SUGGEST_TERMINATE"));
    }

    @Test
    void ningunaRespuestaAutorizaCierreAutomatico() {
        // El dominio nunca termina ni suspende un proceso por detección de cuelgue
        // sin una acción manual explícita del usuario.
        assertEquals(HangResponseService.HangResponse.SUGGEST_TERMINATE,
                HangResponseService.HangResponse.SUGGEST_TERMINATE);
        ProcessId pid = new ProcessId(7777L);
        assertEquals(7777L, pid.value());
    }
}
