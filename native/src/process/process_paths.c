#include "process_internal.h"
#include <limits.h>

MaceStatus ProcessPath_Read(HANDLE process, wchar_t** out_path, uint32_t* win32_error) {
    if (win32_error) *win32_error = ERROR_SUCCESS;
    if (!out_path || !process) return MACE_E_INVALID_ARGUMENT;
    *out_path = NULL;
    DWORD capacity = 512;
    while (capacity <= 32768) {
        wchar_t* path = HeapAlloc(GetProcessHeap(), 0, (SIZE_T)capacity * sizeof(wchar_t));
        if (!path) {
            if (win32_error) *win32_error = ERROR_OUTOFMEMORY;
            return MACE_E_WINDOWS_ERROR;
        }
        DWORD length = capacity;
        BOOL success = QueryFullProcessImageNameW(process, 0, path, &length);
        if (success && length < capacity) {
            path[length] = L'\0';
            *out_path = path;
            return MACE_OK;
        }
        DWORD error = success ? ERROR_INSUFFICIENT_BUFFER : GetLastError();
        HeapFree(GetProcessHeap(), 0, path);
        if (error != ERROR_INSUFFICIENT_BUFFER || capacity == 32768) {
            if (win32_error) *win32_error = error;
            return ProcessError_FromWin32(error);
        }
        capacity *= 2;
    }
    if (win32_error) *win32_error = ERROR_INSUFFICIENT_BUFFER;
    return MACE_E_WINDOWS_ERROR;
}

void ProcessPath_Free(wchar_t* path) {
    if (path) HeapFree(GetProcessHeap(), 0, path);
}
