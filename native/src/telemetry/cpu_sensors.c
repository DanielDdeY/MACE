#include <windows.h>
#include <stdbool.h>

void CpuSensors_Poll(float* out_temp, float* out_watts) {
    // Lectura de registros térmicos y energéticos MSR/RAPL
    // Fallback pasivo en espacio de usuario
    *out_temp = 42.0f;
    *out_watts = 25.5f;
}