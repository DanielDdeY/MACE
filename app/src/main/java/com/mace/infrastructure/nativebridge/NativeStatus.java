package com.mace.infrastructure.nativebridge;

/**
 * Códigos {@code MaceStatus} de las funciones V2 ({@code native/include/mace_status.h}).
 *
 * <p>Los errores V2 son <strong>negativos</strong>. No se mezclan con los retornos heredados
 * ({@code CP_PollTelemetry}, {@code CP_EnumerateUserlandProcesses}...), que usan {@code -1} o
 * códigos Win32 positivos.</p>
 */
public final class NativeStatus {

    private NativeStatus() {
    }

    public static final int OK = 0;
    public static final int INVALID_ARGUMENT = -1;
    public static final int BUFFER_TOO_SMALL = -2;
    public static final int NOT_INITIALIZED = -3;
    public static final int UNSUPPORTED_VERSION = -4;
    public static final int ACCESS_DENIED = -5;
    public static final int PROCESS_EXITED = -6;
    public static final int PID_REUSED = -7;
    public static final int SENSOR_UNAVAILABLE = -8;
    public static final int DEVICE_NOT_FOUND = -9;
    public static final int BUSY = -10;
    public static final int WINDOWS_ERROR = -11;
    public static final int PROTECTED_PROCESS = -12;

    /** Nombre simbólico para mensajes de diagnóstico. */
    public static String name(int status) {
        return switch (status) {
            case OK -> "MACE_OK";
            case INVALID_ARGUMENT -> "MACE_E_INVALID_ARGUMENT";
            case BUFFER_TOO_SMALL -> "MACE_E_BUFFER_TOO_SMALL";
            case NOT_INITIALIZED -> "MACE_E_NOT_INITIALIZED";
            case UNSUPPORTED_VERSION -> "MACE_E_UNSUPPORTED_VERSION";
            case ACCESS_DENIED -> "MACE_E_ACCESS_DENIED";
            case PROCESS_EXITED -> "MACE_E_PROCESS_EXITED";
            case PID_REUSED -> "MACE_E_PID_REUSED";
            case SENSOR_UNAVAILABLE -> "MACE_E_SENSOR_UNAVAILABLE";
            case DEVICE_NOT_FOUND -> "MACE_E_DEVICE_NOT_FOUND";
            case BUSY -> "MACE_E_BUSY";
            case WINDOWS_ERROR -> "MACE_E_WINDOWS_ERROR";
            case PROTECTED_PROCESS -> "MACE_E_PROTECTED_PROCESS";
            default -> "MaceStatus(" + status + ")";
        };
    }

    // ------------------------------------------------------------------
    // Bits de implemented_capabilities (MACE_CAP_*)
    // ------------------------------------------------------------------

    public static final long CAP_CPU_USAGE = 1L << 0;
    public static final long CAP_CPU_TEMPERATURE = 1L << 1;
    public static final long CAP_CPU_POWER = 1L << 2;
    public static final long CAP_GPU_INVENTORY = 1L << 3;
    public static final long CAP_GPU_TEMPERATURE = 1L << 4;
    public static final long CAP_GPU_POWER = 1L << 5;
    public static final long CAP_GPU_USAGE = 1L << 6;
    public static final long CAP_GPU_FAN_RPM = 1L << 7;
    public static final long CAP_GPU_FAN_PERCENT = 1L << 8;
    public static final long CAP_PROCESS_SAMPLES = 1L << 9;
    public static final long CAP_PROCESS_IDENTITY = 1L << 10;
    public static final long CAP_SAFE_TERMINATION = 1L << 11;
    public static final long CAP_WINDOW_RESPONSIVENESS = 1L << 12;
    public static final long CAP_BLACKBOX_BUFFER = 1L << 13;
    public static final long CAP_SYSTEM_MEMORY = 1L << 14;
    public static final long CAP_GPU_MEMORY = 1L << 15;

    // ------------------------------------------------------------------
    // Tipos de registro del anillo V2 (MACE_RECORD_*)
    // ------------------------------------------------------------------

    public static final int RECORD_CPU_TELEMETRY = 1;
    public static final int RECORD_GPU_TELEMETRY = 2;
    public static final int RECORD_PROCESS_SAMPLE = 3;
    public static final int RECORD_SYSTEM_MEMORY = 4;
}
