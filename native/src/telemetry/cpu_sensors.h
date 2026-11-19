#ifndef MACE_CPU_SENSORS_H
#define MACE_CPU_SENSORS_H
#include <stdbool.h>
/* Windows usage only. Temperature and power belong to the helper. */
void CpuSensors_Shutdown(void);
bool CpuSensors_UsageReady(void);
void CpuSensors_Poll(float* out_usage);
#endif
