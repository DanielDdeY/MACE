#ifndef MACE_RUNTIME_H
#define MACE_RUNTIME_H

#include <stdint.h>
#include "mace_status.h"

/* The runtime lock serializes differential provider state and prevents module
 * unloading while any telemetry call is using its functions or handles. */
int MaceRuntime_Initialize(uint32_t cpu_vendor);
void MaceRuntime_Shutdown(void);
MaceStatus MaceRuntime_BeginPoll(void);
void MaceRuntime_EndPoll(void);

/* Resource creation remains legal without telemetry initialization. The
 * barrier orders it with CP_ShutdownTelemetry's close-all operation. */
void MaceRuntime_BeginResourceCreation(void);
void MaceRuntime_EndResourceCreation(void);

#endif
