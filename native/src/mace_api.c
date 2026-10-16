#ifndef MACE_EXPORTS
#define MACE_EXPORTS
#endif
#include <windows.h>
#include <intrin.h>
#include <string.h>
#include "mace_api.h"
#include "hardware/gpu_inventory.h"
#include "telemetry/gpu_providers.h"
#include "telemetry/cpu_sensors.h"
#include "process/process_internal.h"
#include "internal/mace_runtime.h"
#include "extensions/hang_detector.h"
#include "extensions/shared_buffer.h"
#ifdef MACE_HAS_RYZEN_SDK
#include "telemetry/amd_ryzen_provider.h"
#endif

static uint64_t monotonic_ns(void) { return GetTickCount64() * UINT64_C(1000000); }

static uint32_t cpu_vendor(void) {
    int regs[4] = {0};
    char vendor[13] = {0};
    __cpuid(regs, 0);
    memcpy(vendor, &regs[1], 4);
    memcpy(vendor + 4, &regs[3], 4);
    memcpy(vendor + 8, &regs[2], 4);
    if (strcmp(vendor, "GenuineIntel") == 0) return MACE_CPU_VENDOR_INTEL;
    if (strcmp(vendor, "AuthenticAMD") == 0) return MACE_CPU_VENDOR_AMD;
    return MACE_CPU_VENDOR_UNKNOWN;
}

CP_API int CP_InitializeTelemetry(void) {
    return MaceRuntime_Initialize(cpu_vendor());
}

CP_API void CP_ShutdownTelemetry(void) {
    MaceRuntime_Shutdown();
}

CP_API MaceStatus CP_EnumerateGpuAdapters(MaceGpuAdapterInfoV2* adapters,
                                           uint32_t capacity, uint32_t* count) {
    return GpuInventory_Enumerate(adapters, capacity, count);
}

static MaceStatus poll_cpu_locked(MaceCpuTelemetryV2* out) {
    MaceCpuTelemetryV2 sample = {0};
    sample.struct_size = sizeof(sample);
    sample.version = MACE_V2_CONTRACT_VERSION;
    sample.timestamp_monotonic_ns = monotonic_ns();
    sample.cpu_vendor = cpu_vendor();
    sample.provider_id = MACE_PROVIDER_WINDOWS;
    sample.temperature_c.state = MACE_METRIC_NO_PROVIDER;
    sample.power_w.state = MACE_METRIC_NO_PROVIDER;
    float temp = 0, power = 0, usage = 0;
    CpuSensors_Poll(&temp, &power, &usage);
    sample.usage_percent.value = usage;
    sample.usage_percent.state = CpuSensors_UsageReady() ? MACE_METRIC_VALID : MACE_METRIC_WARMING_UP;
    if (sample.cpu_vendor == MACE_CPU_VENDOR_INTEL && CpuSensors_HardwareAvailable()) {
        sample.provider_id = MACE_PROVIDER_INTEL_MSR;
        sample.power_scope = MACE_POWER_SCOPE_CPU_PACKAGE;
        sample.temperature_c.state = temp > 0 ? MACE_METRIC_VALID : MACE_METRIC_READ_FAILED;
        sample.temperature_c.value = temp;
        sample.power_w.state = CpuSensors_PowerReady() ? MACE_METRIC_VALID :
            (CpuSensors_PowerSupported() ? MACE_METRIC_WARMING_UP : MACE_METRIC_READ_FAILED);
        sample.power_w.value = power;
    }
#ifdef MACE_HAS_RYZEN_SDK
    if (sample.cpu_vendor == MACE_CPU_VENDOR_AMD)
        AmdRyzenProvider_Poll(&sample);
#endif
    memcpy(out, &sample, sizeof(sample));
    return MACE_OK;
}

CP_API MaceStatus CP_PollCpuTelemetryV2(MaceCpuTelemetryV2* out,
                                         uint32_t capacity) {
    if (capacity < sizeof(*out)) return MACE_E_BUFFER_TOO_SMALL;
    if (!out) return MACE_E_INVALID_ARGUMENT;
    MaceStatus status = MaceRuntime_BeginPoll();
    if (status != MACE_OK) return status;
    status = poll_cpu_locked(out);
    MaceRuntime_EndPoll();
    return status;
}

static MaceStatus poll_gpu_locked(const MaceAdapterLuidV2* luid,
                                  MaceGpuTelemetryV2* out) {
    MaceGpuAdapterInfoV2 adapter;
    MaceStatus status = GpuInventory_Find(luid, &adapter);
    if (status != MACE_OK) return status;
    MaceGpuTelemetryV2 sample = {0};
    sample.struct_size = sizeof(sample);
    sample.version = MACE_V2_CONTRACT_VERSION;
    sample.timestamp_monotonic_ns = monotonic_ns();
    sample.luid = *luid;
    GpuProviders_Poll(&adapter, &sample);
    memcpy(out, &sample, sizeof(sample));
    return MACE_OK;
}

CP_API MaceStatus CP_PollGpuTelemetryV2(const MaceAdapterLuidV2* luid,
                                         MaceGpuTelemetryV2* out,
                                         uint32_t capacity) {
    if (!luid) return MACE_E_INVALID_ARGUMENT;
    if (capacity < sizeof(*out)) return MACE_E_BUFFER_TOO_SMALL;
    if (!out) return MACE_E_INVALID_ARGUMENT;
    MaceStatus status = MaceRuntime_BeginPoll();
    if (status != MACE_OK) return status;
    status = poll_gpu_locked(luid, out);
    MaceRuntime_EndPoll();
    return status;
}

CP_API MaceStatus CP_PollSystemMemoryV2(MaceSystemMemoryV2* out,
                                        uint32_t capacity) {
    if (capacity < sizeof(*out)) return MACE_E_BUFFER_TOO_SMALL;
    if (!out) return MACE_E_INVALID_ARGUMENT;
    MEMORYSTATUSEX memory = {0};
    memory.dwLength = sizeof(memory);
    if (!GlobalMemoryStatusEx(&memory)) return MACE_E_WINDOWS_ERROR;
    MaceSystemMemoryV2 sample = {0};
    sample.struct_size = sizeof(sample);
    sample.version = MACE_V2_CONTRACT_VERSION;
    sample.timestamp_monotonic_ns = monotonic_ns();
    sample.total_bytes.value = memory.ullTotalPhys;
    sample.total_bytes.state = MACE_METRIC_VALID;
    sample.available_bytes.value = memory.ullAvailPhys;
    sample.available_bytes.state = MACE_METRIC_VALID;
    memcpy(out, &sample, sizeof(sample));
    return MACE_OK;
}

CP_API int CP_PollTelemetry(TelemetryData* out) {
    if (!out) return -1;
    memset(out, 0, sizeof(*out));
    if (MaceRuntime_BeginPoll() != MACE_OK) return 0;
    MaceCpuTelemetryV2 cpu;
    if (poll_cpu_locked(&cpu) == MACE_OK) {
        out->cpu_usage = cpu.usage_percent.state == MACE_METRIC_VALID ? cpu.usage_percent.value : 0;
        out->cpu_temp = cpu.temperature_c.state == MACE_METRIC_VALID ? cpu.temperature_c.value : 0;
        out->cpu_watts = cpu.power_w.state == MACE_METRIC_VALID ? cpu.power_w.value : 0;
        out->is_cpu_sensor_available = cpu.temperature_c.state == MACE_METRIC_VALID ||
                                       cpu.power_w.state == MACE_METRIC_VALID;
    }
    uint32_t count = 0;
    if (CP_EnumerateGpuAdapters(NULL, 0, &count) == MACE_E_BUFFER_TOO_SMALL && count) {
        /* La API heredada tiene un solo espacio GPU: seleccionar la primera GPU
         * que realmente entregue temperatura. V2 conserva todas las GPU. */
        MaceGpuAdapterInfoV2* adapters = HeapAlloc(GetProcessHeap(), 0, count * sizeof(*adapters));
        if (adapters && CP_EnumerateGpuAdapters(adapters, count, &count) == MACE_OK) {
            for (uint32_t i = 0; i < count; ++i) {
                MaceGpuTelemetryV2 gpu;
                if (poll_gpu_locked(&adapters[i].luid, &gpu) != MACE_OK ||
                    gpu.temperature_c.state != MACE_METRIC_VALID) continue;
                out->gpu_temp = gpu.temperature_c.value;
                out->gpu_watts = gpu.power_w.state == MACE_METRIC_VALID ? gpu.power_w.value : 0;
                out->gpu_fan_rpm = gpu.fan_rpm.state == MACE_METRIC_VALID ? gpu.fan_rpm.value : 0;
                out->is_gpu_available = 1;
                break;
            }
        }
        if (adapters) HeapFree(GetProcessHeap(), 0, adapters);
    }
    MaceRuntime_EndPoll();
    return 0;
}

CP_API int CP_EnumerateUserlandProcesses(ProcessData* buffer, uint32_t max_count, uint32_t* count) {
    return EnumerateProcesses(buffer, max_count, count);
}
CP_API int CP_TerminateProcess(uint32_t pid) { return TerminateProcessByPid(pid); }
CP_API int CP_CheckProcessResponsiveness(uint32_t pid) { return CheckWindowHung(pid); }
CP_API void* CP_InitSharedTelemetryBuffer(const wchar_t* name, uint32_t size) {
    MaceRuntime_BeginResourceCreation();
    void* view = SetupSharedTelemetryBuffer(name, size);
    MaceRuntime_EndResourceCreation();
    return view;
}
