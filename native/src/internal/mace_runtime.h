#ifndef MACE_RUNTIME_H
#define MACE_RUNTIME_H

#include <stdint.h>
#include "mace_status.h"

/* Poll lock owns GetSystemTimes differential state and cache readers.
 * Lifecycle lock owns worker start/join; provider IO never holds the poll lock.
 * Shutdown waits at most 4000 ms in user-space; incomplete native workers retain
 * their resources and the DLL is pinned until process exit. Init returns BUSY
 * until cleanup really completes. No unsafe thread termination. */
int MaceRuntime_Initialize(uint32_t cpu_vendor);
void MaceRuntime_Shutdown(void);
MaceStatus MaceRuntime_BeginPoll(void);
void MaceRuntime_EndPoll(void);

/* Resource creation remains legal without telemetry initialization. The
 * barrier orders it with CP_ShutdownTelemetry's close-all operation. */
void MaceRuntime_BeginResourceCreation(void);
void MaceRuntime_EndResourceCreation(void);

#endif
