#ifndef MACE_CPU_SENSORS_H
#define MACE_CPU_SENSORS_H

#include <stdbool.h>

/*
 * Interfaz interna del modulo de sensores de CPU.
 *
 * Tanto cpu_sensors.c (definicion) como mace_api.c (uso) incluyen este header:
 * asi el compilador verifica que las firmas coincidan. Declararlas a mano con
 * 'extern' en cada archivo permitia que una llamada con menos argumentos
 * compilara sin aviso y corrompiera memoria en tiempo de ejecucion.
 */

/** Intenta cargar WinRing0 una sola vez. Devuelve true si los MSR son legibles. */
bool CpuSensors_Init(void);

/** Libera WinRing0 si se cargo. Es seguro llamarla aunque Init haya fallado. */
void CpuSensors_Shutdown(void);

/** true si temperatura y energia provienen de sensores reales (WinRing0 + CPU Intel). */
bool CpuSensors_HardwareAvailable(void);

/**
 * Sondeo unificado. El uso de CPU siempre es real (GetSystemTimes); temperatura y
 * energia valen 0 cuando CpuSensors_HardwareAvailable() es false.
 */
void CpuSensors_Poll(float* out_temp, float* out_watts, float* out_usage);

#endif // MACE_CPU_SENSORS_H
