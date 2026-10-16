#define COBJMACROS
#include <windows.h>
#include <dxgi1_4.h>
#include <stdbool.h>
#include "gpu_providers.h"

bool Nvml_Init(void);
void Nvml_Shutdown(void);
bool Nvml_PollAdapter(const MaceGpuAdapterInfoV2*, MaceGpuTelemetryV2*);
bool Igcl_Init(void);
void Igcl_Shutdown(void);
bool Igcl_PollAdapter(const MaceGpuAdapterInfoV2*, MaceGpuTelemetryV2*);
#ifdef MACE_HAS_ADLX
bool Adlx_Init(void);
void Adlx_Shutdown(void);
bool Adlx_PollAdapter(const MaceGpuAdapterInfoV2*, MaceGpuTelemetryV2*);
#endif

static bool nvml_ready, igcl_ready;
#ifdef MACE_HAS_ADLX
static bool adlx_ready;
#endif

void GpuProviders_Init(void) {
    nvml_ready = Nvml_Init();
    igcl_ready = Igcl_Init();
#ifdef MACE_HAS_ADLX
    adlx_ready = Adlx_Init();
#endif
}

void GpuProviders_Shutdown(void) {
    Nvml_Shutdown();
    Igcl_Shutdown();
#ifdef MACE_HAS_ADLX
    Adlx_Shutdown();
    adlx_ready = false;
#endif
    nvml_ready = false;
    igcl_ready = false;
}

static void windows_memory_capacity(const MaceGpuAdapterInfoV2* adapter,
                           MaceGpuTelemetryV2* out) {
    IDXGIFactory1* factory = NULL;
    if (FAILED(CreateDXGIFactory1(&IID_IDXGIFactory1, (void**)&factory))) return;
    IDXGIAdapter1* base = NULL;
    if (SUCCEEDED(IDXGIFactory1_EnumAdapters1(factory, adapter->adapter_index, &base))) {
        DXGI_ADAPTER_DESC1 desc = {0};
        if (SUCCEEDED(IDXGIAdapter1_GetDesc1(base, &desc))) {
            uint64_t total = desc.DedicatedVideoMemory != 0 ?
                desc.DedicatedVideoMemory : desc.SharedSystemMemory;
            if (total != 0 && out->video_memory_total_bytes.state != MACE_METRIC_VALID) {
                out->video_memory_total_bytes.value = total;
                out->video_memory_total_bytes.state = MACE_METRIC_VALID;
                if (out->provider_id == MACE_PROVIDER_NONE)
                    out->provider_id = MACE_PROVIDER_WINDOWS;
            }
        }
        IDXGIAdapter1_Release(base);
    }
    IDXGIFactory1_Release(factory);
}

void GpuProviders_Poll(const MaceGpuAdapterInfoV2* adapter,
                       MaceGpuTelemetryV2* out) {
    out->temperature_c.state = MACE_METRIC_NO_PROVIDER;
    out->power_w.state = MACE_METRIC_NO_PROVIDER;
    out->usage_percent.state = MACE_METRIC_NO_PROVIDER;
    out->fan_rpm.state = MACE_METRIC_NO_PROVIDER;
    out->fan_percent.state = MACE_METRIC_NO_PROVIDER;
    out->video_memory_used_bytes.state = MACE_METRIC_NO_PROVIDER;
    out->video_memory_total_bytes.state = MACE_METRIC_NO_PROVIDER;
    if (adapter->vendor_id == 0x10DEu && nvml_ready)
        Nvml_PollAdapter(adapter, out);
    else if (adapter->vendor_id == 0x8086u && igcl_ready)
        Igcl_PollAdapter(adapter, out);
#ifdef MACE_HAS_ADLX
    else if (adapter->vendor_id == 0x1002u && adlx_ready)
        Adlx_PollAdapter(adapter, out);
#endif
    windows_memory_capacity(adapter, out);
}
