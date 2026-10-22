package com.mace.domain.port.out;

import com.mace.domain.model.BlackboxEvent;
import com.mace.domain.model.BlackboxStats;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.ProcessSample;

/**
 * Puerto de salida hacia la caja negra: historial persistente de telemetría y eventos.
 *
 * <p>La implementación es dueña exclusiva del archivo histórico. Las muestras de hardware pueden
 * pasar antes por un anillo temporal en RAM (nativo o simulado); si ese anillo sobrescribe
 * registros antes de copiarlos, el sink marca el hueco de secuencia en lugar de ocultarlo.</p>
 */
public interface BlackboxSinkPort {

    /** Registra las lecturas de CPU, cada GPU y memoria global de un sondeo. */
    void record(HardwareSnapshot snapshot);

    /** Registra una muestra de memoria de un proceso (p. ej. el seleccionado o el que alerta). */
    void record(ProcessSample sample);

    /** Registra un evento de aplicación (alertas, solicitudes y resultados de terminación). */
    void recordEvent(BlackboxEvent event);

    /** Copia al almacenamiento persistente lo que aún esté en memoria temporal. */
    default void flush() {
    }

    BlackboxStats stats();
}
