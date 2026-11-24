#include "mace_api.h"
#include "process_internal.h"
#include <string.h>
#include <wchar.h>

CP_API MaceStatus CP_GetProcessIdentityV2(uint32_t pid,
    MaceProcessIdentityV2* out_identity, uint32_t out_capacity,
    uint32_t* out_win32_error) {
    if (out_win32_error) *out_win32_error = ERROR_SUCCESS;
    if (!pid) return MACE_E_INVALID_ARGUMENT;
    if (out_capacity < sizeof(MaceProcessIdentityV2)) return MACE_E_BUFFER_TOO_SMALL;
    if (!out_identity) return MACE_E_INVALID_ARGUMENT;
    HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
    if (!process) {
        DWORD error = GetLastError();
        if (out_win32_error) *out_win32_error = error;
        return ProcessError_FromWin32(error);
    }
    MaceProcessIdentityV2 identity;
    MaceStatus status = ProcessIdentity_Read(process, pid, &identity,
                                             out_win32_error);
    CloseHandle(process);
    if (status == MACE_OK) memcpy(out_identity, &identity, sizeof(identity));
    return status;
}

CP_API MaceStatus CP_GetProcessImagePathV2(const MaceProcessIdentityV2* identity,
    wchar_t* out_path, uint32_t capacity_chars, uint32_t* out_required_chars,
    uint32_t* out_win32_error) {
    if (out_win32_error) *out_win32_error = ERROR_SUCCESS;
    if (!out_required_chars || !identity || !identity->pid || identity->reserved ||
        !identity->creation_time_100ns_utc) return MACE_E_INVALID_ARGUMENT;
    *out_required_chars = 0;
    HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | SYNCHRONIZE, FALSE,
                                 identity->pid);
    if (!process) {
        DWORD error = GetLastError();
        if (out_win32_error) *out_win32_error = error;
        return ProcessError_FromWin32(error);
    }
    MaceProcessIdentityV2 actual;
    wchar_t* path = NULL;
    DWORD wait = WaitForSingleObject(process, 0);
    if (wait == WAIT_OBJECT_0) {
        CloseHandle(process);
        return MACE_E_PROCESS_EXITED;
    }
    if (wait == WAIT_FAILED) {
        if (out_win32_error) *out_win32_error = GetLastError();
        CloseHandle(process);
        return MACE_E_WINDOWS_ERROR;
    }
    MaceStatus status = ProcessIdentity_Read(process, identity->pid, &actual,
                                             out_win32_error);
    if (status != MACE_OK) goto done;
    if (actual.creation_time_100ns_utc != identity->creation_time_100ns_utc) {
        status = MACE_E_PID_REUSED;
        goto done;
    }
    status = ProcessPath_Read(process, &path, out_win32_error);
    if (status != MACE_OK) goto done;
    status = ProcessSecurity_Check(process, identity->pid, path, out_win32_error);
    if (status != MACE_OK) goto done;
    size_t required = wcslen(path) + 1;
    if (required > UINT32_MAX) {
        status = MACE_E_WINDOWS_ERROR;
        if (out_win32_error) *out_win32_error = ERROR_ARITHMETIC_OVERFLOW;
        goto done;
    }
    *out_required_chars = (uint32_t)required;
    if (capacity_chars < required) status = MACE_E_BUFFER_TOO_SMALL;
    else if (!out_path) status = MACE_E_INVALID_ARGUMENT;
    else memcpy(out_path, path, required * sizeof(wchar_t));
done:
    ProcessPath_Free(path);
    CloseHandle(process);
    return status;
}

CP_API MaceStatus CP_TerminateProcessV2(const MaceProcessIdentityV2* identity,
                                        uint32_t* out_win32_error) {
    if (out_win32_error) *out_win32_error = ERROR_SUCCESS;
    if (!identity) return MACE_E_INVALID_ARGUMENT;
    return ProcessController_Terminate(identity->pid, identity,
                                       out_win32_error);
}
