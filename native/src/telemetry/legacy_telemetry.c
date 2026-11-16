#include "legacy_telemetry.h"
#include <windows.h>
#include <string.h>
#include "cpu_sensors.h"
#include "gpu_providers.h"
#include "../hardware/gpu_inventory.h"
#ifdef MACE_HAS_RYZEN_SDK
#include "amd_ryzen_provider.h"
#endif

static SRWLOCK cache_lock=SRWLOCK_INIT;
static HANDLE stop_event, worker;
static uint32_t cpu_vendor;
static bool inventory_ready;
static uint64_t cache_time;
static uint32_t adapter_count;
static MaceGpuAdapterInfoV2 adapters[16];
static MaceGpuTelemetryV2 gpu_cache[16];
static MaceCpuTelemetryV2 cpu_cache;

void LegacyTelemetry_GpuState(MaceGpuTelemetryV2* s, MaceMetricState state) {
    s->temperature_c=(MaceMetricF32V2){0,state};
    s->power_w=(MaceMetricF32V2){0,state};
    s->usage_percent=(MaceMetricF32V2){0,state};
    s->fan_rpm=(MaceMetricU32V2){0,state};
    s->fan_percent=(MaceMetricU32V2){0,state};
    s->video_memory_used_bytes=(MaceMetricU64V2){0,state,0};
    s->video_memory_total_bytes=(MaceMetricU64V2){0,state,0};
    s->power_scope=MACE_POWER_SCOPE_UNKNOWN;
}

static DWORD WINAPI sample_worker(void* unused) {
    (void)unused;
#ifdef MACE_HAS_RYZEN_SDK
    if (cpu_vendor==MACE_CPU_VENDOR_AMD) AmdRyzenProvider_Init();
#endif
    GpuProviders_Init();
    do {
        MaceCpuTelemetryV2 cpu={0};
        cpu.provider_id=MACE_PROVIDER_WINDOWS;
        cpu.temperature_c.state=cpu.power_w.state=MACE_METRIC_NO_PROVIDER;
#ifdef MACE_HAS_RYZEN_SDK
        if (cpu_vendor==MACE_CPU_VENDOR_AMD) AmdRyzenProvider_Poll(&cpu);
#endif
        MaceGpuAdapterInfoV2 inventory[16]={0};
        MaceGpuTelemetryV2 samples[16]={0};
        uint32_t count=0;
        bool ready=GpuInventory_Enumerate(inventory,16,&count)==MACE_OK && count<=16;
        if (!ready) count=0;
        for (uint32_t i=0;i<count;i++) GpuProviders_Poll(&inventory[i],&samples[i]);
        AcquireSRWLockExclusive(&cache_lock);
        cpu_cache=cpu; adapter_count=count; inventory_ready=ready;
        memcpy(adapters,inventory,sizeof(adapters)); memcpy(gpu_cache,samples,sizeof(gpu_cache));
        cache_time=GetTickCount64();
        ReleaseSRWLockExclusive(&cache_lock);
    } while (WaitForSingleObject(stop_event,1000)==WAIT_TIMEOUT);
    GpuProviders_Shutdown();
#ifdef MACE_HAS_RYZEN_SDK
    AmdRyzenProvider_Shutdown();
#endif
    CpuSensors_Shutdown(); /* runtime already closed to polling. */
    return 0;
}

bool LegacyTelemetry_Start(uint32_t vendor) {
    cpu_vendor=vendor; cache_time=0; inventory_ready=false; adapter_count=0;
    memset(&cpu_cache,0,sizeof(cpu_cache));
    stop_event=CreateEventW(NULL,TRUE,FALSE,NULL);
    if (!stop_event) return false;
    worker=CreateThread(NULL,0,sample_worker,NULL,0,NULL);
    if (!worker) { CloseHandle(stop_event); stop_event=NULL; return false; }
    return true;
}
void LegacyTelemetry_Stop(void) {
    if (stop_event) SetEvent(stop_event);
    if (worker) { WaitForSingleObject(worker,INFINITE); CloseHandle(worker); worker=NULL; }
    if (stop_event) { CloseHandle(stop_event); stop_event=NULL; }
}
void LegacyTelemetry_Cpu(MaceCpuTelemetryV2* s) {
    s->temperature_c=(MaceMetricF32V2){0,MACE_METRIC_WARMING_UP};
    s->power_w=s->temperature_c;
    if (!TryAcquireSRWLockShared(&cache_lock)) return;
    if (cache_time && GetTickCount64()-cache_time<=3000) {
        s->temperature_c=cpu_cache.temperature_c; s->power_w=cpu_cache.power_w;
        s->provider_id=cpu_cache.provider_id; s->power_scope=cpu_cache.power_scope;
    } else if (cache_time) s->temperature_c.state=s->power_w.state=MACE_METRIC_READ_FAILED;
    ReleaseSRWLockShared(&cache_lock);
}
void LegacyTelemetry_Gpu(const MaceGpuAdapterInfoV2* a, MaceGpuTelemetryV2* s) {
    LegacyTelemetry_GpuState(s,MACE_METRIC_WARMING_UP);
    if (!TryAcquireSRWLockShared(&cache_lock)) return;
    if (cache_time && GetTickCount64()-cache_time>3000) {
        LegacyTelemetry_GpuState(s,MACE_METRIC_READ_FAILED);
    } else if (cache_time) {
        LegacyTelemetry_GpuState(s,MACE_METRIC_NO_PROVIDER);
        for (uint32_t i=0;i<adapter_count;i++)
            if (adapters[i].luid.low_part==a->luid.low_part && adapters[i].luid.high_part==a->luid.high_part) {
                uint32_t size=s->struct_size,version=s->version;
                uint64_t time=s->timestamp_monotonic_ns;
                *s=gpu_cache[i]; s->struct_size=size; s->version=version;
                s->timestamp_monotonic_ns=time; s->luid=a->luid;
                break;
            }
    }
    ReleaseSRWLockShared(&cache_lock);
}
uint32_t LegacyTelemetry_PciMatches(const MaceGpuAdapterInfoV2* a) {
    uint32_t count=0;
    if (!TryAcquireSRWLockShared(&cache_lock)) return UINT32_MAX;
    if (!inventory_ready || GetTickCount64()-cache_time>3000) count=UINT32_MAX;
    else for (uint32_t i=0;i<adapter_count;i++)
        if (adapters[i].vendor_id==a->vendor_id && adapters[i].device_id==a->device_id &&
            adapters[i].subsystem_id==a->subsystem_id && adapters[i].revision==a->revision) count++;
    ReleaseSRWLockShared(&cache_lock);
    return count;
}
