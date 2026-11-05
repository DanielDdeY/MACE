#include "process_internal.h"

MaceStatus ProcessController_Terminate(uint32_t pid,
                                       const MaceProcessIdentityV2* expected,
                                       uint32_t* win32_error) {
    if (win32_error) *win32_error = ERROR_SUCCESS;
    if (pid <= 4 || pid == GetCurrentProcessId()) return MACE_E_PROTECTED_PROCESS;
    if (expected && (expected->pid != pid || expected->reserved != 0 ||
                     expected->creation_time_100ns_utc == 0))
        return MACE_E_INVALID_ARGUMENT;

    /* La validación y TerminateProcess usan el mismo objeto kernel. */
    HANDLE process = OpenProcess(PROCESS_TERMINATE | PROCESS_QUERY_LIMITED_INFORMATION |
                                 SYNCHRONIZE,
                                 FALSE, pid);
    if (!process) {
        DWORD error = GetLastError();
        if (win32_error) *win32_error = error;
        return ProcessError_FromWin32(error);
    }
    MaceStatus status;
    MaceProcessIdentityV2 actual;
    wchar_t* path = NULL;
    DWORD wait = WaitForSingleObject(process, 0);
    if (wait == WAIT_OBJECT_0) {
        status = MACE_E_PROCESS_EXITED;
        goto done;
    }
    if (wait == WAIT_FAILED) {
        if (win32_error) *win32_error = GetLastError();
        status = MACE_E_WINDOWS_ERROR;
        goto done;
    }
    status = ProcessIdentity_Read(process, pid, &actual, win32_error);
    if (status != MACE_OK) goto done;
    if (expected && actual.creation_time_100ns_utc != expected->creation_time_100ns_utc) {
        status = MACE_E_PID_REUSED;
        goto done;
    }
    status = ProcessPath_Read(process, &path, win32_error);
    if (status != MACE_OK) goto done;
    status = ProcessSecurity_Check(process, pid, path, win32_error);
    if (status != MACE_OK) goto done;
    if (!TerminateProcess(process, 1)) {
        DWORD error = GetLastError();
        if (WaitForSingleObject(process, 0) == WAIT_OBJECT_0) {
            status = MACE_E_PROCESS_EXITED;
        } else {
            if (win32_error) *win32_error = error;
            status = ProcessError_FromWin32(error);
        }
        goto done;
    }
    status = MACE_OK;
done:
    ProcessPath_Free(path);
    CloseHandle(process);
    return status;
}

int TerminateProcessByPid(uint32_t pid) {
    if (pid <= 4) return -1;
    uint32_t error = ERROR_SUCCESS;
    MaceStatus status = ProcessController_Terminate(pid, NULL, &error);
    if (status == MACE_OK) return 0;
    if (status == MACE_E_PROTECTED_PROCESS || status == MACE_E_ACCESS_DENIED)
        return ERROR_ACCESS_DENIED;
    if (status == MACE_E_PROCESS_EXITED) return ERROR_INVALID_PARAMETER;
    return error ? (int)error : ERROR_GEN_FAILURE;
}
