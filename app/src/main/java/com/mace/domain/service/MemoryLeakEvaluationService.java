package com.mace.domain.service;

import com.mace.domain.model.MemoryMetrics;
import com.mace.domain.model.ProcessId;

/**
 * Punto de inserción futuro para el cálculo de fuga de memoria de procesos.
 *
 * <p>Este contrato se prepara {@strong sin activar} para la fase de detector de
 * fugas/cuelgues definida en el MVP. No implementa persistencia ni lógica de
 * detección real; solo expone la firma que la infraestructura o un caso de uso
 * futuro podrá consumir.</p>
 *
 * <p>El dominio nunca decide cerrar o suspender un proceso automáticamente por
 * sospecha de fuga; cualquier acción queda en manos del usuario mediante
 * {@link com.mace.application.usecase.TerminateProcessService}.</p>
 */
public interface MemoryLeakEvaluationService {

    /**
     * Veredicto posible de una evaluación de fuga de memoria.
     */
    enum LeakVerdict {
        /** No se detectan indicios de fuga. */
        NO_LEAK,
        /** El crecimiento de memoria es sospechoso pero no concluyente. */
        SUSPECTED,
        /** Se confirma un patrón de fuga de memoria. */
        CONFIRMED,
        /** No hay información suficiente para evaluar. */
        UNKNOWN
    }

    /**
     * Evalúa si un proceso muestra indicios de fuga de memoria comparando su consumo
     * actual con una línea base.
     *
     * @param pid proceso a evaluar
     * @param current métricas de memoria actuales
     * @param baseline métricas de línea base de referencia
     * @return veredicto de fuga; {@link LeakVerdict#UNKNOWN} cuando no se puede evaluar
     * @throws NullPointerException si cualquiera de los argumentos es {@code null}
     */
    LeakVerdict assess(ProcessId pid, MemoryMetrics current, MemoryMetrics baseline);
}
