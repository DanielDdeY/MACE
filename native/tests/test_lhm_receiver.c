#include "mace_api.h"
#include <windows.h>
#include <stdio.h>
#include <stdbool.h>
#include <string.h>
#define CHECK(x) do { if (!(x)) { fprintf(stderr,"line %d: %s\n",__LINE__,#x); CP_ShutdownTelemetry(); return 1; } } while(0)
static uint64_t max_poll;
static DWORD WINAPI shutdown_worker(void* unused) { (void)unused; CP_ShutdownTelemetry(); return 0; }
static bool poll(MaceCpuTelemetryV2* cpu,MaceGpuTelemetryV2* gpu) {
    MaceAdapterLuidV2 luid={1,0}; ULONGLONG before=GetTickCount64();
    if (CP_PollCpuTelemetryV2(cpu,sizeof(*cpu))!=MACE_OK || CP_PollGpuTelemetryV2(&luid,gpu,sizeof(*gpu))!=MACE_OK) return false;
    uint64_t elapsed=GetTickCount64()-before; if (elapsed>max_poll) max_poll=elapsed;
    return true;
}
static bool wait_cpu(MaceMetricState state,DWORD timeout,MaceCpuTelemetryV2* cpu,MaceGpuTelemetryV2* gpu) {
    ULONGLONG until=GetTickCount64()+timeout;
    do { if (!poll(cpu,gpu)) return false; if (cpu->temperature_c.state==state) return true; Sleep(10); } while (GetTickCount64()<until);
    fprintf(stderr,"expected CPU state %u, got %u\n",state,cpu->temperature_c.state); return false;
}
static bool wait_gpu(MaceMetricState state,DWORD timeout,MaceCpuTelemetryV2* cpu,MaceGpuTelemetryV2* gpu) {
    ULONGLONG until=GetTickCount64()+timeout;
    do { if (!poll(cpu,gpu)) return false; if (gpu->temperature_c.state==state && gpu->provider_id==MACE_PROVIDER_LHM) return true; Sleep(10); } while (GetTickCount64()<until);
    return false;
}
int wmain(int argc,wchar_t** argv) {
    CHECK(argc==3);
    CHECK(SetEnvironmentVariableW(L"MACE_LHM_HELPER",argv[1]));
    CHECK(SetEnvironmentVariableW(L"MACE_SIM_SCENARIO",argv[2]));
    CHECK(SetEnvironmentVariableW(L"MACE_LHM_HELPER_SHA256",wcscmp(argv[2],L"hash-mismatch")==0 ?
        L"0000000000000000000000000000000000000000000000000000000000000000" : NULL));
    bool duplicate=wcscmp(argv[2],L"duplicate-pci")==0;
    bool fallback=wcscmp(argv[2],L"fallback")==0;
    SetEnvironmentVariableW(L"MACE_SIM_DUPLICATE_PCI",duplicate ? L"1" : NULL);
    SetEnvironmentVariableW(L"MACE_SIM_FORCE_FALLBACK",fallback ? L"1" : NULL);
    SetEnvironmentVariableW(L"MACE_SIM_SLOW_STOP",wcscmp(argv[2],L"init-race")==0 ? L"1" : NULL);
    max_poll=0;
    ULONGLONG before=GetTickCount64(); CHECK(CP_InitializeTelemetry()==0);
    uint64_t init_ms=GetTickCount64()-before;
    CHECK(init_ms<200);
    MaceCpuTelemetryV2 cpu={0}; MaceGpuTelemetryV2 gpu={0};
    CHECK(poll(&cpu,&gpu));
    if (wcscmp(argv[2],L"slow")==0) CHECK(cpu.temperature_c.state==MACE_METRIC_WARMING_UP);
    bool failed=wcscmp(argv[2],L"bad-pid")==0 || wcscmp(argv[2],L"malformed")==0 ||
        wcscmp(argv[2],L"wrong-client")==0 || wcscmp(argv[2],L"stale-arrival")==0 || wcscmp(argv[2],L"nan")==0 ||
        wcscmp(argv[2],L"bad-server-pid")==0 || wcscmp(argv[2],L"hash-mismatch")==0;
    bool unprivileged=wcscmp(argv[2],L"no-elevation")==0 || wcscmp(argv[2],L"driver-missing")==0 || wcscmp(argv[2],L"zero-unprivileged")==0;
    MaceMetricState expected=failed || wcscmp(argv[2],L"zero-cpu")==0 ? MACE_METRIC_READ_FAILED :
        unprivileged ? MACE_METRIC_NO_PROVIDER : MACE_METRIC_VALID;
    CHECK(wait_cpu(expected,7000,&cpu,&gpu));
    CHECK(cpu.power_w.state==expected);
    if (expected!=MACE_METRIC_VALID) CHECK(cpu.temperature_c.value==0 && cpu.power_w.value==0);
    if (failed) { CHECK(gpu.temperature_c.state==MACE_METRIC_READ_FAILED); }
    else {
        MaceMetricState gpu_expected=duplicate || wcscmp(argv[2],L"unknown-gpu")==0 ? MACE_METRIC_UNSUPPORTED : MACE_METRIC_VALID;
        CHECK(wait_gpu(gpu_expected,4000,&cpu,&gpu));
        if (gpu_expected==MACE_METRIC_VALID) {
            CHECK(gpu.fan_rpm.state==MACE_METRIC_VALID && gpu.fan_rpm.value==0);
            CHECK(gpu.fan_percent.state==MACE_METRIC_VALID && gpu.fan_percent.value==0);
            CHECK(gpu.usage_percent.state==MACE_METRIC_VALID && gpu.usage_percent.value==0);
            if (wcscmp(argv[2],L"unknown-power")==0) {
                CHECK(gpu.power_w.state==MACE_METRIC_UNSUPPORTED && gpu.power_w.value==0 && gpu.power_scope==MACE_POWER_SCOPE_UNKNOWN);
            } else if (wcscmp(argv[2],L"gpu-zero")==0) CHECK(gpu.power_w.state==MACE_METRIC_VALID && gpu.power_w.value==0);
            else CHECK(gpu.power_w.state==MACE_METRIC_VALID && gpu.power_w.value>0 && gpu.power_w.value<1);
            if (wcscmp(argv[2],L"amd-chip")==0) CHECK(gpu.power_scope==MACE_POWER_SCOPE_GPU_CHIP);
        } else CHECK(gpu.temperature_c.value==0 && gpu.power_w.value==0 && gpu.fan_rpm.value==0 &&
            gpu.power_w.state==MACE_METRIC_UNSUPPORTED && gpu.usage_percent.state==MACE_METRIC_UNSUPPORTED &&
            gpu.fan_rpm.state==MACE_METRIC_UNSUPPORTED && gpu.fan_percent.state==MACE_METRIC_UNSUPPORTED &&
            gpu.video_memory_used_bytes.state==MACE_METRIC_UNSUPPORTED && gpu.video_memory_used_bytes.value==0 &&
            gpu.video_memory_total_bytes.state==MACE_METRIC_UNSUPPORTED && gpu.video_memory_total_bytes.value==0 &&
            gpu.power_scope==MACE_POWER_SCOPE_UNKNOWN);
        if (wcscmp(argv[2],L"stale")==0) {
            Sleep(2100); CHECK(poll(&cpu,&gpu)); CHECK(cpu.temperature_c.state==MACE_METRIC_VALID);
            CHECK(wait_cpu(MACE_METRIC_READ_FAILED,2000,&cpu,&gpu));
            CHECK(cpu.temperature_c.value==0 && gpu.temperature_c.state==MACE_METRIC_READ_FAILED && gpu.temperature_c.value==0);
        }
        if (wcscmp(argv[2],L"disconnect")==0 || wcscmp(argv[2],L"replay")==0) {
            CHECK(wait_cpu(MACE_METRIC_READ_FAILED,2500,&cpu,&gpu));
            CHECK(cpu.temperature_c.value==0 && gpu.temperature_c.state==MACE_METRIC_READ_FAILED);
            if (wcscmp(argv[2],L"disconnect")==0) CHECK(wait_cpu(MACE_METRIC_VALID,4000,&cpu,&gpu));
        }
        /* Primaries must survive the secondary helper's data. */
        for (uint32_t i=2;i<=3;i++) {
            MaceAdapterLuidV2 luid={i,0}; MaceGpuTelemetryV2 primary={0};
            CHECK(CP_PollGpuTelemetryV2(&luid,&primary,sizeof(primary))==MACE_OK);
            if (fallback) CHECK(primary.provider_id==MACE_PROVIDER_LHM && primary.temperature_c.value==44);
            else CHECK(primary.provider_id==(MaceProviderId)(i==2 ? MACE_PROVIDER_NVIDIA_NVML : MACE_PROVIDER_INTEL_IGCL) &&
                primary.temperature_c.state==MACE_METRIC_VALID && primary.temperature_c.value==(i==2 ? 42.0f : 43.0f));
        }
    }
    Sleep(30); CHECK(poll(&cpu,&gpu));
    CHECK(cpu.usage_percent.state==MACE_METRIC_VALID);
    CHECK(max_poll<100);
    if (wcscmp(argv[2],L"init-race")==0) {
        HANDLE thread=CreateThread(NULL,0,shutdown_worker,NULL,0,NULL);
        CHECK(thread!=NULL);
        ULONGLONG until=GetTickCount64()+3000;
        while (CP_PollCpuTelemetryV2(&cpu,sizeof(cpu))!=MACE_E_NOT_INITIALIZED && GetTickCount64()<until) Sleep(1);
        before=GetTickCount64();
        CHECK(CP_InitializeTelemetry()==MACE_E_BUSY);
        CHECK(GetTickCount64()-before<100);
        CHECK(WaitForSingleObject(thread,5000)==WAIT_OBJECT_0);
        CloseHandle(thread);
        CHECK(CP_InitializeTelemetry()==0);
    }
    CP_ShutdownTelemetry();
    CHECK(CP_PollCpuTelemetryV2(&cpu,sizeof(cpu))==MACE_E_NOT_INITIALIZED);
    printf("scenario %ls: expected CPU=%u, init=%llu ms, max CPU+GPU poll=%llu ms: OK\n",
        argv[2],expected,(unsigned long long)init_ms,(unsigned long long)max_poll);
    return 0;
}
