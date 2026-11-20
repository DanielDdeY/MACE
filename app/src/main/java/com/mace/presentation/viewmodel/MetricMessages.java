package com.mace.presentation.viewmodel;

import com.mace.domain.model.MetricState;

/** User-facing explanations only; never include exception text or local security details. */
public final class MetricMessages {
    private MetricMessages() {}
    public static String message(MetricState state) {
        return switch (state) {
            case VALID -> "";
            case NOT_ELEVATED -> "Ejecuta MACE como administrador para ver la temperatura y la potencia de la CPU.";
            case PAWNIO_MISSING -> "PawnIO no está instalado. Los sensores de CPU no están disponibles.";
            case PAWNIO_STOPPED -> "PawnIO está detenido. Los sensores de CPU no están disponibles.";
            case REBOOT_REQUIRED -> "Para habilitar los sensores de CPU, reinicia Windows. Las demás métricas disponibles seguirán funcionando";
            case HELPER_UNAVAILABLE -> "El componente de sensores no está disponible. Revisa o repara la instalación de MACE.";
            case HELPER_RETRYING -> "El componente de sensores no responde. MACE seguirá intentándolo.";
            case HELPER_UNTRUSTED -> "El componente de sensores no pudo verificarse. Repara la instalación de MACE.";
            case HELPER_CONTAINMENT_FAILED -> "Windows no pudo iniciar el componente de sensores de forma segura. Revisa las restricciones de ejecución o repara MACE.";
            case WARMING_UP -> "Preparando las lecturas de los sensores.";
            default -> "La lectura no está disponible.";
        };
    }
}
