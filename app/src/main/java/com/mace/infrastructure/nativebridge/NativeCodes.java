package com.mace.infrastructure.nativebridge;

import com.mace.domain.model.CpuVendor;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.PowerScope;
import com.mace.domain.model.ProcessQueryStatus;
import com.mace.domain.model.ResponsivenessOutcome;
import com.mace.domain.model.SensorProvider;
import com.mace.domain.model.TerminationOutcome;

/**
 * Traducción entre los enteros del contrato C y los enums del dominio (en ambos sentidos, para
 * decodificar sondeos y para codificar registros de la caja negra). Los valores desconocidos de
 * una DLL futura nunca se interpretan como válidos.
 */
public final class NativeCodes {

    private NativeCodes() {
    }

    // MaceMetricState: 0..8 en el mismo orden que MetricState.
    public static MetricState metricState(int code) {
        MetricState[] states = MetricState.values();
        return code >= 0 && code < states.length ? states[code] : MetricState.READ_FAILED;
    }

    public static int metricStateCode(MetricState state) {
        return state.ordinal();
    }

    // MaceProviderId: 0..7 en el mismo orden que SensorProvider.
    public static SensorProvider provider(int code) {
        SensorProvider[] providers = SensorProvider.values();
        return code >= 0 && code < providers.length ? providers[code] : SensorProvider.NONE;
    }

    public static int providerCode(SensorProvider provider) {
        return provider.ordinal();
    }

    // MacePowerScope: 0..5 en el mismo orden que PowerScope.
    public static PowerScope powerScope(int code) {
        PowerScope[] scopes = PowerScope.values();
        return code >= 0 && code < scopes.length ? scopes[code] : PowerScope.UNKNOWN;
    }

    public static int powerScopeCode(PowerScope scope) {
        return scope.ordinal();
    }

    // MACE_CPU_VENDOR_*: 0..2 en el mismo orden que CpuVendor.
    public static CpuVendor cpuVendor(int code) {
        CpuVendor[] vendors = CpuVendor.values();
        return code >= 0 && code < vendors.length ? vendors[code] : CpuVendor.UNKNOWN;
    }

    public static int cpuVendorCode(CpuVendor vendor) {
        return vendor.ordinal();
    }

    /** {@code MACE_RESPONSIVENESS_*} (campo {@code outcome} de {@code MaceResponsivenessV2}). */
    public static ResponsivenessOutcome responsivenessOutcome(int code) {
        return switch (code) {
            case 0 -> ResponsivenessOutcome.RESPONDING;
            case 1 -> ResponsivenessOutcome.HUNG;
            case 2 -> ResponsivenessOutcome.NO_WINDOW;
            case 3 -> ResponsivenessOutcome.PROCESS_EXITED;
            default -> ResponsivenessOutcome.ERROR;
        };
    }

    /** Estado de retorno V2 de una consulta de ventanas que no llegó a producir registro. */
    public static ResponsivenessOutcome responsivenessFromStatus(int status) {
        return switch (status) {
            case NativeStatus.PROCESS_EXITED -> ResponsivenessOutcome.PROCESS_EXITED;
            case NativeStatus.PID_REUSED -> ResponsivenessOutcome.PID_REUSED;
            case NativeStatus.ACCESS_DENIED -> ResponsivenessOutcome.ACCESS_DENIED;
            default -> ResponsivenessOutcome.ERROR;
        };
    }

    /** Estado de retorno V2 de una consulta de proceso (ruta, muestra). */
    public static ProcessQueryStatus processQueryStatus(int status) {
        return switch (status) {
            case NativeStatus.OK -> ProcessQueryStatus.OK;
            case NativeStatus.ACCESS_DENIED -> ProcessQueryStatus.ACCESS_DENIED;
            case NativeStatus.PROTECTED_PROCESS -> ProcessQueryStatus.PROTECTED_PROCESS;
            case NativeStatus.PROCESS_EXITED -> ProcessQueryStatus.PROCESS_EXITED;
            case NativeStatus.PID_REUSED -> ProcessQueryStatus.PID_REUSED;
            default -> ProcessQueryStatus.ERROR;
        };
    }

    /** Estado de retorno de {@code CP_TerminateProcessV2}. */
    public static TerminationOutcome terminationOutcome(int status) {
        return switch (status) {
            case NativeStatus.OK -> TerminationOutcome.TERMINATED;
            case NativeStatus.PROTECTED_PROCESS -> TerminationOutcome.PROTECTED_PROCESS;
            case NativeStatus.ACCESS_DENIED -> TerminationOutcome.ACCESS_DENIED;
            case NativeStatus.PROCESS_EXITED -> TerminationOutcome.PROCESS_EXITED;
            case NativeStatus.PID_REUSED -> TerminationOutcome.PID_REUSED;
            case NativeStatus.INVALID_ARGUMENT -> TerminationOutcome.INVALID_IDENTITY;
            default -> TerminationOutcome.FAILED;
        };
    }
}
