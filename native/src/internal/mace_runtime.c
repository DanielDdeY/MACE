#include "mace_runtime.h"
#include <windows.h>
#include <stdbool.h>
#include "../telemetry/cpu_sensors.h"
#include "../telemetry/gpu_providers.h"
#include "../telemetry/legacy_telemetry.h"
#include "../telemetry/lhm_receiver.h"
#include "../extensions/blackbox_buffer.h"
#ifdef MACE_HAS_RYZEN_SDK
#include "../telemetry/amd_ryzen_provider.h"
#endif

static SRWLOCK runtime_lock = SRWLOCK_INIT;
static SRWLOCK lifecycle_lock = SRWLOCK_INIT;
static bool initialized;

int MaceRuntime_Initialize(uint32_t cpu_vendor) {
    /* Never queue behind shutdown's helper join or vendor SDK teardown. */
    if (!TryAcquireSRWLockExclusive(&lifecycle_lock)) return MACE_E_BUSY;
    AcquireSRWLockExclusive(&runtime_lock);
    if (!initialized) {
        LegacyTelemetry_Start(cpu_vendor);
        LhmReceiver_Start();
        initialized = true;
    }
    ReleaseSRWLockExclusive(&runtime_lock);
    ReleaseSRWLockExclusive(&lifecycle_lock);
    return 0; /* Legacy ABI: optional providers may be absent. */
}

void MaceRuntime_Shutdown(void) {
    AcquireSRWLockExclusive(&lifecycle_lock);
    AcquireSRWLockExclusive(&runtime_lock);
    bool was_initialized=initialized;
    initialized = false;
    ReleaseSRWLockExclusive(&runtime_lock);
    /* Polling returns NOT_INITIALIZED while worker shutdown joins here. */
    if (was_initialized) { LhmReceiver_Stop(); LegacyTelemetry_Stop(); }
    Blackbox_CloseAll();
    SharedBuffer_CloseAll();
    ReleaseSRWLockExclusive(&lifecycle_lock);
}

MaceStatus MaceRuntime_BeginPoll(void) {
    AcquireSRWLockExclusive(&runtime_lock);
    if (!initialized) {
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
