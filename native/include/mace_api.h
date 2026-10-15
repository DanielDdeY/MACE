#ifndef MACE_API_H
#define MACE_API_H

#ifdef MACE_EXPORTS
    #define CP_API __declspec(dllexport)
#else
    #define CP_API __declspec(dllimport)
#endif

#include "mace_types.h"
#include "mace_types_v2.h"
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Disponible incluso antes de CP_InitializeTelemetry. Un out_info nulo con
 * out_capacity=0 permite consultar el tamaño requerido. No hay escritura
 * parcial si la capacidad es insuficiente. */
CP_API MaceStatus CP_GetAbiInfo(MaceAbiInfoV2* out_info,
                               uint32_t out_capacity,
                               uint32_t* out_required_size);

// Ciclo de vida del subsistema de telemetría
CP_API int  CP_InitializeTelemetry(void);
CP_API void CP_ShutdownTelemetry(void);
CP_API int  CP_PollTelemetry(TelemetryData* out_data);

// Gestión y consulta de procesos (Userland)
CP_API int  CP_EnumerateUserlandProcesses(ProcessData* out_buffer, uint32_t max_count, uint32_t* out_count);
CP_API int  CP_TerminateProcess(uint32_t pid);

// Hooks para extensiones futuras
CP_API int  CP_CheckProcessResponsiveness(uint32_t pid);
CP_API void* CP_InitSharedTelemetryBuffer(const wchar_t* mapping_name, uint32_t buffer_size);

#ifdef __cplusplus
}
#endif

#endif // MACE_API_H
