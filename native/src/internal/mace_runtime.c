#include "mace_runtime.h"
#include <windows.h>
#include <stdbool.h>
#include "../telemetry/cpu_sensors.h"
#include "../telemetry/gpu_providers.h"
#include "../telemetry/legacy_telemetry.h"
#include "../telemetry/lhm_receiver.h"
#include "../extensions/blackbox_buffer.h"

static SRWLOCK runtime_lock = SRWLOCK_INIT;
static SRWLOCK lifecycle_lock = SRWLOCK_INIT;
/* Only short control operations under this lock; never held while joining. */
static SRWLOCK shutdown_lock = SRWLOCK_INIT;
static HANDLE shutdown_worker;
static volatile LONG shutdown_requested;
static bool initialized;

static bool pin_runtime(void) {
    HMODULE module=NULL;
    return GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_PIN,
        (LPCWSTR)(uintptr_t)&MaceRuntime_Initialize,&module)!=0;
}

int MaceRuntime_Initialize(uint32_t cpu_vendor) {
    /* Never queue behind shutdown's helper join or vendor SDK teardown. */
    if (!TryAcquireSRWLockExclusive(&shutdown_lock)) return MACE_E_BUSY;
    if (shutdown_worker) {
        if (WaitForSingleObject(shutdown_worker,0)!=WAIT_OBJECT_0) {
            ReleaseSRWLockExclusive(&shutdown_lock); return MACE_E_BUSY;
        }
        CloseHandle(shutdown_worker); shutdown_worker=NULL;
        InterlockedExchange(&shutdown_requested,0);
    }
    if (shutdown_requested || !TryAcquireSRWLockExclusive(&lifecycle_lock)) {
        ReleaseSRWLockExclusive(&shutdown_lock); return MACE_E_BUSY;
    }
    AcquireSRWLockExclusive(&runtime_lock);
    if (!initialized) {
        /* Timed-out native workers can outlive CP_ShutdownTelemetry. Pin their
         * code for process lifetime even if a C consumer calls FreeLibrary. */
        if (!pin_runtime()) {
            ReleaseSRWLockExclusive(&runtime_lock);
            ReleaseSRWLockExclusive(&lifecycle_lock);
            ReleaseSRWLockExclusive(&shutdown_lock);
            return MACE_E_WINDOWS_ERROR;
        }
        LegacyTelemetry_Start(cpu_vendor);
        LhmReceiver_Start();
        initialized = true;
    }
    ReleaseSRWLockExclusive(&runtime_lock);
    ReleaseSRWLockExclusive(&lifecycle_lock);
    ReleaseSRWLockExclusive(&shutdown_lock);
    return 0; /* Legacy ABI: optional providers may be absent. */
}

static DWORD WINAPI finish_shutdown(void* unused) {
    (void)unused;
    AcquireSRWLockExclusive(&lifecycle_lock);
    AcquireSRWLockExclusive(&runtime_lock);
    bool was_initialized=initialized;
    initialized = false;
    ReleaseSRWLockExclusive(&runtime_lock);
    /* Polling returns NOT_INITIALIZED while worker shutdown joins here. */
    if (was_initialized) {
        LhmReceiver_RequestStop(); LegacyTelemetry_RequestStop();
        uint64_t deadline=GetTickCount64()+3000;
        bool receiver_done=LhmReceiver_StopUntil(deadline);
        bool gpu_done=LegacyTelemetry_StopUntil(deadline);
        if (!receiver_done || !gpu_done)
            OutputDebugStringW(L"MACE: shutdown deadline; workers quarantined, reinitialization busy.\n");
        /* A vendor call cannot safely be interrupted. Reap in bounded slices,
         * retaining all worker-owned state and forbidding a second generation.
         * The public caller never waits on this loop beyond its own deadline. */
        while (!receiver_done || !gpu_done) {
            deadline=GetTickCount64()+100;
            if (!receiver_done) receiver_done=LhmReceiver_StopUntil(deadline);
            if (!gpu_done) gpu_done=LegacyTelemetry_StopUntil(deadline);
            if (!receiver_done || !gpu_done) Sleep(1);
        }
    }
    Blackbox_CloseAll();
    SharedBuffer_CloseAll();
    ReleaseSRWLockExclusive(&lifecycle_lock);
    return 0;
}

void MaceRuntime_Shutdown(void) {
    /* Overall public wait budget, including competing shutdown callers. Windows
     * scheduling/kernel calls are not a hard real-time guarantee. */
    uint64_t deadline=GetTickCount64()+4000;
    while (!TryAcquireSRWLockExclusive(&shutdown_lock)) {
        if (GetTickCount64()>=deadline) return;
        Sleep(1);
    }
    if (shutdown_worker && WaitForSingleObject(shutdown_worker,0)==WAIT_OBJECT_0) {
        CloseHandle(shutdown_worker); shutdown_worker=NULL;
    }
    InterlockedExchange(&shutdown_requested,1);
    /* Shutdown is also legal before init (resource-only clients). Its cleanup
     * thread needs the same code-lifetime protection as telemetry workers. */
    if (!shutdown_worker) {
        /* Signal promptly even if resource cleanup later queues on its barrier,
         * or allocation of the coordinator fails. No other reaper is active. */
        LhmReceiver_RequestStop(); LegacyTelemetry_RequestStop();
        if (pin_runtime()) shutdown_worker=CreateThread(NULL,0,finish_shutdown,NULL,0,NULL);
    }
    HANDLE waiter=NULL;
    if (shutdown_worker)
        DuplicateHandle(GetCurrentProcess(),shutdown_worker,GetCurrentProcess(),&waiter,SYNCHRONIZE,FALSE,0);
    else OutputDebugStringW(L"MACE: shutdown coordinator unavailable; initialization remains disabled.\n");
    ReleaseSRWLockExclusive(&shutdown_lock);
    if (waiter) {
        uint64_t now=GetTickCount64();
        WaitForSingleObject(waiter,now<deadline ? (DWORD)(deadline-now) : 0);
        CloseHandle(waiter);
    }
}

MaceStatus MaceRuntime_BeginPoll(void) {
    if (InterlockedCompareExchange(&shutdown_requested,0,0)) return MACE_E_NOT_INITIALIZED;
    AcquireSRWLockExclusive(&runtime_lock);
    if (!initialized || InterlockedCompareExchange(&shutdown_requested,0,0)) {
        ReleaseSRWLockExclusive(&runtime_lock);
        return MACE_E_NOT_INITIALIZED;
    }
    return MACE_OK;
}

void MaceRuntime_EndPoll(void) {
    ReleaseSRWLockExclusive(&runtime_lock);
}

void MaceRuntime_BeginResourceCreation(void) {
    AcquireSRWLockExclusive(&lifecycle_lock);
}

void MaceRuntime_EndResourceCreation(void) {
    ReleaseSRWLockExclusive(&lifecycle_lock);
}
