#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <wchar.h>

#include "mace_api.h"
#include "mace_extensions.h"
#include "mace_version.h"

/* Estas aserciones pertenecen a la DLL, no solo al ejecutable de pruebas. */
_Static_assert(sizeof(void*) == 8, "mace_native requires x64");
_Static_assert(sizeof(wchar_t) == 2, "Windows FFM requires UTF-16 wchar_t");
_Static_assert(sizeof(int) == 4, "C int must match JAVA_INT");
_Static_assert(sizeof(float) == 4, "C float must match JAVA_FLOAT");
_Static_assert(sizeof(ProcessData) == 1584 && _Alignof(ProcessData) == 8,
               "ProcessData legacy ABI changed");
_Static_assert(offsetof(ProcessData, pid) == 0 &&
               offsetof(ProcessData, name) == 4 &&
               offsetof(ProcessData, path) == 524 &&
               offsetof(ProcessData, working_set_bytes) == 1568 &&
               offsetof(ProcessData, private_bytes) == 1576,
               "ProcessData legacy offsets changed");
_Static_assert(sizeof(TelemetryData) == 28 && _Alignof(TelemetryData) == 4,
               "TelemetryData legacy ABI changed");
_Static_assert(offsetof(TelemetryData, cpu_temp) == 0 &&
               offsetof(TelemetryData, cpu_watts) == 4 &&
               offsetof(TelemetryData, gpu_temp) == 8 &&
               offsetof(TelemetryData, gpu_watts) == 12 &&
               offsetof(TelemetryData, gpu_fan_rpm) == 16 &&
               offsetof(TelemetryData, is_gpu_available) == 20 &&
               offsetof(TelemetryData, is_cpu_sensor_available) == 21 &&
               offsetof(TelemetryData, _padding) == 22 &&
               offsetof(TelemetryData, cpu_usage) == 24,
               "TelemetryData legacy offsets changed");
_Static_assert(sizeof(BlackboxSharedHeader) == 16 &&
               _Alignof(BlackboxSharedHeader) == 4 &&
               offsetof(BlackboxSharedHeader, magic_header) == 0 &&
               offsetof(BlackboxSharedHeader, ring_buffer_capacity) == 4 &&
               offsetof(BlackboxSharedHeader, write_cursor) == 8 &&
               offsetof(BlackboxSharedHeader, record_count) == 12,
               "BlackboxSharedHeader legacy ABI changed");
_Static_assert(sizeof(MaceAbiInfoV2) == 64 && _Alignof(MaceAbiInfoV2) == 8,
               "MaceAbiInfoV2 ABI changed");

CP_API MaceStatus CP_GetAbiInfo(MaceAbiInfoV2* out_info,
                               uint32_t out_capacity,
                               uint32_t* out_required_size) {
    if (out_required_size == NULL) {
        return MACE_E_INVALID_ARGUMENT;
    }

    *out_required_size = (uint32_t)sizeof(MaceAbiInfoV2);
    if (out_capacity < sizeof(MaceAbiInfoV2)) {
        return MACE_E_BUFFER_TOO_SMALL;
    }
    if (out_info == NULL) {
        return MACE_E_INVALID_ARGUMENT;
    }

    const MaceAbiInfoV2 info = {
        .struct_size = (uint32_t)sizeof(MaceAbiInfoV2),
        .version = MACE_V2_CONTRACT_VERSION,
        .abi_major = MACE_ABI_MAJOR,
        .abi_minor = MACE_ABI_MINOR,
        .process_data_size = (uint32_t)sizeof(ProcessData),
        .telemetry_data_size = (uint32_t)sizeof(TelemetryData),
        .blackbox_header_size = (uint32_t)sizeof(BlackboxSharedHeader),
        .wchar_size = (uint32_t)sizeof(wchar_t),
        .pointer_size = (uint32_t)sizeof(void*),
        .reserved0 = 0,
        .implemented_capabilities = MACE_CAP_CPU_USAGE |
            MACE_CAP_CPU_TEMPERATURE | MACE_CAP_CPU_POWER |
            MACE_CAP_GPU_INVENTORY | MACE_CAP_GPU_TEMPERATURE |
            MACE_CAP_GPU_POWER | MACE_CAP_GPU_USAGE |
            MACE_CAP_GPU_FAN_RPM | MACE_CAP_GPU_FAN_PERCENT |
            MACE_CAP_GPU_MEMORY | MACE_CAP_SYSTEM_MEMORY,
        .build_major = MACE_BUILD_VERSION_MAJOR,
        .build_minor = MACE_BUILD_VERSION_MINOR,
        .build_patch = MACE_BUILD_VERSION_PATCH,
        .reserved1 = 0
    };

    memcpy(out_info, &info, sizeof(info));
    return MACE_OK;
}
