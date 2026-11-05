package com.mace.application.usecase;

import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessSampleResult;
import com.mace.domain.model.ResponsivenessResult;
import com.mace.domain.port.out.ProcessLifecyclePort;

import java.util.Objects;

/** Consultas de solo lectura sobre un proceso identificado (memoria puntual y ventanas). */
public final class ProcessInspectionService {

    private final ProcessLifecyclePort processLifecyclePort;

    public ProcessInspectionService(ProcessLifecyclePort processLifecyclePort) {
        this.processLifecyclePort = Objects.requireNonNull(processLifecyclePort, "processLifecyclePort no puede ser null");
    }

    public ProcessSampleResult sample(ProcessIdentity identity) {
        return processLifecyclePort.sample(Objects.requireNonNull(identity, "identity no puede ser null"));
    }

    public ResponsivenessResult checkResponsiveness(ProcessIdentity identity) {
        return processLifecyclePort.checkResponsiveness(Objects.requireNonNull(identity, "identity no puede ser null"));
    }
}
