package com.mace.domain.port.out;

import com.mace.domain.model.HardwareSnapshot;

/**
 * Puerto de salida para la telemetría de hardware V2.
 *
 * <p>Expone el nuevo contrato de la DLL V2 ({@link HardwareSnapshot}) con estado
 * independiente por métrica, sin filtrar estructuras FFM, {@code MemorySegment},
 * LUID crudo ni códigos Win32 hacia la presentación.</p>
 *
 * <p>Este puerto es aditivo: {@link HardwareSensorsPort} se conserva sin cambios para
 * no romper los consumidores existentes mientras Dev 3 y Dev 4 migran.</p>
 */
public interface HardwareSensorsV2Port {

    /**
     * Lee el estado térmico, energético y de memoria del sistema en la versión V2.
     *
     * @return snapshot inmutable con la telemetría del instante de la llamada
     */
    HardwareSnapshot fetchCurrentTelemetryV2();
}
