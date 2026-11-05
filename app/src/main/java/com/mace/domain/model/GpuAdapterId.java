package com.mace.domain.model;

/**
 * LUID de un adaptador gráfico ({@code MaceAdapterLuidV2}).
 *
 * <p>Identifica la GPU <strong>solo durante la sesión de Windows</strong>: no es una clave
 * persistente. Se vuelve a enumerar al arrancar o cuando cambian los adaptadores.</p>
 *
 * @param lowPart  {@code uint32_t low_part} (bits tal cual)
 * @param highPart {@code int32_t high_part}
 */
public record GpuAdapterId(int lowPart, int highPart) {

    @Override
    public String toString() {
        return String.format("LUID(%08X:%08X)", highPart, lowPart);
    }
}
