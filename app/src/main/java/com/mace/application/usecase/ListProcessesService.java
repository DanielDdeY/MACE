package com.mace.application.usecase;

import com.mace.domain.model.ProcessEntry;
import com.mace.domain.model.ProcessInventory;
import com.mace.domain.port.out.ProcessLifecyclePort;

import java.util.List;
import java.util.Objects;

/** Caso de uso de consulta de procesos de usuario. */
public final class ListProcessesService {
    private final ProcessLifecyclePort processLifecyclePort;

    public ListProcessesService(ProcessLifecyclePort processLifecyclePort) {
        this.processLifecyclePort = Objects.requireNonNull(processLifecyclePort, "processLifecyclePort no puede ser null");
    }

    /** Inventario completo: procesos con identidad, cantidad omitida por permisos y marca temporal. */
    public ProcessInventory inventory() {
        return processLifecyclePort.enumerateProcesses();
    }

    /** Compatibilidad: solo la lista de procesos. */
    public List<ProcessEntry> list() {
        return inventory().processes();
    }
}
