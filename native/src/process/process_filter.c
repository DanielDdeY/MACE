#include <windows.h>
#include <stdbool.h>
#include <wchar.h>

static const wchar_t* BLACKLIST[] = {
    L"csrss.exe",
    L"dwm.exe",
    L"smss.exe",
    L"services.exe",
    L"lsass.exe",
    L"wininit.exe",
    L"winlogon.exe",
    NULL
};

bool IsBlacklistedExecutable(const wchar_t* exe_name) {
    if (!exe_name) return true;
    for (int i = 0; BLACKLIST[i] != NULL; ++i) {
        if (_wcsicmp(exe_name, BLACKLIST[i]) == 0) {
            return true;
        }
    }
    return false;
}

bool IsSystemPath(const wchar_t* full_path) {
    if (!full_path || full_path[0] == L'\0') return true;

    wchar_t system_dir[MAX_PATH];
    wchar_t windows_dir[MAX_PATH];

    if (GetSystemDirectoryW(system_dir, MAX_PATH) > 0) {
        if (_wcsnicmp(full_path, system_dir, wcslen(system_dir)) == 0) {
            return true;
        }
    }

    if (GetWindowsDirectoryW(windows_dir, MAX_PATH) > 0) {
        wchar_t syswow64_dir[MAX_PATH];
        swprintf_s(syswow64_dir, MAX_PATH, L"%s\\SysWOW64", windows_dir);
        if (_wcsnicmp(full_path, syswow64_dir, wcslen(syswow64_dir)) == 0) {
            return true;
        }
    }

    if (wcsstr(full_path, L"\\System32\\") != NULL || wcsstr(full_path, L"\\SysWOW64\\") != NULL) {
        return true;
    }

    return false;
}