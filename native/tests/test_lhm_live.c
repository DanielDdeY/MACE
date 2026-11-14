/* Optional physical integration check, deliberately outside CTest. */
#include "mace_api.h"
#include <windows.h>
#include <stdbool.h>
#include <stdio.h>
#include <wchar.h>
#define CHECK(x) do { if (!(x)) { fprintf(stderr,"line %d: %s\n",__LINE__,#x); CP_ShutdownTelemetry(); return 1; } } while(0)
int wmain(int argc,wchar_t** argv) {
    CHECK(argc==2 || (argc==3 && wcscmp(argv[2],L"--require-cpu")==0));
    CHECK(SetEnvironmentVariableW(L"MACE_LHM_HELPER",argv[1]));
    bool require_cpu=argc==3, saw_cpu=false,saw_gpu=false,ready=false;
    LARGE_INTEGER frequency,start,end; QueryPerformanceFrequency(&frequency);
    QueryPerformanceCounter(&start); CHECK(CP_InitializeTelemetry()==0); QueryPerformanceCounter(&end);
    printf("initialize: %.3f ms\n",(double)(end.QuadPart-start.QuadPart)*1000/frequency.QuadPart);
    uint32_t count=0; MaceGpuAdapterInfoV2 adapters[16]={0};
    CHECK(CP_EnumerateGpuAdapters(adapters,16,&count)==MACE_OK && count<=16);
    ULONGLONG begin=GetTickCount64(),until=begin+8000,next_log=0;
    double max_ms=0,first_ready_ms=-1;
    while (GetTickCount64()<until) {
        MaceCpuTelemetryV2 cpu={0};
        QueryPerformanceCounter(&start);
        CHECK(CP_PollCpuTelemetryV2(&cpu,sizeof(cpu))==MACE_OK);
        QueryPerformanceCounter(&end);
        double elapsed=(double)(end.QuadPart-start.QuadPart)*1000/frequency.QuadPart;
        if (elapsed>max_ms) max_ms=elapsed;
        if (cpu.provider_id==MACE_PROVIDER_LHM && (cpu.temperature_c.state==MACE_METRIC_VALID ||
            cpu.temperature_c.state==MACE_METRIC_NO_PROVIDER || cpu.temperature_c.state==MACE_METRIC_UNSUPPORTED)) {
            ready=true; if (first_ready_ms<0) first_ready_ms=(double)(GetTickCount64()-begin);
        }
        saw_cpu |= cpu.temperature_c.state==MACE_METRIC_VALID && cpu.power_w.state==MACE_METRIC_VALID;
        bool log=GetTickCount64()>=next_log;
        if (log) printf("CPU: temp=%.3f state=%u power=%.3f state=%u scope=%u usage=%.3f state=%u provider=%u\n",
            cpu.temperature_c.value,cpu.temperature_c.state,cpu.power_w.value,cpu.power_w.state,cpu.power_scope,
            cpu.usage_percent.value,cpu.usage_percent.state,cpu.provider_id);
        for (uint32_t i=0;i<count;i++) {
            MaceGpuTelemetryV2 gpu={0};
            QueryPerformanceCounter(&start);
            CHECK(CP_PollGpuTelemetryV2(&adapters[i].luid,&gpu,sizeof(gpu))==MACE_OK);
            QueryPerformanceCounter(&end);
            elapsed=(double)(end.QuadPart-start.QuadPart)*1000/frequency.QuadPart;
            if (elapsed>max_ms) max_ms=elapsed;
            saw_gpu |= gpu.temperature_c.state==MACE_METRIC_VALID;
            if (log) printf("GPU %u: temp=%.3f state=%u power=%.3f state=%u scope=%u RPM=%u state=%u provider=%u\n",
                i,gpu.temperature_c.value,gpu.temperature_c.state,gpu.power_w.value,gpu.power_w.state,gpu.power_scope,
                gpu.fan_rpm.value,gpu.fan_rpm.state,gpu.provider_id);
        }
        if (log) next_log=GetTickCount64()+1000;
        Sleep(20);
    }
    CP_ShutdownTelemetry();
    printf("ready=%.0f ms, max poll=%.3f ms, CPU valid=%d, GPU valid=%d\n",first_ready_ms,max_ms,saw_cpu,saw_gpu);
    CHECK(ready && (!require_cpu || saw_cpu));
    if (count) CHECK(saw_gpu);
    return 0;
}
