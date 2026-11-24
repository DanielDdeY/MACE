#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <wchar.h>

#include "mace_types.h"
#include "mace_types_v2.h"
#include "mace_extensions.h"

#define CHECK_SIZE(type, expected) _Static_assert(sizeof(type) == (expected), #type " size changed")
#define CHECK_ALIGN(type, expected) _Static_assert(_Alignof(type) == (expected), #type " alignment changed")
#define CHECK_OFFSET(type, field, expected) _Static_assert(offsetof(type, field) == (expected), #type "." #field " offset changed")

_Static_assert(sizeof(void*) == 8, "MACE requires a 64-bit build");
_Static_assert(sizeof(wchar_t) == 2, "FFM assumes Windows UTF-16 wchar_t");
_Static_assert(sizeof(int) == 4, "FFM maps C int to JAVA_INT");
_Static_assert(sizeof(float) == 4, "FFM maps float to JAVA_FLOAT");

CHECK_SIZE(ProcessData, 1584);
CHECK_ALIGN(ProcessData, 8);
CHECK_OFFSET(ProcessData, pid, 0);
CHECK_OFFSET(ProcessData, name, 4);
CHECK_OFFSET(ProcessData, path, 524);
CHECK_OFFSET(ProcessData, working_set_bytes, 1568);
CHECK_OFFSET(ProcessData, private_bytes, 1576);

CHECK_SIZE(TelemetryData, 28);
CHECK_ALIGN(TelemetryData, 4);
CHECK_OFFSET(TelemetryData, cpu_temp, 0);
CHECK_OFFSET(TelemetryData, cpu_watts, 4);
CHECK_OFFSET(TelemetryData, gpu_temp, 8);
CHECK_OFFSET(TelemetryData, gpu_watts, 12);
CHECK_OFFSET(TelemetryData, gpu_fan_rpm, 16);
CHECK_OFFSET(TelemetryData, is_gpu_available, 20);
CHECK_OFFSET(TelemetryData, is_cpu_sensor_available, 21);
CHECK_OFFSET(TelemetryData, _padding, 22);
CHECK_OFFSET(TelemetryData, cpu_usage, 24);

CHECK_SIZE(BlackboxSharedHeader, 16);
CHECK_ALIGN(BlackboxSharedHeader, 4);
CHECK_OFFSET(BlackboxSharedHeader, magic_header, 0);
CHECK_OFFSET(BlackboxSharedHeader, ring_buffer_capacity, 4);
CHECK_OFFSET(BlackboxSharedHeader, write_cursor, 8);
CHECK_OFFSET(BlackboxSharedHeader, record_count, 12);

CHECK_SIZE(MaceMetricF32V2, 8);
CHECK_ALIGN(MaceMetricF32V2, 4);
CHECK_OFFSET(MaceMetricF32V2, value, 0);
CHECK_OFFSET(MaceMetricF32V2, state, 4);
CHECK_SIZE(MaceMetricU32V2, 8);
CHECK_ALIGN(MaceMetricU32V2, 4);
CHECK_OFFSET(MaceMetricU32V2, value, 0);
CHECK_OFFSET(MaceMetricU32V2, state, 4);
CHECK_SIZE(MaceMetricU64V2, 16);
CHECK_ALIGN(MaceMetricU64V2, 8);
CHECK_OFFSET(MaceMetricU64V2, value, 0);
CHECK_OFFSET(MaceMetricU64V2, state, 8);
CHECK_OFFSET(MaceMetricU64V2, reserved, 12);
CHECK_SIZE(MaceAbiInfoV2, 64);
CHECK_ALIGN(MaceAbiInfoV2, 8);
CHECK_OFFSET(MaceAbiInfoV2, struct_size, 0);
CHECK_OFFSET(MaceAbiInfoV2, version, 4);
CHECK_OFFSET(MaceAbiInfoV2, abi_major, 8);
CHECK_OFFSET(MaceAbiInfoV2, abi_minor, 12);
CHECK_OFFSET(MaceAbiInfoV2, process_data_size, 16);
CHECK_OFFSET(MaceAbiInfoV2, telemetry_data_size, 20);
CHECK_OFFSET(MaceAbiInfoV2, blackbox_header_size, 24);
CHECK_OFFSET(MaceAbiInfoV2, wchar_size, 28);
CHECK_OFFSET(MaceAbiInfoV2, pointer_size, 32);
CHECK_OFFSET(MaceAbiInfoV2, reserved0, 36);
CHECK_OFFSET(MaceAbiInfoV2, implemented_capabilities, 40);
CHECK_OFFSET(MaceAbiInfoV2, build_major, 48);
CHECK_OFFSET(MaceAbiInfoV2, build_minor, 52);
CHECK_OFFSET(MaceAbiInfoV2, build_patch, 56);
CHECK_OFFSET(MaceAbiInfoV2, reserved1, 60);
CHECK_SIZE(MaceAdapterLuidV2, 8);
CHECK_ALIGN(MaceAdapterLuidV2, 4);
CHECK_OFFSET(MaceAdapterLuidV2, low_part, 0);
CHECK_OFFSET(MaceAdapterLuidV2, high_part, 4);
CHECK_SIZE(MaceGpuAdapterInfoV2, 296);
CHECK_ALIGN(MaceGpuAdapterInfoV2, 4);
CHECK_OFFSET(MaceGpuAdapterInfoV2, struct_size, 0);
CHECK_OFFSET(MaceGpuAdapterInfoV2, version, 4);
CHECK_OFFSET(MaceGpuAdapterInfoV2, luid, 8);
CHECK_OFFSET(MaceGpuAdapterInfoV2, vendor_id, 16);
CHECK_OFFSET(MaceGpuAdapterInfoV2, device_id, 20);
CHECK_OFFSET(MaceGpuAdapterInfoV2, subsystem_id, 24);
CHECK_OFFSET(MaceGpuAdapterInfoV2, revision, 28);
CHECK_OFFSET(MaceGpuAdapterInfoV2, dxgi_flags, 32);
CHECK_OFFSET(MaceGpuAdapterInfoV2, adapter_index, 36);
CHECK_OFFSET(MaceGpuAdapterInfoV2, description, 40);
CHECK_SIZE(MaceCpuTelemetryV2, 56);
CHECK_ALIGN(MaceCpuTelemetryV2, 8);
CHECK_OFFSET(MaceCpuTelemetryV2, struct_size, 0);
CHECK_OFFSET(MaceCpuTelemetryV2, version, 4);
CHECK_OFFSET(MaceCpuTelemetryV2, timestamp_monotonic_ns, 8);
CHECK_OFFSET(MaceCpuTelemetryV2, cpu_vendor, 16);
CHECK_OFFSET(MaceCpuTelemetryV2, provider_id, 20);
CHECK_OFFSET(MaceCpuTelemetryV2, temperature_c, 24);
CHECK_OFFSET(MaceCpuTelemetryV2, power_w, 32);
CHECK_OFFSET(MaceCpuTelemetryV2, usage_percent, 40);
CHECK_OFFSET(MaceCpuTelemetryV2, power_scope, 48);
CHECK_OFFSET(MaceCpuTelemetryV2, reserved, 52);
CHECK_SIZE(MaceGpuTelemetryV2, 104);
CHECK_ALIGN(MaceGpuTelemetryV2, 8);
CHECK_OFFSET(MaceGpuTelemetryV2, struct_size, 0);
CHECK_OFFSET(MaceGpuTelemetryV2, version, 4);
CHECK_OFFSET(MaceGpuTelemetryV2, timestamp_monotonic_ns, 8);
CHECK_OFFSET(MaceGpuTelemetryV2, luid, 16);
CHECK_OFFSET(MaceGpuTelemetryV2, provider_id, 24);
CHECK_OFFSET(MaceGpuTelemetryV2, power_scope, 28);
CHECK_OFFSET(MaceGpuTelemetryV2, temperature_c, 32);
CHECK_OFFSET(MaceGpuTelemetryV2, power_w, 40);
CHECK_OFFSET(MaceGpuTelemetryV2, usage_percent, 48);
CHECK_OFFSET(MaceGpuTelemetryV2, fan_rpm, 56);
CHECK_OFFSET(MaceGpuTelemetryV2, fan_percent, 64);
CHECK_OFFSET(MaceGpuTelemetryV2, video_memory_used_bytes, 72);
CHECK_OFFSET(MaceGpuTelemetryV2, video_memory_total_bytes, 88);
CHECK_SIZE(MaceSystemMemoryV2, 48);
CHECK_ALIGN(MaceSystemMemoryV2, 8);
CHECK_OFFSET(MaceSystemMemoryV2, struct_size, 0);
CHECK_OFFSET(MaceSystemMemoryV2, version, 4);
CHECK_OFFSET(MaceSystemMemoryV2, timestamp_monotonic_ns, 8);
CHECK_OFFSET(MaceSystemMemoryV2, total_bytes, 16);
CHECK_OFFSET(MaceSystemMemoryV2, available_bytes, 32);
CHECK_SIZE(MaceProcessIdentityV2, 16);
CHECK_ALIGN(MaceProcessIdentityV2, 8);
CHECK_OFFSET(MaceProcessIdentityV2, pid, 0);
CHECK_OFFSET(MaceProcessIdentityV2, reserved, 4);
CHECK_OFFSET(MaceProcessIdentityV2, creation_time_100ns_utc, 8);
CHECK_SIZE(MaceProcessInfoV2, 576);
CHECK_ALIGN(MaceProcessInfoV2, 8);
CHECK_OFFSET(MaceProcessInfoV2, struct_size, 0);
CHECK_OFFSET(MaceProcessInfoV2, version, 4);
CHECK_OFFSET(MaceProcessInfoV2, identity, 8);
CHECK_OFFSET(MaceProcessInfoV2, working_set_bytes, 24);
CHECK_OFFSET(MaceProcessInfoV2, private_bytes, 40);
CHECK_OFFSET(MaceProcessInfoV2, name, 56);
CHECK_SIZE(MaceProcessSampleV2, 64);
CHECK_ALIGN(MaceProcessSampleV2, 8);
CHECK_OFFSET(MaceProcessSampleV2, struct_size, 0);
CHECK_OFFSET(MaceProcessSampleV2, version, 4);
CHECK_OFFSET(MaceProcessSampleV2, identity, 8);
CHECK_OFFSET(MaceProcessSampleV2, timestamp_monotonic_ns, 24);
CHECK_OFFSET(MaceProcessSampleV2, working_set_bytes, 32);
CHECK_OFFSET(MaceProcessSampleV2, private_bytes, 48);
CHECK_SIZE(MaceResponsivenessV2, 40);
CHECK_ALIGN(MaceResponsivenessV2, 8);
CHECK_OFFSET(MaceResponsivenessV2, struct_size, 0);
CHECK_OFFSET(MaceResponsivenessV2, version, 4);
CHECK_OFFSET(MaceResponsivenessV2, identity, 8);
CHECK_OFFSET(MaceResponsivenessV2, windows_examined, 24);
CHECK_OFFSET(MaceResponsivenessV2, windows_hung, 28);
CHECK_OFFSET(MaceResponsivenessV2, outcome, 32);
CHECK_OFFSET(MaceResponsivenessV2, win32_error, 36);
CHECK_SIZE(MaceBlackboxHeaderV2, 48);
CHECK_ALIGN(MaceBlackboxHeaderV2, 8);
CHECK_OFFSET(MaceBlackboxHeaderV2, struct_size, 0);
CHECK_OFFSET(MaceBlackboxHeaderV2, version, 4);
CHECK_OFFSET(MaceBlackboxHeaderV2, magic, 8);
CHECK_OFFSET(MaceBlackboxHeaderV2, record_size, 12);
CHECK_OFFSET(MaceBlackboxHeaderV2, capacity_records, 16);
CHECK_OFFSET(MaceBlackboxHeaderV2, write_cursor, 20);
CHECK_OFFSET(MaceBlackboxHeaderV2, record_count, 24);
CHECK_OFFSET(MaceBlackboxHeaderV2, reserved, 28);
CHECK_OFFSET(MaceBlackboxHeaderV2, next_sequence, 32);
CHECK_OFFSET(MaceBlackboxHeaderV2, overwritten_records, 40);
CHECK_SIZE(MaceBlackboxRecordV2, 160);
CHECK_ALIGN(MaceBlackboxRecordV2, 8);
CHECK_OFFSET(MaceBlackboxRecordV2, struct_size, 0);
CHECK_OFFSET(MaceBlackboxRecordV2, version, 4);
CHECK_OFFSET(MaceBlackboxRecordV2, record_type, 8);
CHECK_OFFSET(MaceBlackboxRecordV2, payload_size, 12);
CHECK_OFFSET(MaceBlackboxRecordV2, sequence, 16);
CHECK_OFFSET(MaceBlackboxRecordV2, timestamp_monotonic_ns, 24);
CHECK_OFFSET(MaceBlackboxRecordV2, payload, 32);

int main(void) {
    puts("ABI layouts legacy y V2: OK (MSVC x64, wchar_t UTF-16)");
    return 0;
}
