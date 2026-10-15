#include <windows.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include "mace_types_v2.h"
#include "SDK/Include/ADLX.h"
#include "SDK/Include/ISystem2.h"
#include "SDK/Include/IPerformanceMonitoring.h"

/* ADLX SDK headers are supplied externally at build time; they are not
 * redistributable as source under AMD's SDK agreement. */
static HMODULE module;
static IADLXSystem* system_api;
static IADLXPerformanceMonitoringServices* performance;
static ADLXTerminate_Fn terminate_fn;

bool Adlx_Init(void) {
    if (performance) return true;
    module = LoadLibraryExW(L"amdadlx64.dll", NULL, LOAD_LIBRARY_SEARCH_SYSTEM32);
    if (!module) return false;
    ADLXInitialize_Fn initialize = NULL;
    FARPROC symbol = GetProcAddress(module, "ADLXInitialize");
    memcpy(&initialize, &symbol, sizeof(symbol));
    symbol = GetProcAddress(module, "ADLXTerminate");
    memcpy(&terminate_fn, &symbol, sizeof(symbol));
    if (!initialize || !terminate_fn ||
        ADLX_FAILED(initialize(ADLX_FULL_VERSION, &system_api)) || !system_api)
        goto failed;
    if (ADLX_FAILED(system_api->pVtbl->GetPerformanceMonitoringServices(
            system_api, &performance)) || !performance)
        goto failed;
    return true;
failed:
    if (performance) performance->pVtbl->Release(performance);
    if (system_api) terminate_fn();
    if (module) FreeLibrary(module);
    performance = NULL;
    system_api = NULL;
    module = NULL;
    return false;
}

void Adlx_Shutdown(void) {
    if (performance) performance->pVtbl->Release(performance);
    if (system_api && terminate_fn) terminate_fn();
    if (module) FreeLibrary(module);
    performance = NULL;
    system_api = NULL;
    module = NULL;
}

static IADLXGPU* find_gpu(const MaceAdapterLuidV2* wanted) {
    IADLXGPUList* list = NULL;
    if (ADLX_FAILED(system_api->pVtbl->GetGPUs(system_api, &list)) || !list)
        return NULL;
    IADLXGPU* matched = NULL;
    for (adlx_uint i = list->pVtbl->Begin(list); i < list->pVtbl->End(list); ++i) {
        IADLXGPU* gpu = NULL;
        if (ADLX_FAILED(list->pVtbl->At_GPUList(list, i, &gpu)) || !gpu)
            continue;
        IADLXGPU2* gpu2 = NULL;
        if (ADLX_SUCCEEDED(gpu->pVtbl->QueryInterface(gpu, L"IADLXGPU2", (void**)&gpu2))
            && gpu2) {
            ADLX_LUID luid = {0};
            if (ADLX_SUCCEEDED(gpu2->pVtbl->LUID(gpu2, &luid)) &&
                luid.lowPart == wanted->low_part && luid.highPart == wanted->high_part)
                matched = gpu;
            gpu2->pVtbl->Release(gpu2);
        }
        if (matched) break;
        gpu->pVtbl->Release(gpu);
    }
    list->pVtbl->Release(list);
    return matched;
}

#define READ_DOUBLE(support_method, read_method, field) do { \
    adlx_bool supported = false; \
    if (ADLX_SUCCEEDED(support->pVtbl->support_method(support, &supported)) && supported) { \
        adlx_double value = 0; \
        ADLX_RESULT result = metrics->pVtbl->read_method(metrics, &value); \
        out->field.state = ADLX_SUCCEEDED(result) ? MACE_METRIC_VALID : MACE_METRIC_READ_FAILED; \
        if (ADLX_SUCCEEDED(result)) out->field.value = (float)value; \
    } else out->field.state = MACE_METRIC_UNSUPPORTED; \
} while (0)

bool Adlx_PollAdapter(const MaceGpuAdapterInfoV2* adapter,
                      MaceGpuTelemetryV2* out) {
    if (!performance || adapter->vendor_id != 0x1002u) return false;
    IADLXGPU* gpu = find_gpu(&adapter->luid);
    if (!gpu) return false;
    IADLXGPUMetricsSupport* support = NULL;
    IADLXGPUMetrics* metrics = NULL;
    ADLX_RESULT sr = performance->pVtbl->GetSupportedGPUMetrics(performance, gpu, &support);
    ADLX_RESULT mr = performance->pVtbl->GetCurrentGPUMetrics(performance, gpu, &metrics);
    gpu->pVtbl->Release(gpu);
    if (ADLX_FAILED(sr) || !support || ADLX_FAILED(mr) || !metrics) {
        if (support) support->pVtbl->Release(support);
        if (metrics) metrics->pVtbl->Release(metrics);
        return false;
    }
    out->provider_id = MACE_PROVIDER_AMD_ADLX;
    out->power_scope = MACE_POWER_SCOPE_GPU_CHIP;
    READ_DOUBLE(IsSupportedGPUTemperature, GPUTemperature, temperature_c);
    READ_DOUBLE(IsSupportedGPUPower, GPUPower, power_w);
    READ_DOUBLE(IsSupportedGPUUsage, GPUUsage, usage_percent);
    adlx_bool supported = false;
    if (ADLX_SUCCEEDED(support->pVtbl->IsSupportedGPUFanSpeed(support, &supported)) && supported) {
        adlx_int rpm = 0;
        ADLX_RESULT result = metrics->pVtbl->GPUFanSpeed(metrics, &rpm);
        out->fan_rpm.state = ADLX_SUCCEEDED(result) ? MACE_METRIC_VALID : MACE_METRIC_READ_FAILED;
        if (ADLX_SUCCEEDED(result) && rpm >= 0) out->fan_rpm.value = (uint32_t)rpm;
    } else out->fan_rpm.state = MACE_METRIC_UNSUPPORTED;
    supported = false;
    if (ADLX_SUCCEEDED(support->pVtbl->IsSupportedGPUVRAM(support, &supported)) && supported) {
        adlx_int used_mb = 0;
        ADLX_RESULT result = metrics->pVtbl->GPUVRAM(metrics, &used_mb);
        out->video_memory_used_bytes.state = ADLX_SUCCEEDED(result) && used_mb >= 0 ?
            MACE_METRIC_VALID : MACE_METRIC_READ_FAILED;
        if (out->video_memory_used_bytes.state == MACE_METRIC_VALID)
            out->video_memory_used_bytes.value = (uint64_t)used_mb * UINT64_C(1000000);
    } else out->video_memory_used_bytes.state = MACE_METRIC_UNSUPPORTED;
    support->pVtbl->Release(support);
    metrics->pVtbl->Release(metrics);
    return true;
}
