#ifndef MACE_LHM_RECEIVER_H
#define MACE_LHM_RECEIVER_H
#include <stdbool.h>
#include "mace_types_v2.h"
bool LhmReceiver_Start(void);
void LhmReceiver_RequestStop(void);
bool LhmReceiver_StopUntil(uint64_t deadline_ms);
bool LhmReceiver_ApplyCpu(MaceCpuTelemetryV2* sample);
bool LhmReceiver_ApplyGpu(const MaceGpuAdapterInfoV2* adapter, MaceGpuTelemetryV2* sample);
#endif
