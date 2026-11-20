#ifndef MACE_GPU_PROVIDERS_H
#define MACE_GPU_PROVIDERS_H

#include "mace_types_v2.h"

void GpuProviders_Init(void);
void GpuProviders_Shutdown(void);
void GpuProviders_Poll(const MaceGpuAdapterInfoV2* adapter,
                       MaceGpuTelemetryV2* out);

#endif
