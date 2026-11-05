package com.mace.domain.port.out;

import com.mace.domain.model.GpuAdapter;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.TelemetrySnapshot;

import java.util.List;

/**
 * Puerto de salida hacia los sensores de hardware (CPU, cada GPU y memoria global).
 *
 * <p>El dominio consume telemetría a través de esta interfaz sin conocer si la implementación
 * usa la DLL nativa (FFM) o un mock simulado. Cada llamada a {@link #fetchHardwareSnapshot()}
 * es <em>un</em> sondeo físico: la aplicación debe tener un único bucle que lo invoque.</p>
 */
public interface HardwareSensorsPort {

    /**
     * Sondea CPU, cada GPU enumerada y la memoria del sistema.
     *
     * @return snapshot con estado individual por métrica
     */
    HardwareSnapshot fetchHardwareSnapshot();

    /**
     * Vuelve a enumerar las GPU físicas. Los LUID solo valen en la sesión actual de Windows.
     *
     * @return GPU en orden DXGI; vacía si no hay GPU física
     */
    List<GpuAdapter> listGpuAdapters();

    /**
     * Compatibilidad: snapshot heredado derivado de {@link #fetchHardwareSnapshot()} según las
     * reglas de {@link TelemetrySnapshot#fromHardwareSnapshot}. Realiza un sondeo completo; no
     * combinar con {@link #fetchHardwareSnapshot()} en el mismo ciclo.
     */
    default TelemetrySnapshot fetchCurrentTelemetry() {
        return TelemetrySnapshot.fromHardwareSnapshot(fetchHardwareSnapshot());
    }
}
