#ifndef MACE_TYPES_H
#define MACE_TYPES_H

#include <stdint.h>
#include <wchar.h>

#pragma pack(push, 8)

typedef struct {
    uint32_t pid;
    wchar_t  name[260];
    wchar_t  path[520];
    uint64_t working_set_bytes;
    uint64_t private_bytes;
} ProcessData;

typedef struct {
    float    cpu_temp;
    float    cpu_watts;
    float    gpu_temp;
    float    gpu_watts;
    uint32_t gpu_fan_rpm;
    uint8_t  is_gpu_available;
    uint8_t  _padding[3]; // Alineación a 4/8 bytes
} TelemetryData;

typedef struct {
    uint32_t magic_header;
    uint32_t ring_buffer_capacity;
    uint32_t write_cursor;
    uint32_t record_count;
} BlackboxSharedHeader;

#pragma pack(pop)

#endif // MACE_TYPES_H