package com.mace.domain.port.out;

import com.mace.domain.model.ProcessEntry;
import com.mace.domain.model.ProcessId;

import java.util.List;

/**
 * Puerto de salida para consultar y gestionar el ciclo de vida de procesos userland.
 *
 * Principio no negociable: {@link #terminateProcess(ProcessId)} solo debe invocarse
 * como resultado de una acción manual y explícita del usuario, nunca de forma
 * automática por parte del sistema.
 */
public interface ProcessLifecyclePort {

    /**
     * Enumera los procesos de usuario visibles, excluyendo los procesos críticos
     * del sistema (filtrados por la capa nativa o el mock).
     *
     * @return lista inmutable de procesos userland; vacía si no hay ninguno
     */
    List<ProcessEntry> listUserlandProcesses();

    /**
     * Solicita la terminación de un proceso seleccionado explícitamente por el usuario.
     *
     * @param pid identificador del proceso objetivo
     * @return {@code true} si el proceso fue terminado correctamente
     */
    boolean terminateProcess(ProcessId pid);

    /**
     * Indica si la ventana principal del proceso responde a mensajes
     * (equivalente a IsHungAppWindow en Win32).
     *
     * @param pid identificador del proceso a consultar
     * @return {@code true} si el proceso responde; {@code false} si está colgado
     */
    boolean isWindowResponsive(ProcessId pid);
}
