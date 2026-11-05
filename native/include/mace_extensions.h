#ifndef MACE_EXTENSIONS_H
#define MACE_EXTENSIONS_H

#include <stdint.h>
#include "mace_types_v2.h"

#define MACE_BLACKBOX_V2_MAGIC UINT32_C(0x4D414332) /* MAC2 */
#define MACE_BLACKBOX_V2_PAYLOAD_BYTES 128u

enum {
    MACE_RECORD_CPU_TELEMETRY = 1,
    MACE_RECORD_GPU_TELEMETRY = 2,
    MACE_RECORD_PROCESS_SAMPLE = 3,
    MACE_RECORD_SYSTEM_MEMORY = 4
};

/* El buffer nativo es temporal; la persistencia en disco pertenece al sink Java. */
#pragma pack(push, 8)

typedef struct {
    uint32_t struct_size;
    uint32_t version;
    uint32_t magic;
    uint32_t record_size;
    uint32_t capacity_records;
    uint32_t write_cursor;
    uint32_t record_count;
    uint32_t reserved;
    uint64_t next_sequence;
    uint64_t overwritten_records;
} MaceBlackboxHeaderV2;

typedef struct {
    uint32_t struct_size;
    uint32_t version;
    uint32_t record_type;
    uint32_t payload_size;
    uint64_t sequence;
    uint64_t timestamp_monotonic_ns;
    uint8_t payload[MACE_BLACKBOX_V2_PAYLOAD_BYTES];
} MaceBlackboxRecordV2;

#pragma pack(pop)

#endif /* MACE_EXTENSIONS_H */
