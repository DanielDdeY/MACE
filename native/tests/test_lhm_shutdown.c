#include "mace_api.h"
#include <windows.h>
#include <stdio.h>
#define CHECK(x) do { if (!(x)) { fprintf(stderr,"line %d: %s\n",__LINE__,#x); return 1; } } while(0)
static DWORD WINAPI close_worker(void* unused) { (void)unused; CP_ShutdownTelemetry(); return 0; }
int wmain(int argc,wchar_t** argv) {
    CHECK(argc==2);
    CHECK(SetEnvironmentVariableW(L"MACE_LHM_HELPER",argv[1]));
    CHECK(SetEnvironmentVariableW(L"MACE_LHM_HELPER_SHA256",NULL));
    CHECK(SetEnvironmentVariableW(L"MACE_SIM_SCENARIO",L"valid"));
    CHECK(SetEnvironmentVariableW(L"MACE_SIM_SLOW_STOP",L"6500"));
    CHECK(CP_InitializeTelemetry()==0);
    MaceCpuTelemetryV2 cpu={0};
    ULONGLONG until=GetTickCount64()+6000;
    do { CHECK(CP_PollCpuTelemetryV2(&cpu,sizeof(cpu))==MACE_OK);
        if (cpu.temperature_c.state==MACE_METRIC_VALID) break;
        Sleep(10);
    } while (GetTickCount64()<until);
    CHECK(cpu.temperature_c.state==MACE_METRIC_VALID);
    HANDLE threads[3]; ULONGLONG before=GetTickCount64();
    for (unsigned i=0;i<3;i++) { threads[i]=CreateThread(NULL,0,close_worker,NULL,0,NULL); CHECK(threads[i]); }
    CHECK(WaitForMultipleObjects(3,threads,TRUE,4700)==WAIT_OBJECT_0);
    ULONGLONG elapsed=GetTickCount64()-before;
    CHECK(elapsed<4700);
    printf("three concurrent public shutdown calls: %llu ms\n",elapsed);
    for (unsigned i=0;i<3;i++) CloseHandle(threads[i]);
    CHECK(CP_PollCpuTelemetryV2(&cpu,sizeof(cpu))==MACE_E_NOT_INITIALIZED);
    before=GetTickCount64(); CHECK(CP_InitializeTelemetry()==MACE_E_BUSY);
    CHECK(GetTickCount64()-before<100);
    CHECK(SetEnvironmentVariableW(L"MACE_SIM_SLOW_STOP",NULL));
    until=GetTickCount64()+4500; int status;
    do { status=CP_InitializeTelemetry(); if (status==0) break; CHECK(status==MACE_E_BUSY); Sleep(10); }
    while (GetTickCount64()<until);
    CHECK(status==0); CHECK(CP_PollCpuTelemetryV2(&cpu,sizeof(cpu))==MACE_OK);
    CP_ShutdownTelemetry();
    CHECK(CP_PollCpuTelemetryV2(&cpu,sizeof(cpu))==MACE_E_NOT_INITIALIZED);
    puts("bounded concurrent shutdown; NOT_INITIALIZED; quarantined init BUSY; safe recovery: OK");
    return 0;
}
