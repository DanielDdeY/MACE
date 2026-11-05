#include "process_internal.h"

MaceStatus ProcessError_FromWin32(DWORD error) {
    if (error == ERROR_ACCESS_DENIED || error == ERROR_PRIVILEGE_NOT_HELD)
        return MACE_E_ACCESS_DENIED;
    if (error == ERROR_INVALID_PARAMETER || error == ERROR_NOT_FOUND ||
        error == ERROR_INVALID_HANDLE) return MACE_E_PROCESS_EXITED;
    return MACE_E_WINDOWS_ERROR;
}

MaceStatus ProcessIdentity_Read(HANDLE process, uint32_t pid,
                                MaceProcessIdentityV2* out, uint32_t* win32_error) {
    if (win32_error) *win32_error = ERROR_SUCCESS;
    if (!process || !out) return MACE_E_INVALID_ARGUMENT;
    FILETIME created, exited, kernel, user;
    if (!GetProcessTimes(process, &created, &exited, &kernel, &user)) {
        DWORD error = GetLastError();
        if (win32_error) *win32_error = error;
        return ProcessError_FromWin32(error);
    }
    out->pid = pid;
    out->reserved = 0;
    out->creation_time_100ns_utc = ((uint64_t)created.dwHighDateTime << 32) |
                                    created.dwLowDateTime;
    return MACE_OK;
}
