#ifndef MACE_AMD_RYZEN_PROVIDER_H
#define MACE_AMD_RYZEN_PROVIDER_H

#include "mace_types_v2.h"

#ifdef __cplusplus
extern "C" {
#endif

/* Solo se enlaza cuando se compila con el SDK oficial instalado fuera del repo. */
void AmdRyzenProvider_Init(void);
void AmdRyzenProvider_Shutdown(void);
void AmdRyzenProvider_Poll(MaceCpuTelemetryV2* sample);

#ifdef __cplusplus
}
#endif

#endif
