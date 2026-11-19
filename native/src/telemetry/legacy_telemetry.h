#ifndef MACE_LEGACY_TELEMETRY_H
#define MACE_LEGACY_TELEMETRY_H
#include <stdbool.h>
#include "mace_types_v2.h"
bool LegacyTelemetry_Start(uint32_t vendor);
void LegacyTelemetry_RequestStop(void);
bool LegacyTelemetry_StopUntil(uint64_t deadline_ms);
void LegacyTelemetry_Gpu(const MaceGpuAdapterInfoV2* adapter, MaceGpuTelemetryV2* sample);
/* UINT32_MAX means inventory unavailable, never permission to associate. */
uint32_t LegacyTelemetry_PciMatches(const MaceGpuAdapterInfoV2* adapter);
void LegacyTelemetry_GpuState(MaceGpuTelemetryV2* sample, MaceMetricState state);
#endif
