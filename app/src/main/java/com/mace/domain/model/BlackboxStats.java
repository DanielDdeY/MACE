package com.mace.domain.model;

import java.util.Objects;

/**
 * Estado de la caja negra para diagnóstico y UI.
 *
 * @param enabled          {@code true} si hay un sink persistente activo
 * @param recordsPersisted registros escritos en disco en esta sesión
 * @param recordsLost      registros que el anillo temporal sobrescribió antes de copiarse
 * @param gapCount         huecos de secuencia marcados en el archivo
 * @param location         carpeta o archivo actual ("" si no hay)
 */
public record BlackboxStats(boolean enabled, long recordsPersisted, long recordsLost, long gapCount, String location) {

    public BlackboxStats {
        location = Objects.requireNonNullElse(location, "");
    }

    public static BlackboxStats disabled() {
        return new BlackboxStats(false, 0, 0, 0, "");
    }

    public boolean hasGaps() {
        return gapCount > 0;
    }
}
