#include "mace_api.h"
#include "../src/telemetry/lhm_transport.h"
#include <windows.h>
#include <stdio.h>
#include <string.h>
#define CHECK(x) do { if (!(x)) { fprintf(stderr,"line %d: %s\n",__LINE__,#x); return 1; } } while(0)
typedef struct { DWORD pid, result; } Evidence;

static int parent(const wchar_t* helper,const wchar_t* mapping,const wchar_t* ready,bool restricted) {
    HANDLE memory=OpenFileMappingW(FILE_MAP_WRITE,FALSE,mapping);
    HANDLE event=OpenEventW(EVENT_MODIFY_STATE,FALSE,ready);
    CHECK(memory && event);
    Evidence* evidence=MapViewOfFile(memory,FILE_MAP_WRITE,0,0,sizeof(Evidence));
    CHECK(evidence);
    if (restricted) {
        SetEnvironmentVariableW(L"MACE_LHM_HELPER",helper);
        SetEnvironmentVariableW(L"MACE_LHM_HELPER_SHA256",NULL);
        CHECK(CP_InitializeTelemetry()==0);
        MaceCpuTelemetryV2 cpu={0}; ULONGLONG deadline=GetTickCount64()+5000;
        do {
            CHECK(CP_PollCpuTelemetryV2(&cpu,sizeof(cpu))==MACE_OK);
            if (cpu.temperature_c.state==MACE_METRIC_READ_FAILED && cpu.usage_percent.state==MACE_METRIC_VALID) break;
            Sleep(20);
        } while (GetTickCount64()<deadline);
        evidence->result=cpu.temperature_c.state==MACE_METRIC_READ_FAILED &&
            cpu.temperature_c.value==0 && cpu.usage_percent.state==MACE_METRIC_VALID ? 0 : 1;
        CP_ShutdownTelemetry(); SetEvent(event);
        UnmapViewOfFile(evidence); CloseHandle(memory); CloseHandle(event); return 0;
    }
    LhmSession session={0}; HANDLE stop=CreateEventW(NULL,TRUE,FALSE,NULL);
    if (!LhmTransport_Open(helper,stop,&session)) {
        evidence->result=1; SetEvent(event); return 1;
    }
    evidence->pid=session.pid; evidence->result=0; SetEvent(event);
    Sleep(60000); /* Outer test abruptly terminates this exact process handle. */
    LhmTransport_Close(&session); CloseHandle(stop);
    UnmapViewOfFile(evidence); CloseHandle(memory); CloseHandle(event); return 0;
}

static int parent_case(const wchar_t* helper,bool restricted) {
    wchar_t mapping[100],ready[100],exe[32768],command[32768];
    swprintf_s(mapping,100,L"Local\\MaceJobEvidence_%lu_%llu",GetCurrentProcessId(),GetTickCount64());
    swprintf_s(ready,100,L"%s_ready",mapping);
    HANDLE memory=CreateFileMappingW(INVALID_HANDLE_VALUE,NULL,PAGE_READWRITE,0,sizeof(Evidence),mapping);
    HANDLE event=CreateEventW(NULL,TRUE,FALSE,ready); CHECK(memory && event);
    Evidence* evidence=MapViewOfFile(memory,FILE_MAP_ALL_ACCESS,0,0,sizeof(Evidence)); CHECK(evidence);
    evidence->result=1;
    GetModuleFileNameW(NULL,exe,32768);
    swprintf_s(command,32768,L"\"%s\" --parent \"%s\" \"%s\" \"%s\" %u",exe,helper,mapping,ready,restricted ? 1u : 0u);
    STARTUPINFOEXW startup={0}; startup.StartupInfo.cb=sizeof(startup);
    PROCESS_INFORMATION child={0}; HANDLE job=NULL;
    SIZE_T size=0;
    {
        job=CreateJobObjectW(NULL,NULL); CHECK(job);
        JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits={0};
        limits.BasicLimitInformation.LimitFlags=JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        if (restricted) {
            limits.BasicLimitInformation.LimitFlags|=JOB_OBJECT_LIMIT_ACTIVE_PROCESS;
            limits.BasicLimitInformation.ActiveProcessLimit=1;
        }
        CHECK(SetInformationJobObject(job,JobObjectExtendedLimitInformation,&limits,sizeof(limits)));
        InitializeProcThreadAttributeList(NULL,1,0,&size);
        startup.lpAttributeList=HeapAlloc(GetProcessHeap(),0,size); CHECK(startup.lpAttributeList);
        CHECK(InitializeProcThreadAttributeList(startup.lpAttributeList,1,0,&size));
        CHECK(UpdateProcThreadAttribute(startup.lpAttributeList,0,PROC_THREAD_ATTRIBUTE_JOB_LIST,&job,sizeof(job),NULL,NULL));
    }
    CHECK(CreateProcessW(exe,command,NULL,NULL,FALSE,CREATE_NO_WINDOW|EXTENDED_STARTUPINFO_PRESENT,
        NULL,NULL,&startup.StartupInfo,&child));
    CloseHandle(child.hThread);
    if (startup.lpAttributeList) {
        DeleteProcThreadAttributeList(startup.lpAttributeList);
        HeapFree(GetProcessHeap(),0,startup.lpAttributeList);
    }
    CHECK(WaitForSingleObject(event,8000)==WAIT_OBJECT_0);
    CHECK(evidence->result==0);
    if (!restricted) {
        HANDLE helper_process=OpenProcess(SYNCHRONIZE,FALSE,evidence->pid); CHECK(helper_process);
        CHECK(TerminateProcess(child.hProcess,123));
        CHECK(WaitForSingleObject(child.hProcess,2000)==WAIT_OBJECT_0);
        CHECK(WaitForSingleObject(helper_process,2000)==WAIT_OBJECT_0);
        CloseHandle(helper_process);
    } else {
        CHECK(WaitForSingleObject(child.hProcess,2000)==WAIT_OBJECT_0);
        DWORD exit_code=1; CHECK(GetExitCodeProcess(child.hProcess,&exit_code) && exit_code==0);
    }
    CloseHandle(child.hProcess); if (job) CloseHandle(job);
    UnmapViewOfFile(evidence); CloseHandle(memory); CloseHandle(event);
    puts(restricted ? "real restrictive job rejects helper; CPU READ_FAILED/0, Windows usage VALID: OK" :
        "abrupt parent death closes last job handle and kills owned helper: OK");
    return 0;
}

int wmain(int argc,wchar_t** argv) {
    if (argc==6 && wcscmp(argv[1],L"--parent")==0)
        return parent(argv[2],argv[3],argv[4],wcscmp(argv[5],L"1")==0);
    CHECK(argc==3);
    if (wcscmp(argv[2],L"abrupt-parent")==0) return parent_case(argv[1],false);
    if (wcscmp(argv[2],L"restrictive-job")==0) return parent_case(argv[1],true);
    bool forced=wcscmp(argv[2],L"forced-close")==0;
    bool descendants=wcscmp(argv[2],L"descendants")==0;
    SetEnvironmentVariableW(L"MACE_SIM_SCENARIO",forced ? L"hang-close" : descendants ? L"job-descendant" : L"valid");
    LhmSession session={0}; HANDLE stop=CreateEventW(NULL,TRUE,FALSE,NULL); CHECK(stop);
    CHECK(LhmTransport_Open(argv[1],stop,&session));
    BOOL assigned=FALSE; CHECK(IsProcessInJob(session.process,session.job,&assigned) && assigned);
    DWORD flags=0; CHECK(GetHandleInformation(session.job,&flags) && !(flags&HANDLE_FLAG_INHERIT));
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits={0};
    CHECK(QueryInformationJobObject(session.job,JobObjectExtendedLimitInformation,&limits,sizeof(limits),NULL));
    CHECK(limits.BasicLimitInformation.LimitFlags&JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE);
    CHECK(!(limits.BasicLimitInformation.LimitFlags&(JOB_OBJECT_LIMIT_BREAKAWAY_OK|JOB_OBJECT_LIMIT_SILENT_BREAKAWAY_OK)));
    unsigned char frame[48]; CHECK(LhmTransport_Read(&session,stop,frame,48,GetTickCount64()+5000));
    HANDLE processes[8]; DWORD count=0;
    if (descendants) {
        ULONG_PTR storage[16]={0}; JOBOBJECT_BASIC_PROCESS_ID_LIST* ids=(JOBOBJECT_BASIC_PROCESS_ID_LIST*)storage;
        ULONGLONG until=GetTickCount64()+2000;
        do {
            CHECK(QueryInformationJobObject(session.job,JobObjectBasicProcessIdList,ids,sizeof(storage),NULL));
            if (ids->NumberOfProcessIdsInList>=2) break;
            Sleep(10);
        } while (GetTickCount64()<until);
        printf("owned job members: %lu\n",ids->NumberOfProcessIdsInList);
        CHECK(ids->NumberOfProcessIdsInList>=2 && ids->NumberOfProcessIdsInList<=8);
        unsigned fake_count=0;
        for (DWORD i=0;i<ids->NumberOfProcessIdsInList;i++) {
            processes[count]=OpenProcess(SYNCHRONIZE|PROCESS_QUERY_LIMITED_INFORMATION,FALSE,(DWORD)ids->ProcessIdList[i]); CHECK(processes[count]);
            wchar_t image[32768]; DWORD length=32768;
            CHECK(QueryFullProcessImageNameW(processes[count],0,image,&length));
            const wchar_t* name=wcsrchr(image,L'\\'); name=name ? name+1 : image;
            printf("owned member image: %ls\n",name);
            const wchar_t* expected=wcsrchr(argv[1],L'\\'); expected=expected ? expected+1 : argv[1];
            const wchar_t* forward=wcsrchr(expected,L'/'); if (forward) expected=forward+1;
            if (_wcsicmp(name,expected)==0) fake_count++;
            count++;
        }
        CHECK(fake_count==2);
    } else {
        CHECK(DuplicateHandle(GetCurrentProcess(),session.process,GetCurrentProcess(),&processes[0],SYNCHRONIZE,FALSE,0)); count=1;
    }
    ULONGLONG before=GetTickCount64(); LhmTransport_Close(&session); ULONGLONG elapsed=GetTickCount64()-before;
    CHECK(elapsed<3500); if (forced) CHECK(elapsed>=1800);
    for (DWORD i=0;i<count;i++) { CHECK(WaitForSingleObject(processes[i],1000)==WAIT_OBJECT_0); CloseHandle(processes[i]); }
    CHECK(!session.job && !session.process && !session.pipe); CloseHandle(stop);
    printf("job containment, handle ownership, descendant cleanup; close %llu ms: OK\n",elapsed);
    return 0;
}
