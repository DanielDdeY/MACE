#include "mace_api.h"
#include <windows.h>
#include <tlhelp32.h>
#include <stdio.h>
#include <string.h>
#define CHECK(x) do { if (!(x)) { fprintf(stderr,"security line %d: %s\n",__LINE__,#x); CP_ShutdownTelemetry(); return 1; } } while(0)
static bool wait_state(MaceMetricState expected,DWORD ms) {
    ULONGLONG until=GetTickCount64()+ms; MaceCpuTelemetryV2 cpu;
    do { memset(&cpu,0,sizeof(cpu));
        if (CP_PollCpuTelemetryV2(&cpu,sizeof(cpu))!=MACE_OK) return false;
        if (cpu.temperature_c.state==expected) return expected==MACE_METRIC_VALID || cpu.temperature_c.value==0;
        Sleep(10);
    } while (GetTickCount64()<until);
    return false;
}
static HANDLE own_helper(void) {
    HANDLE snapshot=CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS,0); if (snapshot==INVALID_HANDLE_VALUE) return NULL;
    PROCESSENTRY32W item={0}; item.dwSize=sizeof(item); HANDLE child=NULL;
    if (Process32FirstW(snapshot,&item)) do {
        if (item.th32ParentProcessID==GetCurrentProcessId() && !_wcsicmp(item.szExeFile,L"Mace.Lhm.Helper.exe")) {
            child=OpenProcess(PROCESS_TERMINATE|SYNCHRONIZE,FALSE,item.th32ProcessID); break;
        }
    } while (Process32NextW(snapshot,&item));
    CloseHandle(snapshot); return child;
}
int wmain(int argc,wchar_t** argv) {
    CHECK(argc==4);
    const wchar_t* mode=argv[3]; wchar_t exe[32768],root[32768],source[32768],destination[32768],signal[100];
    GetModuleFileNameW(NULL,exe,32768); wchar_t* slash=wcsrchr(exe,L'\\'); CHECK(slash); *slash=0;
    CHECK(swprintf_s(root,32768,L"%s\\lhm",exe)>0); CreateDirectoryW(root,NULL);
    const wchar_t* names[]={L"Mace.Lhm.Helper.exe",L"dependency.dll",L"helper-runtime.manifest"};
    for (unsigned i=0;i<3;i++) {
        swprintf_s(source,32768,L"%s\\%s",argv[2],names[i]); swprintf_s(destination,32768,L"%s\\%s",root,names[i]);
        bool copied=false; ULONGLONG until=GetTickCount64()+2500;
        do {
            if (CopyFileW(source,destination,FALSE)) { copied=true; break; }
            DWORD error=GetLastError();
            if (error!=ERROR_SHARING_VIOLATION && error!=ERROR_ACCESS_DENIED) break;
            Sleep(10); // Prior process image teardown/AV can briefly retain a file section.
        } while (GetTickCount64()<until);
        if (!copied) fwprintf(stderr,L"fixture copy %s failed: %lu\n",names[i],GetLastError());
        CHECK(copied);
    }
    swprintf_s(signal,100,L"Local\\MaceSecureStarted_%lu",GetCurrentProcessId());
    HANDLE started=CreateEventW(NULL,TRUE,FALSE,signal); CHECK(started);
    CHECK(SetEnvironmentVariableW(L"MACE_LHM_HELPER",argv[1]));
    CHECK(SetEnvironmentVariableW(L"MACE_LHM_HELPER_SHA256",L"0000000000000000000000000000000000000000000000000000000000000000"));
    CHECK(SetEnvironmentVariableW(L"DOTNET_STARTUP_HOOKS",L"untrusted-parent-value"));
    CHECK(SetEnvironmentVariableW(L"CORECLR_ENABLE_PROFILING",L"1"));
    bool ignore=!wcscmp(mode,L"ignore-env"),valid=!wcscmp(mode,L"valid") || !wcscmp(mode,L"locks") || !wcscmp(mode,L"relaunch");
    const wchar_t* damaged=ignore ? names[0] : !wcscmp(mode,L"manifest") ? names[2] : names[1];
    swprintf_s(destination,32768,L"%s\\%s",root,damaged);
    if (ignore || !wcscmp(mode,L"missing")) CHECK(DeleteFileW(destination));
    else if (!valid) {
        if (!wcscmp(mode,L"extra")) swprintf_s(destination,32768,L"%s\\unexpected.dll",root);
        HANDLE file=CreateFileW(destination,FILE_APPEND_DATA,0,NULL,OPEN_ALWAYS,0,NULL); CHECK(file!=INVALID_HANDLE_VALUE);
        DWORD written=0; CHECK(WriteFile(file,"!",1,&written,NULL)); CloseHandle(file);
    }
    CHECK(CP_InitializeTelemetry()==0);
    CHECK(wait_state(valid ? MACE_METRIC_VALID : ignore ? MACE_METRIC_HELPER_UNAVAILABLE : MACE_METRIC_HELPER_UNTRUSTED,5000));
    CHECK(WaitForSingleObject(started,0)==(valid ? WAIT_OBJECT_0 : WAIT_TIMEOUT));
    if (!wcscmp(mode,L"locks")) {
        for (unsigned i=0;i<3;i++) {
            swprintf_s(destination,32768,L"%s\\%s",root,names[i]);
            HANDLE file=CreateFileW(destination,GENERIC_WRITE,FILE_SHARE_READ|FILE_SHARE_WRITE|FILE_SHARE_DELETE,NULL,OPEN_EXISTING,0,NULL);
            CHECK(file==INVALID_HANDLE_VALUE && GetLastError()==ERROR_SHARING_VIOLATION);
            swprintf_s(source,32768,L"%s\\renamed.tmp",root);
            CHECK(!MoveFileExW(destination,source,0));
        }
    }
    if (!wcscmp(mode,L"relaunch")) {
        HANDLE child=own_helper(); CHECK(child); CHECK(TerminateProcess(child,1)); CHECK(WaitForSingleObject(child,2000)==WAIT_OBJECT_0); CloseHandle(child);
        CHECK(wait_state(MACE_METRIC_READ_FAILED,2000)); ResetEvent(started);
        swprintf_s(destination,32768,L"%s\\dependency.dll",root);
        HANDLE file=INVALID_HANDLE_VALUE; ULONGLONG until=GetTickCount64()+500;
        do { file=CreateFileW(destination,FILE_APPEND_DATA,0,NULL,OPEN_EXISTING,0,NULL); if (file!=INVALID_HANDLE_VALUE) break; Sleep(1); } while (GetTickCount64()<until);
        CHECK(file!=INVALID_HANDLE_VALUE); DWORD written=0; CHECK(WriteFile(file,"!",1,&written,NULL)); CloseHandle(file);
        Sleep(1700); CHECK(wait_state(MACE_METRIC_HELPER_UNTRUSTED,100)); CHECK(WaitForSingleObject(started,0)==WAIT_TIMEOUT);
    }
    CP_ShutdownTelemetry();
    if (!wcscmp(mode,L"extra")) CHECK(DeleteFileW(destination));
    CloseHandle(started);
    wprintf(L"production security %s: expected state, launch containment, environment and integrity: OK\n",mode);
    return 0;
}
