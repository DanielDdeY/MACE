/* Only linked into mace_native_lhm_test.dll: no vendor SDK or sensor hardware. */
#include <windows.h>
#include <string.h>
#include "../src/telemetry/gpu_providers.h"
#include "../src/hardware/gpu_inventory.h"
static void adapter(uint32_t i,MaceGpuAdapterInfoV2* a) {
    memset(a,0,sizeof(*a)); a->struct_size=sizeof(*a); a->version=2;
    a->luid.low_part=i+1; a->adapter_index=i;
    a->vendor_id=i==1 ? 0x10DE : i==2 ? 0x8086 : 0x1002;
    a->device_id=i==1 ? 0x2222 : i==2 ? 0x3333 : 0x1111;
    a->subsystem_id=0x12345678; a->revision=1;
}
MaceStatus GpuInventory_Enumerate(MaceGpuAdapterInfoV2* out,uint32_t capacity,uint32_t* count) {
    if (!count) return MACE_E_INVALID_ARGUMENT;
    wchar_t flag[2]; *count=GetEnvironmentVariableW(L"MACE_SIM_DUPLICATE_PCI",flag,2) ? 4 : 3;
    if (capacity<*count) return MACE_E_BUFFER_TOO_SMALL;
    if (!out) return MACE_E_INVALID_ARGUMENT;
    for (uint32_t i=0;i<*count;i++) adapter(i,&out[i]);
    return MACE_OK;
}
MaceStatus GpuInventory_Find(const MaceAdapterLuidV2* luid,MaceGpuAdapterInfoV2* out) {
    if (!luid || !out) return MACE_E_INVALID_ARGUMENT;
    if (luid->high_part || luid->low_part<1 || luid->low_part>4) return MACE_E_DEVICE_NOT_FOUND;
    adapter(luid->low_part-1,out); return MACE_OK;
}
bool GpuInventory_GetPciAddress(const MaceAdapterLuidV2* luid,uint32_t* bus,uint32_t* dev,uint32_t* fn) {
    (void)luid;(void)bus;(void)dev;(void)fn; return false;
}
void GpuProviders_Init(void) {}
void GpuProviders_Shutdown(void) {
    wchar_t flag[2];
    if (GetEnvironmentVariableW(L"MACE_SIM_SLOW_STOP",flag,2)) Sleep(1000);
}
void GpuProviders_Poll(const MaceGpuAdapterInfoV2* a,MaceGpuTelemetryV2* s) {
    s->temperature_c.state=s->power_w.state=s->usage_percent.state=MACE_METRIC_NO_PROVIDER;
    s->fan_rpm.state=s->fan_percent.state=MACE_METRIC_NO_PROVIDER;
    s->video_memory_used_bytes.state=s->video_memory_total_bytes.state=MACE_METRIC_NO_PROVIDER;
    s->provider_id=MACE_PROVIDER_WINDOWS;
    s->video_memory_total_bytes=(MaceMetricU64V2){4294967296ULL,MACE_METRIC_VALID,0};
    wchar_t fallback[2];
    if (GetEnvironmentVariableW(L"MACE_SIM_FORCE_FALLBACK",fallback,2)) return;
    if (a->vendor_id==0x10DE || a->vendor_id==0x8086) {
        s->provider_id=a->vendor_id==0x10DE ? MACE_PROVIDER_NVIDIA_NVML : MACE_PROVIDER_INTEL_IGCL;
        s->temperature_c=(MaceMetricF32V2){a->vendor_id==0x10DE ? 42.0f : 43.0f,MACE_METRIC_VALID};
    }
}
