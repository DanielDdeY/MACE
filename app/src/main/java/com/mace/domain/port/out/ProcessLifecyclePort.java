package com.mace.domain.port.out;

import com.mace.domain.model.ProcessEntry;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessInventory;
import com.mace.domain.model.ProcessSampleResult;
import com.mace.domain.model.ResponsivenessResult;
import com.mace.domain.model.TerminationAttempt;

import java.util.List;

/**
 * Puerto de salida para consultar y gestionar procesos userland por <strong>identidad
 * completa</strong> (PID + fecha de creación).
 *
 * <p>Principio no negociable: {@link #terminate(ProcessIdentity)} solo debe invocarse como
 * resultado de una acción manual y explícita del usuario, nunca de forma automática. No existe
 * una variante por PID: una identidad incompleta no puede detectar la reutilización del PID.</p>
 */
public interface ProcessLifecyclePort {

    /**
     * Enumera los procesos de usuario visibles, excluyendo los críticos del sistema.
     *
     * @return inventario con identidad, nombre, ruta (o su motivo de ausencia) y memoria
     */
    ProcessInventory enumerateProcesses();

    /** Compatibilidad: solo la lista de {@link #enumerateProcesses()}. */
    default List<ProcessEntry> listUserlandProcesses() {
        return enumerateProcesses().processes();
    }

    /**
     * Solicita la terminación del proceso exacto elegido por el usuario. La capa nativa vuelve a
     * verificar identidad y política de protección sobre el mismo handle.
     *
     * @param identity identidad tomada de la fila seleccionada
     * @return desenlace específico (terminado, protegido, sin permisos, finalizado, PID reutilizado...)
     */
    TerminationAttempt terminate(ProcessIdentity identity);

    /** Muestra puntual de memoria del proceso identificado (solo lectura). */
    ProcessSampleResult sample(ProcessIdentity identity);

    /** Estado de las ventanas visibles del proceso identificado (solo lectura). */
    ResponsivenessResult checkResponsiveness(ProcessIdentity identity);
}
