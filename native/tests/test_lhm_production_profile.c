#include "mace_api.h"
#include <windows.h>
#include <stdio.h>
#define CHECK(x) do { if (!(x)) { fprintf(stderr,"production line %d: %s\n",__LINE__,#x); return 1; } } while(0)
int wmain(int argc,wchar_t** argv) {
    CHECK(argc==3);
    wchar_t path[32768],folder[32768]; GetModuleFileNameW(NULL,path,32768);
    wchar_t* slash=wcsrchr(path,L'\\'); CHECK(slash); *slash=0;
    CHECK(swprintf_s(folder,32768,L"%s\\profile-proof-%lu-%llu",path,GetCurrentProcessId(),GetTickCount64())>0);
    CHECK(CreateDirectoryW(folder,NULL));
    CHECK(swprintf_s(path,32768,L"%s\\mace_native.dll",folder)>0); CHECK(CopyFileW(argv[1],path,TRUE));
    CHECK(SetEnvironmentVariableW(L"MACE_LHM_HELPER",argv[2]));
    CHECK(SetEnvironmentVariableW(L"MACE_LHM_HELPER_SHA256",L"0000000000000000000000000000000000000000000000000000000000000000"));
    HMODULE library=LoadLibraryW(path); CHECK(library);
    int (*initialize)(void)=(int(*)(void))(uintptr_t)GetProcAddress(library,"CP_InitializeTelemetry");
    void (*shutdown)(void)=(void(*)(void))(uintptr_t)GetProcAddress(library,"CP_ShutdownTelemetry");
    MaceStatus (*poll)(MaceCpuTelemetryV2*,uint32_t)=(MaceStatus(*)(MaceCpuTelemetryV2*,uint32_t))(uintptr_t)GetProcAddress(library,"CP_PollCpuTelemetryV2");
    CHECK(initialize && shutdown && poll); CHECK(initialize()==0);
    MaceCpuTelemetryV2 cpu={0}; ULONGLONG until=GetTickCount64()+3000;
    do { CHECK(poll(&cpu,sizeof(cpu))==MACE_OK);
        if (cpu.temperature_c.state==MACE_METRIC_HELPER_UNAVAILABLE && cpu.usage_percent.state==MACE_METRIC_VALID) break;
        Sleep(20);
    } while (GetTickCount64()<until);
    CHECK(cpu.temperature_c.state==MACE_METRIC_HELPER_UNAVAILABLE && cpu.temperature_c.value==0 && cpu.usage_percent.state==MACE_METRIC_VALID);
    shutdown(); FreeLibrary(library);
    puts("actual production DLL ignores inherited helper path/hash; Windows CPU usage survives: OK");
    return 0;
}
