#include "lhm_receiver.h"
#include "lhm_transport.h"
#include "lhm_wire.h"
#include "lhm_retry.h"
#include "legacy_telemetry.h"
#include <windows.h>
#include <stdio.h>
#include <string.h>
#include "lhm_security_config.h"

extern IMAGE_DOS_HEADER __ImageBase;
static SRWLOCK cache_lock=SRWLOCK_INIT;
static HANDLE stop_event, worker;
static LhmFrame cache;
static bool have_frame;
static MaceMetricState cache_state=MACE_METRIC_WARMING_UP;
#ifdef MACE_LHM_TEST_HOOKS
#include "../../tests/lhm_retry_evidence.h"
static LhmRetryEvidence test_evidence;
void LhmReceiver_TestEvidence(LhmRetryEvidence* e) {
    AcquireSRWLockShared(&cache_lock); *e=test_evidence; ReleaseSRWLockShared(&cache_lock);
}
#endif
static void evidence(const LhmRetry* r,uint32_t cause) {
#ifdef MACE_LHM_TEST_HOOKS
    AcquireSRWLockExclusive(&cache_lock); test_evidence=(LhmRetryEvidence){*r,cause}; ReleaseSRWLockExclusive(&cache_lock);
#else
    (void)r; (void)cause;
#endif
}
#ifndef MACE_LHM_RETRY_TEST_TIME_SCALE
#define MACE_LHM_RETRY_TEST_TIME_SCALE 1u
#endif
static bool awake_ms(uint64_t* ms) {
    ULONGLONG ticks=0;
    if (!QueryUnbiasedInterruptTime(&ticks)) return false;
    *ms=(ticks/10000)*MACE_LHM_RETRY_TEST_TIME_SCALE;
    return true;
}
static bool usable_frame(const LhmFrame* f,uint64_t now) {
    for (uint32_t i=0;i<f->count;i++) {
        const LhmDevice* d=&f->devices[i];
        bool cpu=d->kind==1;
        if (!cpu) {
            MaceGpuAdapterInfoV2 adapter={0};
            adapter.vendor_id=d->vendor; adapter.device_id=d->device;
            adapter.subsystem_id=d->subsystem; adapter.revision=d->revision;
            if (d->identity!=2 || LegacyTelemetry_PciMatches(&adapter)!=1) continue;
        }
        for (unsigned m=0;m<(cpu ? 2u : 7u);m++) {
            if (!cpu && m==1 && d->metrics[m].meaning==6) continue;
            if (LhmWire_State(f,&d->metrics[m],cpu,now)==MACE_METRIC_VALID) return true;
        }
    }
    return false;
}

static void invalidate(MaceMetricState state) {
    AcquireSRWLockExclusive(&cache_lock);
    have_frame=false; memset(&cache,0,sizeof(cache)); cache_state=state;
    ReleaseSRWLockExclusive(&cache_lock);
}
static bool helper_path(wchar_t* path, DWORD capacity) {
#if MACE_LHM_DEVELOPMENT
    DWORD size=GetEnvironmentVariableW(L"MACE_LHM_HELPER",path,capacity);
    if (size>=capacity) return false;
    if (size) return size>3 && path[1]==L':' && (path[2]==L'\\' || path[2]==L'/');
#endif
    DWORD size_default=GetModuleFileNameW((HMODULE)&__ImageBase,path,capacity);
    if (!size_default || size_default>=capacity) return false;
    wchar_t* slash=wcsrchr(path,L'\\');
    if (!slash) return false;
    *slash=0;
    return wcscat_s(path,capacity,L"\\lhm\\Mace.Lhm.Helper.exe")==0;
}
static DWORD WINAPI receive_worker(void* unused) {
    (void)unused;
    LhmRetry retry; LhmRetry_Init(&retry); evidence(&retry,0);
    wchar_t path[32768];
    if (!helper_path(path,32768)) {
        retry.blocked=true; evidence(&retry,1); invalidate(MACE_METRIC_NO_PROVIDER); return 0;
    }
    if (GetFileAttributesW(path)==INVALID_FILE_ATTRIBUTES) {
        DWORD error=GetLastError(); bool missing=error==ERROR_FILE_NOT_FOUND || error==ERROR_PATH_NOT_FOUND;
        retry.blocked=true; evidence(&retry,missing ? 1u : 2u);
        invalidate(missing ? MACE_METRIC_NO_PROVIDER : MACE_METRIC_READ_FAILED);
        return 0;
    }
    while (WaitForSingleObject(stop_event,0)==WAIT_TIMEOUT) {
        uint64_t now;
        if (!awake_ms(&now)) { retry.blocked=true; evidence(&retry,5); invalidate(MACE_METRIC_READ_FAILED); break; }
        if (!LhmRetry_Start(&retry,now)) {
            evidence(&retry,0);
            if (retry.blocked) break;
            uint64_t remaining=retry.next_ms>now ? retry.next_ms-now : 1;
            DWORD wait=(DWORD)((remaining+MACE_LHM_RETRY_TEST_TIME_SCALE-1)/MACE_LHM_RETRY_TEST_TIME_SCALE);
            if (wait>1000) wait=1000;
            if (WaitForSingleObject(stop_event,wait)!=WAIT_TIMEOUT) break;
            continue;
        }
        evidence(&retry,0);
        LhmSession session={0};
        /* Each attempt owns a new child, pipe name, PID and sequence domain. */
        LhmOpenFailure failure=LHM_OPEN_TRANSIENT;
        bool opened=LhmTransport_OpenEx(path,stop_event,&session,&failure);
        uint64_t sequence=0,last_sample=0;
        if (opened) for (;;) {
            unsigned char bytes[LHM_MAX_FRAME];
            ULONGLONG deadline=GetTickCount64()+LHM_MAX_AGE_MS;
            if (!LhmTransport_Read(&session,stop_event,bytes,12,deadline)) break;
            uint32_t size=LhmWire_U32(bytes+8);
            if (size<LHM_HEADER_SIZE || size>LHM_MAX_FRAME || (size-LHM_HEADER_SIZE)%LHM_DEVICE_SIZE ||
                !LhmTransport_Read(&session,stop_event,bytes+12,size-12,deadline)) break;
            LhmFrame frame;
            if (!LhmWire_Decode(bytes,size,session.pid,sequence,GetTickCount64(),&frame)) break;
            sequence=frame.sequence;
            uint64_t received=GetTickCount64();
            if (last_sample && received-last_sample>LHM_MAX_AGE_MS) LhmRetry_Sample(&retry,false);
            LhmRetry_Sample(&retry,usable_frame(&frame,received)); last_sample=received;
            evidence(&retry,0);
            AcquireSRWLockExclusive(&cache_lock);
            cache=frame; have_frame=true; cache_state=MACE_METRIC_VALID;
            ReleaseSRWLockExclusive(&cache_lock);
        }
        invalidate(MACE_METRIC_READ_FAILED);
        LhmTransport_Close(&session);
        if (WaitForSingleObject(stop_event,0)!=WAIT_TIMEOUT) break;
        if (!awake_ms(&now)) { retry.blocked=true; evidence(&retry,5); break; }
        LhmRetry_Failed(&retry,now,!opened && failure!=LHM_OPEN_TRANSIENT);
        evidence(&retry,!opened && failure!=LHM_OPEN_TRANSIENT ? (uint32_t)failure+1 : 0);
        if (retry.blocked) break;
    }
    return 0;
}
bool LhmReceiver_Start(void) {
    invalidate(MACE_METRIC_WARMING_UP);
    stop_event=CreateEventW(NULL,TRUE,FALSE,NULL);
    if (!stop_event) { invalidate(MACE_METRIC_READ_FAILED); return false; }
    worker=CreateThread(NULL,0,receive_worker,NULL,0,NULL);
    if (!worker) { CloseHandle(stop_event); stop_event=NULL; invalidate(MACE_METRIC_READ_FAILED); return false; }
    return true;
}
void LhmReceiver_RequestStop(void) {
    if (stop_event) SetEvent(stop_event);
}
bool LhmReceiver_StopUntil(uint64_t deadline_ms) {
    LhmReceiver_RequestStop();
    if (worker) {
        uint64_t now=GetTickCount64();
        DWORD remaining=now<deadline_ms ? (DWORD)(deadline_ms-now) : 0;
        if (WaitForSingleObject(worker,remaining)!=WAIT_OBJECT_0) {
            LhmTransport_AbortActive();
            /* Retain event/worker/cache until it really exits; never kill a thread. */
            return false;
        }
        CloseHandle(worker); worker=NULL;
    }
    if (stop_event) { CloseHandle(stop_event); stop_event=NULL; }
    invalidate(MACE_METRIC_NOT_INITIALIZED);
    return true;
}
static void snapshot(LhmFrame* f, MaceMetricState* state) {
    *state=MACE_METRIC_WARMING_UP;
    memset(f,0,sizeof(*f));
    if (!TryAcquireSRWLockShared(&cache_lock)) return;
    *state=cache_state;
    if (have_frame) {
        *f=cache;
        uint64_t now=GetTickCount64();
        if (f->timestamp>now || now-f->timestamp>LHM_MAX_AGE_MS) *state=MACE_METRIC_READ_FAILED;
        else if (f->helper==1) *state=MACE_METRIC_WARMING_UP;
        else if (f->helper!=2) *state=MACE_METRIC_READ_FAILED;
    }
    ReleaseSRWLockShared(&cache_lock);
}
bool LhmReceiver_ApplyCpu(MaceCpuTelemetryV2* s) {
    LhmFrame f; MaceMetricState state;
    snapshot(&f,&state);
    s->provider_id=MACE_PROVIDER_LHM; s->power_scope=MACE_POWER_SCOPE_UNKNOWN;
    s->temperature_c=(MaceMetricF32V2){0,state}; s->power_w=s->temperature_c;
    if (state!=MACE_METRIC_VALID) return true;
    const LhmDevice* cpu=NULL;
    for (uint32_t i=0;i<f.count;i++) if (f.devices[i].kind==1) cpu=&f.devices[i];
    if (!cpu) { s->temperature_c.state=s->power_w.state=MACE_METRIC_NO_PROVIDER; return true; }
    s->temperature_c.state=LhmWire_State(&f,&cpu->metrics[0],true,GetTickCount64());
    s->power_w.state=LhmWire_State(&f,&cpu->metrics[1],true,GetTickCount64());
    if (s->temperature_c.state==MACE_METRIC_VALID) s->temperature_c.value=(float)cpu->metrics[0].value;
    if (s->power_w.state==MACE_METRIC_VALID) {
        s->power_w.value=(float)cpu->metrics[1].value; s->power_scope=MACE_POWER_SCOPE_CPU_PACKAGE;
    }
    return true;
}
bool LhmReceiver_ApplyGpu(const MaceGpuAdapterInfoV2* a, MaceGpuTelemetryV2* s) {
    LhmFrame f; MaceMetricState state;
    snapshot(&f,&state);
    /* A missing helper does not invalidate independently known Windows capacity.
     * Never restore capacity for ambiguous PCI, malformed data or stale samples. */
    MaceMetricU64V2 capacity=s->video_memory_total_bytes;
    bool windows_capacity=s->provider_id==MACE_PROVIDER_WINDOWS;
    s->provider_id=MACE_PROVIDER_LHM;
    LegacyTelemetry_GpuState(s,state);
    if (state!=MACE_METRIC_VALID) {
        if (state==MACE_METRIC_NO_PROVIDER && windows_capacity &&
            capacity.state==MACE_METRIC_VALID && LegacyTelemetry_PciMatches(a)==1) {
            s->video_memory_total_bytes=capacity;
            s->provider_id=MACE_PROVIDER_WINDOWS;
        }
        return true;
    }
    uint32_t matches=LegacyTelemetry_PciMatches(a);
    if (matches==UINT32_MAX) { LegacyTelemetry_GpuState(s,MACE_METRIC_WARMING_UP); return true; }
    if (matches!=1) { LegacyTelemetry_GpuState(s,MACE_METRIC_UNSUPPORTED); return true; }
    const LhmDevice* gpu=NULL; bool ambiguous=false;
    for (uint32_t i=0;i<f.count;i++) {
        const LhmDevice* d=&f.devices[i];
        uint32_t vendor=d->kind==2 ? 0x10DEu : d->kind==3 ? 0x1002u : d->kind==4 ? 0x8086u : 0;
        if (vendor!=a->vendor_id) continue;
        if (d->identity==0) ambiguous=true;
        if (d->identity==2 && d->vendor==a->vendor_id && d->device==a->device_id &&
            d->subsystem==a->subsystem_id && d->revision==a->revision) gpu=d;
    }
    if (!gpu) { LegacyTelemetry_GpuState(s,ambiguous ? MACE_METRIC_UNSUPPORTED : MACE_METRIC_NO_PROVIDER); return true; }
    MaceMetricState states[7]; uint64_t now=GetTickCount64();
    for (unsigned i=0;i<7;i++) states[i]=LhmWire_State(&f,&gpu->metrics[i],false,now);
    if (gpu->metrics[1].state==1 && gpu->metrics[1].meaning==6) states[1]=MACE_METRIC_UNSUPPORTED;
    s->temperature_c=(MaceMetricF32V2){states[0]==0 ? (float)gpu->metrics[0].value : 0,states[0]};
    s->power_w=(MaceMetricF32V2){states[1]==0 ? (float)gpu->metrics[1].value : 0,states[1]};
    s->usage_percent=(MaceMetricF32V2){states[2]==0 ? (float)gpu->metrics[2].value : 0,states[2]};
    s->fan_rpm=(MaceMetricU32V2){states[3]==0 ? (uint32_t)gpu->metrics[3].value : 0,states[3]};
    s->fan_percent=(MaceMetricU32V2){states[4]==0 ? (uint32_t)gpu->metrics[4].value : 0,states[4]};
    s->video_memory_used_bytes=(MaceMetricU64V2){states[5]==0 ? (uint64_t)gpu->metrics[5].value : 0,states[5],0};
    s->video_memory_total_bytes=(MaceMetricU64V2){states[6]==0 ? (uint64_t)gpu->metrics[6].value : 0,states[6],0};
    if (states[1]==MACE_METRIC_VALID) s->power_scope=gpu->metrics[1].meaning==4 ? MACE_POWER_SCOPE_GPU_BOARD : MACE_POWER_SCOPE_GPU_CHIP;
    return true;
}
