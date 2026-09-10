#include "mace_types.h"
#include <windows.h>

void* SetupSharedTelemetryBuffer(const wchar_t* mapping_name, uint32_t buffer_size) {
    if (!mapping_name || buffer_size < sizeof(BlackboxSharedHeader)) {
        return NULL;
    }

    HANDLE hMapFile = CreateFileMappingW(
        INVALID_HANDLE_VALUE,
        NULL,
        PAGE_READWRITE,
        0,
        buffer_size,
        mapping_name
    );

    if (!hMapFile) return NULL;

    void* pBuffer = MapViewOfFile(hMapFile, FILE_MAP_ALL_ACCESS, 0, 0, buffer_size);
    if (!pBuffer) {
        CloseHandle(hMapFile);
        return NULL;
    }

    BlackboxSharedHeader* header = (BlackboxSharedHeader*)pBuffer;
    header->magic_header = 0x4D414345; // 'MACE'
    header->ring_buffer_capacity = buffer_size - sizeof(BlackboxSharedHeader);
    header->write_cursor = 0;
    header->record_count = 0;

    return pBuffer;
}