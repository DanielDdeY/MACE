#include "mace_api.h"
#include "process_internal.h"
#include <psapi.h>
#include <tlhelp32.h>
#include <string.h>
#include <wchar.h>

static MaceStatus collect(MaceProcessInfoV2** out_entries, uint32_t* out_count,
                          uint32_t* out_skipped, uint32_t* win32_error) {
    *out_entries = NULL;
    *out_count = 0;
    *out_skipped = 0;
    HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snapshot == INVALID_HANDLE_VALUE) {
        *win32_error = GetLastError();
        return MACE_E_WINDOWS_ERROR;
    }

    PROCESSENTRY32W entry = {0};
    entry.dwSize = sizeof(entry);
    if (!Process32FirstW(snapshot, &entry)) {
        DWORD error = GetLastError();
        CloseHandle(snapshot);
        if (error == ERROR_NO_MORE_FILES) return MACE_OK;
        *win32_error = error;
        return MACE_E_WINDOWS_ERROR;
    }

    uint32_t allocated = 0;
    MaceStatus result = MACE_OK;
    do {
        const DWORD pid = entry.th32ProcessID;
        if (pid <= 4 || pid == GetCurrentProcessId() ||
            ProcessSecurity_IsBlacklistedName(entry.szExeFile)) continue;
        HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
        if (!process) { ++*out_skipped; continue; }
        wchar_t* path = NULL;
        uint32_t ignored = 0;
        MaceStatus status = ProcessPath_Read(process, &path, &ignored);
        if (status != MACE_OK) { ++*out_skipped; CloseHandle(process); continue; }
        status = ProcessSecurity_Check(process, pid, path, &ignored);
        if (status != MACE_OK) {
            if (status != MACE_E_PROTECTED_PROCESS) ++*out_skipped;
            ProcessPath_Free(path);
            CloseHandle(process);
            continue;
        }

        MaceProcessInfoV2 info = {0};
        status = ProcessIdentity_Read(process, pid, &info.identity, &ignored);
        if (status != MACE_OK) {
            ++*out_skipped;
            ProcessPath_Free(path);
            CloseHandle(process);
            continue;
        }
        info.struct_size = sizeof(info);
        info.version = MACE_V2_CONTRACT_VERSION;
        const wchar_t* base = wcsrchr(path, L'\\');
        base = base ? base + 1 : path;
        wcsncpy_s(info.name, 260, base, _TRUNCATE);
        PROCESS_MEMORY_COUNTERS_EX memory = {0};
        if (GetProcessMemoryInfo(process, (PROCESS_MEMORY_COUNTERS*)&memory,
                                 sizeof(memory))) {
            info.working_set_bytes.value = (uint64_t)memory.WorkingSetSize;
            info.private_bytes.value = (uint64_t)memory.PrivateUsage;
            info.working_set_bytes.state = MACE_METRIC_VALID;
            info.private_bytes.state = MACE_METRIC_VALID;
        } else {
            DWORD error = GetLastError();
            MaceMetricState state = error == ERROR_ACCESS_DENIED ?
                MACE_METRIC_ACCESS_DENIED : MACE_METRIC_READ_FAILED;
            info.working_set_bytes.state = state;
            info.private_bytes.state = state;
        }
        ProcessPath_Free(path);
        CloseHandle(process);

        if (*out_count == allocated) {
            if (allocated > UINT32_MAX / 2 ||
                (SIZE_T)(allocated ? allocated * 2 : 64) > SIZE_MAX / sizeof(info)) {
                *win32_error = ERROR_OUTOFMEMORY;
                result = MACE_E_WINDOWS_ERROR;
                break;
            }
            uint32_t next = allocated ? allocated * 2 : 64;
            void* memory_block = allocated ?
                HeapReAlloc(GetProcessHeap(), 0, *out_entries, (SIZE_T)next * sizeof(info)) :
                HeapAlloc(GetProcessHeap(), 0, (SIZE_T)next * sizeof(info));
            if (!memory_block) {
                *win32_error = ERROR_OUTOFMEMORY;
                result = MACE_E_WINDOWS_ERROR;
                break;
            }
            *out_entries = memory_block;
            allocated = next;
        }
        (*out_entries)[(*out_count)++] = info;
    } while (Process32NextW(snapshot, &entry));

    if (result == MACE_OK) {
        DWORD error = GetLastError();
        if (error != ERROR_NO_MORE_FILES) {
            *win32_error = error;
            result = MACE_E_WINDOWS_ERROR;
        }
    }
    CloseHandle(snapshot);
    if (result != MACE_OK) {
        if (*out_entries) HeapFree(GetProcessHeap(), 0, *out_entries);
        *out_entries = NULL;
        *out_count = 0;
    }
    return result;
}

CP_API MaceStatus CP_EnumerateUserlandProcessesV2(MaceProcessInfoV2* out_entries,
    uint32_t capacity, uint32_t* out_required_count, uint32_t* out_skipped_count,
    uint32_t* out_win32_error) {
    if (out_win32_error) *out_win32_error = ERROR_SUCCESS;
    if (out_skipped_count) *out_skipped_count = 0;
    if (!out_required_count) return MACE_E_INVALID_ARGUMENT;
    *out_required_count = 0;
    uint32_t error = ERROR_SUCCESS, count = 0, skipped = 0;
    MaceProcessInfoV2* entries = NULL;
    MaceStatus status = collect(&entries, &count, &skipped, &error);
    if (out_win32_error) *out_win32_error = error;
    if (status != MACE_OK) return status;
    *out_required_count = count;
    if (out_skipped_count) *out_skipped_count = skipped;
    if (capacity < count) status = MACE_E_BUFFER_TOO_SMALL;
    else if (count && !out_entries) status = MACE_E_INVALID_ARGUMENT;
    else if (count) memcpy(out_entries, entries, (SIZE_T)count * sizeof(*entries));
    if (entries) HeapFree(GetProcessHeap(), 0, entries);
    return status;
}

int EnumerateProcesses(ProcessData* out_buffer, uint32_t max_count, uint32_t* out_count) {
    if (!out_buffer || !out_count || max_count == 0) return -1;
    *out_count = 0;
    uint32_t count = 0, skipped = 0, error = 0;
    MaceProcessInfoV2* entries = NULL;
    MaceStatus status = collect(&entries, &count, &skipped, &error);
    (void)skipped;
    if (status != MACE_OK) return error ? (int)error : ERROR_GEN_FAILURE;
    uint32_t written = count < max_count ? count : max_count;
    for (uint32_t i = 0; i < written; ++i) {
        ProcessData* item = &out_buffer[i];
        memset(item, 0, sizeof(*item));
        item->pid = entries[i].identity.pid;
        wcsncpy_s(item->name, 260, entries[i].name, _TRUNCATE);
        /* La API antigua conserva path[520]; el camino completo V2 está aparte. */
        MaceProcessIdentityV2 identity = entries[i].identity;
        wchar_t* path = NULL;
        HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE,
                                     identity.pid);
        if (process) {
            MaceProcessIdentityV2 actual;
            if (ProcessIdentity_Read(process, identity.pid, &actual, NULL) == MACE_OK &&
                actual.creation_time_100ns_utc == identity.creation_time_100ns_utc &&
                ProcessPath_Read(process, &path, NULL) == MACE_OK) {
                wcsncpy_s(item->path, 520, path, _TRUNCATE);
            }
            ProcessPath_Free(path);
            CloseHandle(process);
        }
        if (entries[i].working_set_bytes.state == MACE_METRIC_VALID)
            item->working_set_bytes = entries[i].working_set_bytes.value;
        if (entries[i].private_bytes.state == MACE_METRIC_VALID)
            item->private_bytes = entries[i].private_bytes.value;
    }
    if (entries) HeapFree(GetProcessHeap(), 0, entries);
    *out_count = written;
    return 0;
}
