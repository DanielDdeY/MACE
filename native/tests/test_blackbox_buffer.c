#include "mace_api.h"
#include <windows.h>
#include <stdio.h>
#include <string.h>

#define CHECK(x) do { if (!(x)) { \
    fprintf(stderr, "check failed at line %d: %s\n", __LINE__, #x); return 1; \
} } while (0)

int main(void) {
    wchar_t name[100];
    swprintf_s(name, 100, L"Local\\MACEPhase4Test_%lu_%llu",
               GetCurrentProcessId(), (unsigned long long)GetTickCount64());
    DWORD handles_before = 0, handles_after = 0;
    CHECK(GetProcessHandleCount(GetCurrentProcess(), &handles_before));

    uint64_t token = 0, duplicate = 123;
    uint32_t error = 0;
    CHECK(CP_BlackboxCreateV2(name, 0, &token, &error) == MACE_E_INVALID_ARGUMENT);
    CHECK(token == 0);
    CHECK(CP_BlackboxCreateV2(name, 3, &token, &error) == MACE_OK);
    CHECK(token != 0);
    CHECK(CP_BlackboxCreateV2(name, 3, &duplicate, &error) == MACE_E_BUSY);
    CHECK(duplicate == 0 && error == ERROR_ALREADY_EXISTS);

    MaceSystemMemoryV2 memory = {0};
    memory.struct_size = sizeof(memory);
    memory.version = 2;
    memory.total_bytes.value = 1234;
    memory.total_bytes.state = MACE_METRIC_VALID;
    CHECK(CP_BlackboxAppendV2(token, MACE_RECORD_SYSTEM_MEMORY, &memory,
          sizeof(memory) - 1, NULL) == MACE_E_INVALID_ARGUMENT);
    for (uint64_t i = 1; i <= 5; ++i) {
        uint64_t sequence = 0;
        memory.total_bytes.value = i;
        CHECK(CP_BlackboxAppendV2(token, MACE_RECORD_SYSTEM_MEMORY, &memory,
              sizeof(memory), &sequence) == MACE_OK);
        CHECK(sequence == i);
    }
    MaceBlackboxHeaderV2 stats = {0};
    CHECK(CP_BlackboxGetStatsV2(token, &stats, sizeof(stats)) == MACE_OK);
    CHECK(stats.magic == MACE_BLACKBOX_V2_MAGIC && stats.version == 2);
    CHECK(stats.capacity_records == 3 && stats.record_size == 160);
    CHECK(stats.record_count == 3 && stats.write_cursor == 2);
    CHECK(stats.next_sequence == 6 && stats.overwritten_records == 2);

    MaceBlackboxRecordV2 records[3];
    memset(records, 0xA5, sizeof(records));
    uint32_t required = 0;
    CHECK(CP_BlackboxReadV2(token, 0, records, 2,
          &required) == MACE_E_BUFFER_TOO_SMALL);
    CHECK(required == 3);
    for (size_t i = 0; i < sizeof(records); ++i)
        CHECK(((const unsigned char*)records)[i] == 0xA5);
    CHECK(CP_BlackboxReadV2(token, 0, records, 3, &required) == MACE_OK);
    CHECK(required == 3);
    for (uint32_t i = 0; i < required; ++i) {
        MaceSystemMemoryV2 copied = {0};
        CHECK(records[i].struct_size == sizeof(records[i]));
        CHECK(records[i].version == 2 && records[i].record_type == MACE_RECORD_SYSTEM_MEMORY);
        CHECK(records[i].payload_size == sizeof(memory) && records[i].sequence == i + 3);
        memcpy(&copied, records[i].payload, sizeof(copied));
        CHECK(copied.total_bytes.value == i + 3);
    }
    CHECK(CP_BlackboxReadV2(token, 4, records, 3, &required) == MACE_OK);
    CHECK(required == 2 && records[0].sequence == 4 && records[1].sequence == 5);

    /* A second writable view simulates a process corrupting the named
     * mapping's control header. DLL operations must use private state. */
    HANDLE outsider = OpenFileMappingW(FILE_MAP_ALL_ACCESS, FALSE, name);
    CHECK(outsider != NULL);
    MaceBlackboxHeaderV2* exposed = MapViewOfFile(outsider, FILE_MAP_ALL_ACCESS,
                                                   0, 0, sizeof(*exposed));
    CHECK(exposed != NULL);
    exposed->magic = 0;
    exposed->capacity_records = 0;
    exposed->write_cursor = UINT32_MAX;
    exposed->record_count = UINT32_MAX;
    exposed->next_sequence = UINT64_MAX;
    exposed->overwritten_records = UINT64_MAX;
    CHECK(CP_BlackboxGetStatsV2(token, &stats, sizeof(stats)) == MACE_OK);
    CHECK(stats.magic == MACE_BLACKBOX_V2_MAGIC && stats.capacity_records == 3);
    CHECK(stats.write_cursor == 2 && stats.record_count == 3);
    CHECK(stats.next_sequence == 6 && stats.overwritten_records == 2);
    CHECK(CP_BlackboxReadV2(token, 0, records, 3, &required) == MACE_OK);
    CHECK(required == 3 && records[0].sequence == 3 && records[2].sequence == 5);
    uint64_t recovered_sequence = 0;
    memory.total_bytes.value = 6;
    CHECK(CP_BlackboxAppendV2(token, MACE_RECORD_SYSTEM_MEMORY, &memory,
          sizeof(memory), &recovered_sequence) == MACE_OK);
    CHECK(recovered_sequence == 6);
    CHECK(CP_BlackboxGetStatsV2(token, &stats, sizeof(stats)) == MACE_OK);
    CHECK(stats.write_cursor == 0 && stats.record_count == 3);
    CHECK(stats.next_sequence == 7 && stats.overwritten_records == 3);
    CHECK(exposed->magic == stats.magic && exposed->capacity_records == 3);
    CHECK(exposed->next_sequence == 7 && exposed->write_cursor == 0);
    CHECK(CP_BlackboxReadV2(token, 0, records, 3, &required) == MACE_OK);
    CHECK(required == 3 && records[0].sequence == 4 && records[2].sequence == 6);
    CHECK(UnmapViewOfFile(exposed));
    CHECK(CloseHandle(outsider));

    CHECK(CP_BlackboxCloseV2(token) == MACE_OK);
    CHECK(CP_BlackboxCloseV2(token) == MACE_E_INVALID_ARGUMENT);
    CHECK(CP_BlackboxReadV2(token, 0, records, 3, &required) == MACE_E_INVALID_ARGUMENT);
    CHECK(CP_BlackboxCreateV2(name, 4, &token, &error) == MACE_OK);
    MaceCpuTelemetryV2 cpu = {0};
    cpu.struct_size = sizeof(cpu);
    cpu.version = 2;
    MaceGpuTelemetryV2 gpu = {0};
    gpu.struct_size = sizeof(gpu);
    gpu.version = 2;
    MaceProcessSampleV2 process = {0};
    process.struct_size = sizeof(process);
    process.version = 2;
    CHECK(CP_BlackboxAppendV2(token, MACE_RECORD_CPU_TELEMETRY,
          &cpu, sizeof(cpu), NULL) == MACE_OK);
    CHECK(CP_BlackboxAppendV2(token, MACE_RECORD_GPU_TELEMETRY,
          &gpu, sizeof(gpu), NULL) == MACE_OK);
    CHECK(CP_BlackboxAppendV2(token, MACE_RECORD_PROCESS_SAMPLE,
          &process, sizeof(process), NULL) == MACE_OK);
    process.version = 99;
    CHECK(CP_BlackboxAppendV2(token, MACE_RECORD_PROCESS_SAMPLE,
          &process, sizeof(process), NULL) == MACE_E_UNSUPPORTED_VERSION);
    MaceBlackboxRecordV2 typed[4] = {0};
    CHECK(CP_BlackboxReadV2(token, 0, typed, 4, &required) == MACE_OK);
    CHECK(required == 3);
    CHECK(typed[0].record_type == MACE_RECORD_CPU_TELEMETRY &&
          typed[1].record_type == MACE_RECORD_GPU_TELEMETRY &&
          typed[2].record_type == MACE_RECORD_PROCESS_SAMPLE);
    CP_ShutdownTelemetry();
    CHECK(CP_BlackboxCloseV2(token) == MACE_E_INVALID_ARGUMENT);

    void* legacy = CP_InitSharedTelemetryBuffer(name, 4096);
    CHECK(legacy != NULL);
    CHECK(CP_InitSharedTelemetryBuffer(name, 4096) == NULL);
    BlackboxSharedHeader* old_header = legacy;
    CHECK(old_header->magic_header == 0x4D414345);
    CHECK(old_header->ring_buffer_capacity == 4096 - sizeof(*old_header));
    CHECK(CP_CloseSharedTelemetryBuffer(legacy) == MACE_OK);
    CHECK(CP_CloseSharedTelemetryBuffer(legacy) == MACE_E_INVALID_ARGUMENT);
    legacy = CP_InitSharedTelemetryBuffer(name, 4096);
    CHECK(legacy != NULL);
    CP_ShutdownTelemetry();
    CHECK(CP_CloseSharedTelemetryBuffer(legacy) == MACE_E_INVALID_ARGUMENT);
    CHECK(GetProcessHandleCount(GetCurrentProcess(), &handles_after));
    CHECK(handles_after <= handles_before + 2);
    puts("V2 ring overwrite, copy read, named mapping and legacy close: OK");
    return 0;
}
