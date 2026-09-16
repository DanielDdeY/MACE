package com.mace.application.usecase;

import com.mace.domain.model.ProcessEntry;
import com.mace.domain.port.out.ProcessLifecyclePort;

import java.util.List;
import java.util.Objects;

/** Caso de uso de consulta de procesos de usuario. */
public final class ListProcessesService {
    private final ProcessLifecyclePort processLifecyclePort;

    public ListProcessesService(ProcessLifecyclePort processLifecyclePort) {
        this.processLifecyclePort = Objects.requireNonNull(processLifecyclePort, "processLifecyclePort no puede ser null");
    }

    public List<ProcessEntry> list() {
        return processLifecyclePort.listUserlandProcesses();
    }
}
