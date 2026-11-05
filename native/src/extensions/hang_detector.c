#include "mace_api.h"
#include "process_internal.h"
#include <windows.h>
#include <string.h>

typedef struct {
    DWORD pid;
    uint32_t examined;
    uint32_t hung;
} WindowContext;

static BOOL CALLBACK count_windows(HWND hwnd, LPARAM parameter) {
    WindowContext* context = (WindowContext*)parameter;
    DWORD owner_pid = 0;
    GetWindowThreadProcessId(hwnd, &owner_pid);
    if (owner_pid == context->pid && IsWindowVisible(hwnd)) {
        ++context->examined;
        if (IsHungAppWindow(hwnd)) ++context->hung;
    }
    return TRUE;
}

CP_API MaceStatus CP_CheckProcessResponsivenessV2(
    const MaceProcessIdentityV2* identity, MaceResponsivenessV2* out_result,
    uint32_t out_capacity) {
    if (!identity || !identity->pid || identity->reserved ||
        !identity->creation_time_100ns_utc) return MACE_E_INVALID_ARGUMENT;
    if (out_capacity < sizeof(MaceResponsivenessV2)) return MACE_E_BUFFER_TOO_SMALL;
    if (!out_result) return MACE_E_INVALID_ARGUMENT;

    HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | SYNCHRONIZE,
                                 FALSE, identity->pid);
    if (!process) return ProcessError_FromWin32(GetLastError());
    MaceResponsivenessV2 response = {0};
    response.struct_size = sizeof(response);
    response.version = MACE_V2_CONTRACT_VERSION;
    response.identity = *identity;

    DWORD wait = WaitForSingleObject(process, 0);
    if (wait == WAIT_OBJECT_0) {
        response.outcome = MACE_RESPONSIVENESS_PROCESS_EXITED;
    } else if (wait == WAIT_FAILED) {
        response.outcome = MACE_RESPONSIVENESS_ERROR;
        response.win32_error = GetLastError();
    } else {
        MaceProcessIdentityV2 actual = {0};
        uint32_t error = 0;
        MaceStatus status = ProcessIdentity_Read(process, identity->pid, &actual, &error);
        if (status != MACE_OK) {
            CloseHandle(process);
            return status;
        }
        if (actual.creation_time_100ns_utc != identity->creation_time_100ns_utc) {
            CloseHandle(process);
            return MACE_E_PID_REUSED;
        }
        WindowContext windows = {identity->pid, 0, 0};
        SetLastError(ERROR_SUCCESS);
        if (!EnumWindows(count_windows, (LPARAM)&windows)) {
            response.outcome = MACE_RESPONSIVENESS_ERROR;
            response.win32_error = GetLastError();
            if (!response.win32_error) response.win32_error = ERROR_GEN_FAILURE;
        } else {
            response.windows_examined = windows.examined;
            response.windows_hung = windows.hung;
            response.outcome = !windows.examined ? MACE_RESPONSIVENESS_NO_WINDOW :
                (windows.hung ? MACE_RESPONSIVENESS_HUNG : MACE_RESPONSIVENESS_RESPONDING);
        }
        wait = WaitForSingleObject(process, 0);
        if (wait == WAIT_OBJECT_0) {
            response.windows_examined = response.windows_hung = 0;
            response.outcome = MACE_RESPONSIVENESS_PROCESS_EXITED;
            response.win32_error = 0;
        } else if (wait == WAIT_FAILED) {
            response.outcome = MACE_RESPONSIVENESS_ERROR;
            response.win32_error = GetLastError();
        }
    }
    CloseHandle(process);
    memcpy(out_result, &response, sizeof(response));
    return MACE_OK;
}

int CheckWindowHung(uint32_t pid) {
    MaceProcessIdentityV2 identity = {0};
    if (CP_GetProcessIdentityV2(pid, &identity, sizeof(identity), NULL) != MACE_OK)
        return 0;
    MaceResponsivenessV2 result = {0};
    return CP_CheckProcessResponsivenessV2(&identity, &result, sizeof(result)) == MACE_OK &&
           result.outcome == MACE_RESPONSIVENESS_HUNG ? 1 : 0;
}
