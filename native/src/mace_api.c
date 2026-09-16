#include "mace_api.h"

// Prototipos internos de módulos
extern bool Nvml_Init(void);
extern void Nvml_Shutdown(void);
extern bool Nvml_PollMetrics(float* out_temp, float* out_watts, uint32_t* out_fan_rpm);
extern void CpuSensors_Poll(float* out_temp, float* out_watts);
extern int  EnumerateProcesses(ProcessData* out_buffer, uint32_t max_count, uint32_t* out_count);
extern int  TerminateProcessByPid(uint32_t pid);
extern int  CheckWindowHung(uint32_t pid);
extern void* SetupSharedTelemetryBuffer(const wchar_t* mapping_name, uint32_t buffer_size);

static bool g_telemetry_ready = false;

CP_API int CP_InitializeTelemetry(void) {
    g_telemetry_ready = Nvml_Init();
    return 0;
}

CP_API void CP_ShutdownTelemetry(void) {
    Nvml_Shutdown();
    g_telemetry_ready = false;
}

CP_API int CP_PollTelemetry(TelemetryData* out_data) {
    if (!out_data) return -1;

    CpuSensors_Poll(&out_data->cpu_temp, &out_data->cpu_watts);

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