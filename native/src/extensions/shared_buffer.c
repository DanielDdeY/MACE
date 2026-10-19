#include "mace_api.h"
#include "blackbox_buffer.h"
#include <windows.h>

#define MACE_LEGACY_MAX_BYTES (16u * 1024u * 1024u)

typedef struct LegacyView {
    struct LegacyView* next;
    HANDLE mapping;
    void* view;
} LegacyView;

static SRWLOCK legacy_lock = SRWLOCK_INIT;
static LegacyView* legacy_views;

void* SetupSharedTelemetryBuffer(const wchar_t* name, uint32_t size) {
    if (!name || !*name || size < sizeof(BlackboxSharedHeader) ||
        size > MACE_LEGACY_MAX_BYTES) return NULL;
    HANDLE mapping = CreateFileMappingW(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE,
                                       0, size, name);
    if (!mapping) return NULL;
    if (GetLastError() == ERROR_ALREADY_EXISTS) {
        CloseHandle(mapping);
        return NULL;
    }
    void* view = MapViewOfFile(mapping, FILE_MAP_ALL_ACCESS, 0, 0, size);
    if (!view) {
        CloseHandle(mapping);
        return NULL;
    }
    LegacyView* item = HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, sizeof(*item));
    if (!item) {
        UnmapViewOfFile(view);
        CloseHandle(mapping);
        return NULL;
    }
    BlackboxSharedHeader* header = view;
    header->magic_header = 0x4D414345;
    header->ring_buffer_capacity = size - sizeof(*header);
    header->write_cursor = 0;
    header->record_count = 0;
    item->mapping = mapping;
    item->view = view;
    AcquireSRWLockExclusive(&legacy_lock);
    item->next = legacy_views;
    legacy_views = item;
    ReleaseSRWLockExclusive(&legacy_lock);
    return view;
}

CP_API MaceStatus CP_CloseSharedTelemetryBuffer(void* view) {
    if (!view) return MACE_E_INVALID_ARGUMENT;
    AcquireSRWLockExclusive(&legacy_lock);
    LegacyView** link = &legacy_views;
    while (*link && (*link)->view != view) link = &(*link)->next;
    if (!*link) {
        ReleaseSRWLockExclusive(&legacy_lock);
        return MACE_E_INVALID_ARGUMENT;
    }
    LegacyView* item = *link;
    *link = item->next;
    UnmapViewOfFile(item->view);
    CloseHandle(item->mapping);
    HeapFree(GetProcessHeap(), 0, item);
    ReleaseSRWLockExclusive(&legacy_lock);
    return MACE_OK;
}

void SharedBuffer_CloseAll(void) {
    AcquireSRWLockExclusive(&legacy_lock);
    while (legacy_views) {
        LegacyView* item = legacy_views;
        legacy_views = item->next;
        UnmapViewOfFile(item->view);
        CloseHandle(item->mapping);
        HeapFree(GetProcessHeap(), 0, item);
    }
    ReleaseSRWLockExclusive(&legacy_lock);
}
