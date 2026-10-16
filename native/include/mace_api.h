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

/* Phase 2: capacities are bytes for single records and entries for the array.
 * A NULL array and capacity 0 query the required count. Short buffers are not
 * modified. The LUID is valid only for the current Windows adapter session. */
CP_API MaceStatus CP_EnumerateGpuAdapters(MaceGpuAdapterInfoV2* out_adapters,
                                          uint32_t capacity,
                                          uint32_t* out_count);
CP_API MaceStatus CP_PollCpuTelemetryV2(MaceCpuTelemetryV2* out_data,
                                       uint32_t out_capacity);
CP_API MaceStatus CP_PollGpuTelemetryV2(const MaceAdapterLuidV2* luid,
                                       MaceGpuTelemetryV2* out_data,
                                       uint32_t out_capacity);
CP_API MaceStatus CP_PollSystemMemoryV2(MaceSystemMemoryV2* out_data,
                                       uint32_t out_capacity);

// Ciclo de vida del subsistema de telemetría
CP_API int  CP_InitializeTelemetry(void);
CP_API void CP_ShutdownTelemetry(void);
CP_API int  CP_PollTelemetry(TelemetryData* out_data);

// Gestión y consulta de procesos (Userland)
CP_API int  CP_EnumerateUserlandProcesses(ProcessData* out_buffer, uint32_t max_count, uint32_t* out_count);
CP_API int  CP_TerminateProcess(uint32_t pid);

/* Phase 3: capacity is in records for the array, bytes for an identity, and
 * UTF-16 code units for the path (including NUL). A short output buffer is
 * never modified. out_win32_error is zero unless Windows itself failed. */
CP_API MaceStatus CP_EnumerateUserlandProcessesV2(MaceProcessInfoV2* out_entries,
    uint32_t capacity, uint32_t* out_required_count, uint32_t* out_skipped_count,
    uint32_t* out_win32_error);
CP_API MaceStatus CP_GetProcessIdentityV2(uint32_t pid,
    MaceProcessIdentityV2* out_identity, uint32_t out_capacity,
    uint32_t* out_win32_error);
CP_API MaceStatus CP_GetProcessImagePathV2(const MaceProcessIdentityV2* identity,
    wchar_t* out_path, uint32_t capacity_chars, uint32_t* out_required_chars,
    uint32_t* out_win32_error);
CP_API MaceStatus CP_TerminateProcessV2(const MaceProcessIdentityV2* identity,
    uint32_t* out_win32_error);

// Hooks para extensiones futuras
CP_API int  CP_CheckProcessResponsiveness(uint32_t pid);
CP_API void* CP_InitSharedTelemetryBuffer(const wchar_t* mapping_name, uint32_t buffer_size);

#ifdef __cplusplus
}
#endif

#endif // MACE_API_H
