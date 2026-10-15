#include <windows.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <shlobj.h>
#include "mace_types_v2.h"
#include "../hardware/gpu_inventory.h"

typedef int NvmlResult;
typedef void* NvmlDevice;
enum { NVML_OK = 0, NVML_NOT_SUPPORTED = 3, NVML_NO_PERMISSION = 4,
       NVML_NOT_FOUND = 6, NVML_GPU_LOST = 15,
       NVML_GPU_TEMPERATURE = 0, NVML_FAN_SPEED_RPM = 1 };
typedef struct {
    char bus_id_legacy[16];
    unsigned int domain, bus, device, pci_device_id, subsystem_id;
    char bus_id[32];
} NvmlPciInfo;
typedef struct { unsigned long long total, free, used; } NvmlMemoryInfo;
typedef struct { unsigned int gpu, memory; } NvmlUtilization;
typedef struct { unsigned int version, fan, speed; } NvmlFanInfo;

static HMODULE module;
static bool ready;
static NvmlResult (*init_fn)(void);
static NvmlResult (*shutdown_fn)(void);
static NvmlResult (*count_fn)(unsigned int*);
static NvmlResult (*handle_fn)(unsigned int, NvmlDevice*);
static NvmlResult (*pci_fn)(NvmlDevice, NvmlPciInfo*);
static NvmlResult (*temperature_fn)(NvmlDevice, int, unsigned int*);
static NvmlResult (*power_fn)(NvmlDevice, unsigned int*);
static NvmlResult (*utilization_fn)(NvmlDevice, NvmlUtilization*);
static NvmlResult (*memory_fn)(NvmlDevice, NvmlMemoryInfo*);
static NvmlResult (*fan_percent_fn)(NvmlDevice, unsigned int*);
static NvmlResult (*fan_rpm_fn)(NvmlDevice, NvmlFanInfo*);

static MaceMetricState result_state(NvmlResult result) {
    if (result == NVML_OK) return MACE_METRIC_VALID;
    if (result == NVML_NOT_SUPPORTED) return MACE_METRIC_UNSUPPORTED;
    if (result == NVML_NO_PERMISSION) return MACE_METRIC_ACCESS_DENIED;
    if (result == NVML_NOT_FOUND) return MACE_METRIC_NO_DEVICE;
    if (result == NVML_GPU_LOST) return MACE_METRIC_DEVICE_INACTIVE;
    return MACE_METRIC_READ_FAILED;
}

bool Nvml_Init(void) {
    if (ready) return true;
    /* SearchSystem32 evita cargar una DLL no confiable desde el directorio actual. */
    module = LoadLibraryExW(L"nvml.dll", NULL, LOAD_LIBRARY_SEARCH_SYSTEM32);
    if (!module) {
        PWSTR program_files = NULL;
        if (SUCCEEDED(SHGetKnownFolderPath(&FOLDERID_ProgramFilesX64, 0, NULL,
                                           &program_files)) && program_files) {
            wchar_t path[MAX_PATH];
            if (swprintf_s(path, _countof(path), L"%s\\NVIDIA Corporation\\NVSMI\\nvml.dll",
                           program_files) > 0)
                module = LoadLibraryExW(path, NULL,
                    LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_SYSTEM32);
            CoTaskMemFree(program_files);
        }
    }
    if (!module) return false;
#define RESOLVE(dst, name) do { FARPROC symbol = GetProcAddress(module, name); \
    memcpy(&(dst), &symbol, sizeof(symbol)); } while (0)
    RESOLVE(init_fn, "nvmlInit_v2");
    RESOLVE(shutdown_fn, "nvmlShutdown");
    RESOLVE(count_fn, "nvmlDeviceGetCount_v2");
    RESOLVE(handle_fn, "nvmlDeviceGetHandleByIndex_v2");
    RESOLVE(pci_fn, "nvmlDeviceGetPciInfo_v3");
    RESOLVE(temperature_fn, "nvmlDeviceGetTemperature");
    RESOLVE(power_fn, "nvmlDeviceGetPowerUsage");
    RESOLVE(utilization_fn, "nvmlDeviceGetUtilizationRates");
    RESOLVE(memory_fn, "nvmlDeviceGetMemoryInfo");
    RESOLVE(fan_percent_fn, "nvmlDeviceGetFanSpeed");
    RESOLVE(fan_rpm_fn, "nvmlDeviceGetFanSpeedRPM");
#undef RESOLVE
    if (!init_fn || !shutdown_fn || !count_fn || !handle_fn || !pci_fn ||
        init_fn() != NVML_OK) {
        FreeLibrary(module);
        module = NULL;
        return false;
    }
    ready = true;
    return true;
}

void Nvml_Shutdown(void) {
    if (ready) shutdown_fn();
    if (module) FreeLibrary(module);
    module = NULL;
    ready = false;
}

bool Nvml_PollAdapter(const MaceGpuAdapterInfoV2* adapter,
                      MaceGpuTelemetryV2* out) {
    if (!ready || adapter->vendor_id != 0x10DEu) return false;
    uint32_t bus = 0, device = 0, function = 0;
    if (!GpuInventory_GetPciAddress(&adapter->luid, &bus, &device, &function))
        return false;
    unsigned int count = 0;
    if (count_fn(&count) != NVML_OK) return false;
    NvmlDevice matched = NULL;
    for (unsigned int i = 0; i < count; ++i) {
        NvmlDevice candidate = NULL;
        NvmlPciInfo pci = {0};
        if (handle_fn(i, &candidate) != NVML_OK ||
            pci_fn(candidate, &pci) != NVML_OK) continue;
        unsigned int fn = 0, parsed_bus = 0, parsed_dev = 0, domain = 0;
        int parsed = sscanf_s(pci.bus_id, "%x:%x:%x.%x",
                              &domain, &parsed_bus, &parsed_dev, &fn);
        if (pci.bus == bus && pci.device == device &&
            parsed == 4 && fn == function) {
            matched = candidate;
            break;
        }
    }
    if (!matched) return false;
    out->provider_id = MACE_PROVIDER_NVIDIA_NVML;
    out->power_scope = MACE_POWER_SCOPE_GPU_BOARD;
    out->temperature_c.state = MACE_METRIC_UNSUPPORTED;
    out->power_w.state = MACE_METRIC_UNSUPPORTED;
    out->usage_percent.state = MACE_METRIC_UNSUPPORTED;
    out->fan_rpm.state = MACE_METRIC_UNSUPPORTED;
    out->fan_percent.state = MACE_METRIC_UNSUPPORTED;
    out->video_memory_used_bytes.state = MACE_METRIC_UNSUPPORTED;
    out->video_memory_total_bytes.state = MACE_METRIC_UNSUPPORTED;
    unsigned int value = 0;
    NvmlResult result;
    if (temperature_fn) {
        result = temperature_fn(matched, NVML_GPU_TEMPERATURE, &value);
        out->temperature_c.state = result_state(result);
        if (result == NVML_OK) out->temperature_c.value = (float)value;
    }
    if (power_fn) {
        result = power_fn(matched, &value);
        out->power_w.state = result_state(result);
        if (result == NVML_OK) out->power_w.value = (float)value / 1000.0f;
    }
    if (utilization_fn) {
        NvmlUtilization util = {0};
        result = utilization_fn(matched, &util);
        out->usage_percent.state = result_state(result);
        if (result == NVML_OK) out->usage_percent.value = (float)util.gpu;
    }
    if (fan_percent_fn) {
        result = fan_percent_fn(matched, &value);
        out->fan_percent.state = result_state(result);
        if (result == NVML_OK) out->fan_percent.value = value;
    }
    if (fan_rpm_fn) {
        NvmlFanInfo fan = {sizeof(fan) | (1u << 24), 0, 0};
        result = fan_rpm_fn(matched, &fan);
        out->fan_rpm.state = result_state(result);
        if (result == NVML_OK) out->fan_rpm.value = fan.speed;
    }
    if (memory_fn) {
        NvmlMemoryInfo memory = {0};
        result = memory_fn(matched, &memory);
        out->video_memory_used_bytes.state = result_state(result);
        out->video_memory_total_bytes.state = result_state(result);
        if (result == NVML_OK) {
            out->video_memory_used_bytes.value = memory.used;
            out->video_memory_total_bytes.value = memory.total;
        }
    }
    return true;
}
