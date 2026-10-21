#include "process_internal.h"
#include <wchar.h>
#include <wctype.h>

static const wchar_t* const blacklist[] = {
    L"csrss.exe", L"dwm.exe", L"smss.exe", L"services.exe",
    L"lsass.exe", L"wininit.exe", L"winlogon.exe", NULL
};

bool ProcessSecurity_IsBlacklistedName(const wchar_t* name) {
    if (!name || !*name) return true;
    for (size_t i = 0; blacklist[i]; ++i)
        if (_wcsicmp(name, blacklist[i]) == 0) return true;
    return false;
}

static bool within(const wchar_t* path, const wchar_t* directory) {
    if (!path || !directory || !*directory) return false;
    if (_wcsnicmp(path, L"\\\\?\\", 4) == 0 ||
        _wcsnicmp(path, L"\\??\\", 4) == 0) path += 4;
    size_t length = wcslen(directory);
    while (length && (directory[length - 1] == L'\\' ||
                      directory[length - 1] == L'/')) --length;
    if (!length) return false;
    for (size_t i = 0; i < length; ++i) {
        wchar_t a = path[i], b = directory[i];
        if (!a) return false;
        if (a == L'/') a = L'\\';
        if (b == L'/') b = L'\\';
        if (towlower(a) != towlower(b)) return false;
    }
    return path[length] == L'\0' || path[length] == L'\\' || path[length] == L'/';
}

bool ProcessSecurity_IsSystemPathWithRoots(const wchar_t* path,
                                           const wchar_t* windows_dir,
                                           const wchar_t* system_dir) {
    if (!path || !*path || !windows_dir || !*windows_dir ||
        !system_dir || !*system_dir) return true;
    if (within(path, system_dir) || within(path, windows_dir)) return true;
    return false;
}

MaceStatus ProcessSecurity_Check(HANDLE process, uint32_t pid,
                                 const wchar_t* path, uint32_t* win32_error) {
    if (win32_error) *win32_error = ERROR_SUCCESS;
    if (pid <= 4 || pid == GetCurrentProcessId()) return MACE_E_PROTECTED_PROCESS;
    if (!process || !path || !*path) return MACE_E_INVALID_ARGUMENT;
    const wchar_t* name = wcsrchr(path, L'\\');
    name = name ? name + 1 : path;
    if (ProcessSecurity_IsBlacklistedName(name)) return MACE_E_PROTECTED_PROCESS;

    wchar_t windows_dir[MAX_PATH];
    wchar_t system_dir[MAX_PATH];
    UINT windows_length = GetWindowsDirectoryW(windows_dir, MAX_PATH);
    UINT system_length = GetSystemDirectoryW(system_dir, MAX_PATH);
    if (!windows_length || windows_length >= MAX_PATH ||
        !system_length || system_length >= MAX_PATH) {
        if (win32_error) *win32_error = windows_length >= MAX_PATH ||
            system_length >= MAX_PATH ? ERROR_INSUFFICIENT_BUFFER : GetLastError();
        return MACE_E_WINDOWS_ERROR;
    }
    if (ProcessSecurity_IsSystemPathWithRoots(path, windows_dir, system_dir))
        return MACE_E_PROTECTED_PROCESS;

    BOOL critical = FALSE;
    if (!IsProcessCritical(process, &critical)) {
        DWORD error = GetLastError();
        if (win32_error) *win32_error = error;
        return ProcessError_FromWin32(error);
    }
    if (critical) return MACE_E_PROTECTED_PROCESS;
    return MACE_OK;
}
