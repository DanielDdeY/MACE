#ifndef MACE_TYPES_V2_H
#define MACE_TYPES_V2_H

#include <stdint.h>
#include <wchar.h>
#include "mace_status.h"

#define MACE_V2_CONTRACT_VERSION 2u

/* Campos reservados deben ser cero al escribir y al llamar a la DLL. */
#pragma pack(push, 8)

typedef struct {
    float value;
    MaceMetricState state;
} MaceMetricF32V2;

typedef struct {
    uint32_t value;
    MaceMetricState state;
} MaceMetricU32V2;

typedef struct {
    uint64_t value;
    MaceMetricState state;
    uint32_t reserved;
} MaceMetricU64V2;

/* Consulta disponible antes de inicializar telemetría. */
typedef struct {
    uint32_t struct_size;
    uint32_t version;
    uint32_t abi_major;
    uint32_t abi_minor;
    uint32_t process_data_size;
    uint32_t telemetry_data_size;
    uint32_t blackbox_header_size;
    uint32_t wchar_size;
    uint32_t pointer_size;
    uint32_t reserved0;
    uint64_t implemented_capabilities;
    uint32_t build_major;
    uint32_t build_minor;
    uint32_t build_patch;
    uint32_t reserved1;
} MaceAbiInfoV2;

/* LUID identifica un adaptador en la sesión de Windows; no persiste tras reiniciar. */
typedef struct {
    uint32_t low_part;
    int32_t high_part;
} MaceAdapterLuidV2;

typedef struct {
    uint32_t struct_size;
    uint32_t version;
    MaceAdapterLuidV2 luid;
    uint32_t vendor_id;
    uint32_t device_id;
    uint32_t subsystem_id;
    uint32_t revision;
    uint32_t dxgi_flags;
    uint32_t adapter_index;
    wchar_t description[128];
} MaceGpuAdapterInfoV2;

typedef struct {
    uint32_t struct_size;
    uint32_t version;
    uint64_t timestamp_monotonic_ns;
    uint32_t cpu_vendor;
    MaceProviderId provider_id;
    MaceMetricF32V2 temperature_c;
    MaceMetricF32V2 power_w;
    MaceMetricF32V2 usage_percent;
    MacePowerScope power_scope;
    uint32_t reserved;
} MaceCpuTelemetryV2;

typedef struct {
    uint32_t struct_size;
    uint32_t version;
    uint64_t timestamp_monotonic_ns;
    MaceAdapterLuidV2 luid;
    MaceProviderId provider_id;
    MacePowerScope power_scope;
    MaceMetricF32V2 temperature_c;
    MaceMetricF32V2 power_w;
    MaceMetricF32V2 usage_percent;
    MaceMetricU32V2 fan_rpm;
    MaceMetricU32V2 fan_percent;
    MaceMetricU64V2 video_memory_used_bytes;
    MaceMetricU64V2 video_memory_total_bytes;
} MaceGpuTelemetryV2;

typedef struct {
    uint32_t struct_size;
    uint32_t version;
    uint64_t timestamp_monotonic_ns;
    MaceMetricU64V2 total_bytes;
    MaceMetricU64V2 available_bytes;
} MaceSystemMemoryV2;

typedef struct {
    uint32_t pid;
    uint32_t reserved;
    uint64_t creation_time_100ns_utc;
} MaceProcessIdentityV2;

/* Inventario de procesos seguro; la ruta completa se consulta aparte. */
typedef struct {
    uint32_t struct_size;
    uint32_t version;
    MaceProcessIdentityV2 identity;
    MaceMetricU64V2 working_set_bytes;
    MaceMetricU64V2 private_bytes;
    wchar_t name[260];
} MaceProcessInfoV2;

typedef struct {
    uint32_t struct_size;
    uint32_t version;
    MaceProcessIdentityV2 identity;
    uint64_t timestamp_monotonic_ns;
    MaceMetricU64V2 working_set_bytes;
    MaceMetricU64V2 private_bytes;
} MaceProcessSampleV2;

typedef struct {
    uint32_t struct_size;
    uint32_t version;
    MaceProcessIdentityV2 identity;
    uint32_t windows_examined;
    uint32_t windows_hung;
    uint32_t outcome;
    uint32_t win32_error;
} MaceResponsivenessV2;

#pragma pack(pop)

#endif /* MACE_TYPES_V2_H */
