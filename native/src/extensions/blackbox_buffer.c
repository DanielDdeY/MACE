#include "mace_api.h"
#include "blackbox_buffer.h"
#include "../internal/mace_runtime.h"
#include <windows.h>
#include <string.h>

#define MACE_BLACKBOX_MAX_RECORDS 65536u

typedef struct BlackboxContext {
    struct BlackboxContext* next;
    uint64_t token;
    HANDLE mapping;
    MaceBlackboxHeaderV2* header;
    /* Authoritative ring state is private to this DLL, never read from the
     * writable mapping exposed by a named file-mapping object. */
    uint32_t capacity_records;
    uint32_t write_cursor;
    uint32_t record_count;
    uint64_t next_sequence;
    uint64_t overwritten_records;
} BlackboxContext;

/* Serializes create, append, read and close. Tokens are never reused during a
 * DLL lifetime, so a stale token cannot accidentally address a new mapping. */
static SRWLOCK blackbox_lock = SRWLOCK_INIT;
static BlackboxContext* blackboxes;
static uint64_t next_token = 1;

static BlackboxContext* find_context(uint64_t token) {
    for (BlackboxContext* item = blackboxes; item; item = item->next)
        if (item->token == token) return item;
    return NULL;
}

static MaceBlackboxHeaderV2 snapshot_header(const BlackboxContext* item) {
    MaceBlackboxHeaderV2 header = {0};
    header.struct_size = sizeof(header);
    header.version = MACE_V2_CONTRACT_VERSION;
    header.magic = MACE_BLACKBOX_V2_MAGIC;
    header.record_size = sizeof(MaceBlackboxRecordV2);
    header.capacity_records = item->capacity_records;
    header.write_cursor = item->write_cursor;
    header.record_count = item->record_count;
    header.next_sequence = item->next_sequence;
    header.overwritten_records = item->overwritten_records;
    return header;
}

static void publish_header(BlackboxContext* item) {
    MaceBlackboxHeaderV2 snapshot = snapshot_header(item);
    memcpy(item->header, &snapshot, sizeof(snapshot));
}

static void destroy_context(BlackboxContext* item) {
    UnmapViewOfFile(item->header);
    CloseHandle(item->mapping);
    HeapFree(GetProcessHeap(), 0, item);
}

static uint32_t expected_payload_size(uint32_t type) {
    switch (type) {
    case MACE_RECORD_CPU_TELEMETRY: return sizeof(MaceCpuTelemetryV2);
    case MACE_RECORD_GPU_TELEMETRY: return sizeof(MaceGpuTelemetryV2);
    case MACE_RECORD_PROCESS_SAMPLE: return sizeof(MaceProcessSampleV2);
    case MACE_RECORD_SYSTEM_MEMORY: return sizeof(MaceSystemMemoryV2);
    default: return 0;
    }
}

static MaceStatus create_blackbox(const wchar_t* name,
    uint32_t capacity_records, uint64_t* out_handle, uint32_t* out_win32_error) {
    if (out_win32_error) *out_win32_error = ERROR_SUCCESS;
    if (out_handle) *out_handle = 0;
    if (!out_handle || (name && !*name) || !capacity_records ||
        capacity_records > MACE_BLACKBOX_MAX_RECORDS) return MACE_E_INVALID_ARGUMENT;

    const uint64_t bytes = sizeof(MaceBlackboxHeaderV2) +
        (uint64_t)capacity_records * sizeof(MaceBlackboxRecordV2);
    HANDLE mapping = CreateFileMappingW(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE,
        (DWORD)(bytes >> 32), (DWORD)bytes, name);
    if (!mapping) {
        DWORD error = GetLastError();
        if (out_win32_error) *out_win32_error = error;
        return error == ERROR_ACCESS_DENIED ? MACE_E_ACCESS_DENIED : MACE_E_WINDOWS_ERROR;
    }
    DWORD creation_error = GetLastError();
    if (creation_error == ERROR_ALREADY_EXISTS) {
        CloseHandle(mapping);
        if (out_win32_error) *out_win32_error = creation_error;
        return MACE_E_BUSY;
    }
    MaceBlackboxHeaderV2* header = MapViewOfFile(mapping, FILE_MAP_ALL_ACCESS,
        0, 0, (SIZE_T)bytes);
    if (!header) {
        DWORD error = GetLastError();
        CloseHandle(mapping);
        if (out_win32_error) *out_win32_error = error;
        return MACE_E_WINDOWS_ERROR;
    }
    BlackboxContext* item = HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, sizeof(*item));
    if (!item) {
        UnmapViewOfFile(header);
        CloseHandle(mapping);
        if (out_win32_error) *out_win32_error = ERROR_OUTOFMEMORY;
        return MACE_E_WINDOWS_ERROR;
    }
    memset(header, 0, (SIZE_T)bytes);
    item->mapping = mapping;
    item->header = header;
    item->capacity_records = capacity_records;
    item->next_sequence = 1;
    publish_header(item);

    AcquireSRWLockExclusive(&blackbox_lock);
    if (next_token == 0) {
        ReleaseSRWLockExclusive(&blackbox_lock);
        destroy_context(item);
        return MACE_E_BUSY;
    }
    item->token = next_token++;
    item->next = blackboxes;
    blackboxes = item;
    *out_handle = item->token;
    ReleaseSRWLockExclusive(&blackbox_lock);
    return MACE_OK;
}

CP_API MaceStatus CP_BlackboxCreateV2(const wchar_t* name,
    uint32_t capacity_records, uint64_t* out_handle, uint32_t* out_win32_error) {
    MaceRuntime_BeginResourceCreation();
    MaceStatus status = create_blackbox(name, capacity_records, out_handle,
                                        out_win32_error);
    MaceRuntime_EndResourceCreation();
    return status;
}

CP_API MaceStatus CP_BlackboxGetStatsV2(uint64_t handle,
    MaceBlackboxHeaderV2* out_header, uint32_t out_capacity) {
    if (!handle) return MACE_E_INVALID_ARGUMENT;
    if (out_capacity < sizeof(MaceBlackboxHeaderV2)) return MACE_E_BUFFER_TOO_SMALL;
    if (!out_header) return MACE_E_INVALID_ARGUMENT;
    AcquireSRWLockShared(&blackbox_lock);
    BlackboxContext* item = find_context(handle);
    if (item) *out_header = snapshot_header(item);
    bool found = item != NULL;
    ReleaseSRWLockShared(&blackbox_lock);
    return found ? MACE_OK : MACE_E_INVALID_ARGUMENT;
}

CP_API MaceStatus CP_BlackboxAppendV2(uint64_t handle, uint32_t record_type,
    const void* payload, uint32_t payload_size, uint64_t* out_sequence) {
    if (out_sequence) *out_sequence = 0;
    uint32_t expected = expected_payload_size(record_type);
    if (!handle || !payload || !expected || payload_size != expected) return MACE_E_INVALID_ARGUMENT;
    uint32_t struct_size = 0, version = 0;
    memcpy(&struct_size, payload, sizeof(struct_size));
    memcpy(&version, (const uint8_t*)payload + sizeof(struct_size), sizeof(version));
    if (struct_size != expected || version != MACE_V2_CONTRACT_VERSION)
        return MACE_E_UNSUPPORTED_VERSION;
    AcquireSRWLockExclusive(&blackbox_lock);
    BlackboxContext* item = find_context(handle);
    if (!item) {
        ReleaseSRWLockExclusive(&blackbox_lock);
        return MACE_E_INVALID_ARGUMENT;
    }
    if (item->next_sequence == UINT64_MAX) {
        ReleaseSRWLockExclusive(&blackbox_lock);
        return MACE_E_BUSY;
    }
    MaceBlackboxRecordV2* records = (MaceBlackboxRecordV2*)(item->header + 1);
    MaceBlackboxRecordV2* record = &records[item->write_cursor];
    memset(record, 0, sizeof(*record));
    record->struct_size = sizeof(*record);
    record->version = MACE_V2_CONTRACT_VERSION;
    record->record_type = record_type;
    record->payload_size = payload_size;
    record->sequence = item->next_sequence++;
    record->timestamp_monotonic_ns = GetTickCount64() * UINT64_C(1000000);
    memcpy(record->payload, payload, payload_size);
    item->write_cursor = (item->write_cursor + 1) % item->capacity_records;
    if (item->record_count < item->capacity_records) ++item->record_count;
    else ++item->overwritten_records;
    publish_header(item);
    if (out_sequence) *out_sequence = record->sequence;
    ReleaseSRWLockExclusive(&blackbox_lock);
    return MACE_OK;
}

CP_API MaceStatus CP_BlackboxReadV2(uint64_t handle, uint64_t from_sequence,
    MaceBlackboxRecordV2* out_records, uint32_t capacity_records,
    uint32_t* out_required_count) {
    if (!handle || !out_required_count) return MACE_E_INVALID_ARGUMENT;
    *out_required_count = 0;
    AcquireSRWLockShared(&blackbox_lock);
    BlackboxContext* item = find_context(handle);
    if (!item) {
        ReleaseSRWLockShared(&blackbox_lock);
        return MACE_E_INVALID_ARGUMENT;
    }
    const uint64_t oldest = item->next_sequence - item->record_count;
    const uint64_t start = (from_sequence == 0 || from_sequence < oldest) ? oldest : from_sequence;
    const uint32_t required = start < item->next_sequence ?
        (uint32_t)(item->next_sequence - start) : 0;
    *out_required_count = required;
    if (capacity_records < required) {
        ReleaseSRWLockShared(&blackbox_lock);
        return MACE_E_BUFFER_TOO_SMALL;
    }
    if (required && !out_records) {
        ReleaseSRWLockShared(&blackbox_lock);
        return MACE_E_INVALID_ARGUMENT;
    }
    const MaceBlackboxRecordV2* records = (const MaceBlackboxRecordV2*)(item->header + 1);
    const uint32_t oldest_index = (item->write_cursor + item->capacity_records -
                                   item->record_count) % item->capacity_records;
    for (uint32_t i = 0; i < required; ++i) {
        uint32_t index = (oldest_index + (uint32_t)(start - oldest) + i) %
                         item->capacity_records;
        out_records[i] = records[index];
    }
    ReleaseSRWLockShared(&blackbox_lock);
    return MACE_OK;
}

CP_API MaceStatus CP_BlackboxCloseV2(uint64_t handle) {
    if (!handle) return MACE_E_INVALID_ARGUMENT;
    AcquireSRWLockExclusive(&blackbox_lock);
    BlackboxContext** link = &blackboxes;
    while (*link && (*link)->token != handle) link = &(*link)->next;
    if (!*link) {
        ReleaseSRWLockExclusive(&blackbox_lock);
        return MACE_E_INVALID_ARGUMENT;
    }
    BlackboxContext* item = *link;
    *link = item->next;
    destroy_context(item);
    ReleaseSRWLockExclusive(&blackbox_lock);
    return MACE_OK;
}

void Blackbox_CloseAll(void) {
    AcquireSRWLockExclusive(&blackbox_lock);
    while (blackboxes) {
        BlackboxContext* item = blackboxes;
        blackboxes = item->next;
        destroy_context(item);
    }
    ReleaseSRWLockExclusive(&blackbox_lock);
}
