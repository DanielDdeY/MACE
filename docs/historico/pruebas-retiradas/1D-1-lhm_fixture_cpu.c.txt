/* Keep actual GetSystemTimes behavior, disable all hardware initialization. */
#define CpuSensors_Init FixtureCpuSensors_RealInitUnused
#include "../src/telemetry/cpu_sensors.c"
#undef CpuSensors_Init
bool CpuSensors_Init(void) { return false; }
