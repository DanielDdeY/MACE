#include "mace_api.h"
#include "lhm_retry_evidence.h"
#include "lhm_retry_fixture.h"
#include <stdio.h>
#include <string.h>
#define CHECK(x) do { if (!(x)) { fprintf(stderr,"retry receiver line %d: %s\n",__LINE__,#x); CP_ShutdownTelemetry(); return 1; } } while(0)
static uint64_t max_poll;
static bool cpu_state(MaceMetricState expected,DWORD timeout) {
    ULONGLONG until=GetTickCount64()+timeout;
    do {
        MaceCpuTelemetryV2 cpu={0}; ULONGLONG before=GetTickCount64();
        if (CP_PollCpuTelemetryV2(&cpu,sizeof(cpu))!=MACE_OK) return false;
        uint64_t elapsed=GetTickCount64()-before; if (elapsed>max_poll) max_poll=elapsed;
        if (cpu.temperature_c.state==expected) return expected==MACE_METRIC_VALID || cpu.temperature_c.value==0;
        Sleep(5);
    } while (GetTickCount64()<until);
    return false;
}
static bool attempts(uint32_t count,DWORD timeout,LhmRetryEvidence* e) {
    ULONGLONG until=GetTickCount64()+timeout;
    do { LhmReceiver_TestEvidence(e); if (e->policy.attempts>=count) return true; Sleep(5); } while (GetTickCount64()<until);
    return false;
}
int wmain(int argc,wchar_t** argv) {
    CHECK(argc==3);
    const wchar_t* mode=argv[2];
    wchar_t mapping[100]; swprintf_s(mapping,100,L"Local\\MaceRetryFixture_%lu",GetCurrentProcessId());
    HANDLE memory=CreateFileMappingW(INVALID_HANDLE_VALUE,NULL,PAGE_READWRITE,0,sizeof(LhmRetryFixture),mapping); CHECK(memory);
    LhmRetryFixture* shared=MapViewOfFile(memory,FILE_MAP_ALL_ACCESS,0,0,sizeof(*shared)); CHECK(shared);
    memset(shared,0,sizeof(*shared));
    CHECK(SetEnvironmentVariableW(L"MACE_LHM_HELPER",argv[1]));
    CHECK(SetEnvironmentVariableW(L"MACE_LHM_HELPER_SHA256",NULL));
    CHECK(SetEnvironmentVariableW(L"MACE_SIM_SCENARIO",mode));
    bool missing=!wcscmp(mode,L"permanent-missing"),trust=!wcscmp(mode,L"permanent-trust"),job=!wcscmp(mode,L"permanent-job");
    HANDLE restricting_job=NULL;
    if (missing) {
        wchar_t absent[32768]; swprintf_s(absent,32768,L"%s.missing",argv[1]);
        CHECK(SetEnvironmentVariableW(L"MACE_LHM_HELPER",absent));
    }
    if (trust) CHECK(SetEnvironmentVariableW(L"MACE_LHM_HELPER_SHA256",L"0000000000000000000000000000000000000000000000000000000000000000"));
    if (job) {
        restricting_job=CreateJobObjectW(NULL,NULL); CHECK(restricting_job);
        JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits={0};
        limits.BasicLimitInformation.LimitFlags=JOB_OBJECT_LIMIT_ACTIVE_PROCESS;
        limits.BasicLimitInformation.ActiveProcessLimit=1;
        CHECK(SetInformationJobObject(restricting_job,JobObjectExtendedLimitInformation,&limits,sizeof(limits)));
        CHECK(AssignProcessToJobObject(restricting_job,GetCurrentProcess()));
    }
    ULONGLONG start=GetTickCount64(); CHECK(CP_InitializeTelemetry()==0); CHECK(GetTickCount64()-start<200);
    LhmRetryEvidence e={0};
    if (missing || trust || job) {
        CHECK(cpu_state(missing ? MACE_METRIC_NO_PROVIDER : MACE_METRIC_READ_FAILED,4000));
        Sleep(1200); LhmReceiver_TestEvidence(&e);
        CHECK(e.policy.blocked && e.policy.attempts==(uint32_t)(missing ? 0 : 1));
        CHECK(shared->launches==0);
    } else if (!wcscmp(mode,L"retry-loop") || !wcscmp(mode,L"retry-recover")) {
        CHECK(attempts(4,4000,&e));
        CHECK(cpu_state(MACE_METRIC_READ_FAILED,1000));
        // attempts increments BEFORE launch; wait for the fourth failure/cleanup,
        // not a fixed delay that races process/image teardown on a loaded host.
        ULONGLONG exhausted_until=GetTickCount64()+5000;
        do {
            LhmReceiver_TestEvidence(&e);
            if (e.policy.slow || e.policy.blocked) break;
            Sleep(5);
        } while (GetTickCount64()<exhausted_until);
        if (e.policy.attempts!=4 || !e.policy.slow || e.policy.blocked)
            fprintf(stderr,"attempts=%u window-count=%u slow=%u blocked=%u cause=%u\n",
                e.policy.attempts,e.policy.count,(unsigned)e.policy.slow,(unsigned)e.policy.blocked,e.cause);
        CHECK(e.policy.attempts==4 && e.policy.slow && !e.policy.blocked);
        CHECK(shared->launches==4);
        CHECK(attempts(5,9000,&e)); // 60 s policy window = 6 s only in this compiled fixture.
        if (!wcscmp(mode,L"retry-recover")) {
            CHECK(cpu_state(MACE_METRIC_VALID,5000));
            ULONGLONG until=GetTickCount64()+4000;
            do { LhmReceiver_TestEvidence(&e); if (e.policy.consecutive==3) break; Sleep(5); } while (GetTickCount64()<until);
            CHECK(e.policy.consecutive==3 && !e.policy.slow && e.policy.backoff_ms==1000);
        } else CHECK(cpu_state(MACE_METRIC_READ_FAILED,1000));
    } else if (!wcscmp(mode,L"retry-streak")) {
        CHECK(attempts(2,4000,&e)); CHECK(cpu_state(MACE_METRIC_UNSUPPORTED,3000));
        LhmReceiver_TestEvidence(&e); CHECK(e.policy.consecutive==0 && e.policy.backoff_ms==2000);
        InterlockedExchange(&shared->phase,1);
        ULONGLONG until=GetTickCount64()+5000; bool saw_two=false;
        do {
            LhmReceiver_TestEvidence(&e);
            if (e.policy.consecutive==2) { CHECK(e.policy.backoff_ms==2000); saw_two=true; }
            if (e.policy.consecutive==3) break;
            Sleep(5);
        } while (GetTickCount64()<until);
        CHECK(saw_two && e.policy.consecutive==3 && e.policy.backoff_ms==1000);
    } else if (!wcscmp(mode,L"retry-hung")) {
        CHECK(attempts(1,3000,&e));
        ULONGLONG until=GetTickCount64()+2000;
        while (!shared->pid && GetTickCount64()<until) Sleep(5);
        HANDLE owned=OpenProcess(SYNCHRONIZE,FALSE,(DWORD)shared->pid); CHECK(owned);
        CHECK(cpu_state(MACE_METRIC_READ_FAILED,4500));
        CHECK(WaitForSingleObject(owned,4000)==WAIT_OBJECT_0); CloseHandle(owned);
        CHECK(cpu_state(MACE_METRIC_VALID,4000));
        LhmReceiver_TestEvidence(&e); CHECK(e.policy.attempts==2 && e.policy.count==1);
    } else if (!wcscmp(mode,L"retry-broken")) {
        CHECK(cpu_state(MACE_METRIC_VALID,3000)); CHECK(cpu_state(MACE_METRIC_READ_FAILED,3000));
        CHECK(attempts(2,4000,&e)); CHECK(cpu_state(MACE_METRIC_VALID,3000));
        LhmReceiver_TestEvidence(&e); CHECK(e.policy.count==1);
    } else if (!wcscmp(mode,L"retry-invalid")) {
        CHECK(cpu_state(MACE_METRIC_READ_FAILED,3000));
        CHECK(attempts(2,4000,&e)); CHECK(cpu_state(MACE_METRIC_VALID,3000));
        LhmReceiver_TestEvidence(&e); CHECK(e.policy.count==1);
    } else if (!wcscmp(mode,L"retry-pawn")) {
        CHECK(cpu_state(MACE_METRIC_NO_PROVIDER,3000));
        InterlockedExchange(&shared->phase,1); CHECK(cpu_state(MACE_METRIC_VALID,3000));
        InterlockedExchange(&shared->phase,2); CHECK(cpu_state(MACE_METRIC_NO_PROVIDER,3000));
        InterlockedExchange(&shared->phase,3); CHECK(cpu_state(MACE_METRIC_VALID,3000));
        LhmReceiver_TestEvidence(&e); CHECK(e.policy.attempts==1 && e.policy.count==0 && shared->launches==1);
    } else CHECK(false);
    CHECK(max_poll<100);
    CP_ShutdownTelemetry(); if (restricting_job) CloseHandle(restricting_job);
    UnmapViewOfFile(shared); CloseHandle(memory);
    printf("%ls: V2 expected state, retry budget and process ownership OK; max poll %llu ms\n",mode,(unsigned long long)max_poll);
    return 0;
}
