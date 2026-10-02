// 1. Definición de macro de exportación antes de la cabecera pública
//    (CMake ya la define para el target mace_native; esto cubre otras compilaciones)
#ifndef MACE_EXPORTS
#define MACE_EXPORTS
#endif

#include "mace_api.h"
#include "telemetry/cpu_sensors.h"

// Prototipos internos de módulos
// (los de CPU vienen de telemetry/cpu_sensors.h para que el compilador verifique las firmas)
extern bool Nvml_Init(void);
extern void Nvml_Shutdown(void);
extern bool Nvml_PollMetrics(float* out_temp, float* out_watts, uint32_t* out_fan_rpm);
extern int  EnumerateProcesses(ProcessData* out_buffer, uint32_t max_count, uint32_t* out_count);
extern int  TerminateProcessByPid(uint32_t pid);
extern int  CheckWindowHung(uint32_t pid);
extern void* SetupSharedTelemetryBuffer(const wchar_t* mapping_name, uint32_t buffer_size);

static bool g_telemetry_ready = false;

CP_API int CP_InitializeTelemetry(void) {
    g_telemetry_ready = Nvml_Init();
    // Sensores MSR de CPU: intento único; si WinRing0 no está, se informa como no disponible
    CpuSensors_Init();
    return 0;
}

CP_API void CP_ShutdownTelemetry(void) {
    Nvml_Shutdown();
    CpuSensors_Shutdown();
    g_telemetry_ready = false;
}

CP_API int CP_PollTelemetry(TelemetryData* out_data) {
    if (!out_data) return -1;

    // Lectura de CPU (temperatura, watts y uso del sistema)
    CpuSensors_Poll(&out_data->cpu_temp, &out_data->cpu_watts, &out_data->cpu_usage);
    out_data->is_cpu_sensor_available = CpuSensors_HardwareAvailable() ? 1 : 0;

    // Lectura de GPU (NVIDIA NVML con fallback)
    if (g_telemetry_ready) {
        bool gpu_ok = Nvml_PollMetrics(&out_data->gpu_temp, &out_data->gpu_watts, &out_data->gpu_fan_rpm);
        out_data->is_gpu_available = gpu_ok ? 1 : 0;
    } else {
        out_data->gpu_temp = 0.0f;
        out_data->gpu_watts = 0.0f;
        out_data->gpu_fan_rpm = 0;
        out_data->is_gpu_available = 0;
    }

    return 0;
}

CP_API int CP_EnumerateUserlandProcesses(ProcessData* out_buffer, uint32_t max_count, uint32_t* out_count) {
    return EnumerateProcesses(out_buffer, max_count, out_count);
}

CP_API int CP_TerminateProcess(uint32_t pid) {
    return TerminateProcessByPid(pid);
}

CP_API int CP_CheckProcessResponsiveness(uint32_t pid) {
    return CheckWindowHung(pid);
}

CP_API void* CP_InitSharedTelemetryBuffer(const wchar_t* mapping_name, uint32_t buffer_size) {
    return SetupSharedTelemetryBuffer(mapping_name, buffer_size);
}
