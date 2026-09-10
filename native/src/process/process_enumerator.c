#include "mace_types.h"
#include <windows.h>
#include <tlhelp32.h>
#include <psapi.h>
#include <stdbool.h>

extern bool IsBlacklistedExecutable(const wchar_t* exe_name);
extern bool IsSystemPath(const wchar_t* full_path);

int EnumerateProcesses(ProcessData* out_buffer, uint32_t max_count, uint32_t* out_count) {
    if (!out_buffer || !out_count || max_count == 0) return -1;

    HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snapshot == INVALID_HANDLE_VALUE) {
        *out_count = 0;
        return (int)GetLastError();
    }

    PROCESSENTRY32W entry;
    entry.dwSize = sizeof(PROCESSENTRY32W);

    uint32_t collected = 0;

    if (Process32FirstW(snapshot, &entry)) {
        do {
            if (entry.th32ProcessID <= 4) continue;
            if (IsBlacklistedExecutable(entry.szExeFile)) continue;

            HANDLE hProcess = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, entry.th32ProcessID);
            if (!hProcess) continue;

            wchar_t path[520] = {0};
            DWORD path_size = 520;
            BOOL has_path = QueryFullProcessImageNameW(hProcess, 0, path, &path_size);

            if (!has_path || IsSystemPath(path)) {
                CloseHandle(hProcess);
                continue;
            }

            PROCESS_MEMORY_COUNTERS_EX pmc;
            uint64_t working_set = 0;
            uint64_t private_usage = 0;

            if (GetProcessMemoryInfo(hProcess, (PROCESS_MEMORY_COUNTERS*)&pmc, sizeof(pmc))) {
                working_set = (uint64_t)pmc.WorkingSetSize;
                private_usage = (uint64_t)pmc.PrivateUsage;
            }

            CloseHandle(hProcess);

            // Poblar registro en el buffer
            out_buffer[collected].pid = entry.th32ProcessID;
            wcsncpy_s(out_buffer[collected].name, 260, entry.szExeFile, _TRUNCATE);
            wcsncpy_s(out_buffer[collected].path, 520, path, _TRUNCATE);
            out_buffer[collected].working_set_bytes = working_set;
            out_buffer[collected].private_bytes = private_usage;

            collected++;
        } while (collected < max_count && Process32NextW(snapshot, &entry));
    }

    CloseHandle(snapshot);
    *out_count = collected;
    return 0;
}