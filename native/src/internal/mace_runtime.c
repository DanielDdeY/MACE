#include "mace_runtime.h"
#include <windows.h>
#include <stdbool.h>
#include "../telemetry/cpu_sensors.h"
#include "../telemetry/gpu_providers.h"
#include "../extensions/blackbox_buffer.h"
#ifdef MACE_HAS_RYZEN_SDK
#include "../telemetry/amd_ryzen_provider.h"
#endif

static SRWLOCK runtime_lock = SRWLOCK_INIT;
static bool initialized;

int MaceRuntime_Initialize(uint32_t cpu_vendor) {
    AcquireSRWLockExclusive(&runtime_lock);
    if (!initialized) {
        CpuSensors_Init();
#ifdef MACE_HAS_RYZEN_SDK
        if (cpu_vendor == MACE_CPU_VENDOR_AMD) AmdRyzenProvider_Init();
#else
        (void)cpu_vendor;
#endif
        GpuProviders_Init();
        initialized = true;
    }
    ReleaseSRWLockExclusive(&runtime_lock);
    return 0; /* Legacy ABI: optional providers may be absent. */
}

void MaceRuntime_Shutdown(void) {
    AcquireSRWLockExclusive(&runtime_lock);
    if (initialized) {
        GpuProviders_Shutdown();
#ifdef MACE_HAS_RYZEN_SDK
        AmdRyzenProvider_Shutdown();
#endif
        CpuSensors_Shutdown();
        initialized = false;
    }
    Blackbox_CloseAll();
    SharedBuffer_CloseAll();
    ReleaseSRWLockExclusive(&runtime_lock);
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
    AcquireSRWLockExclusive(&runtime_lock);
}

void MaceRuntime_EndResourceCreation(void) {
    ReleaseSRWLockExclusive(&runtime_lock);
}
