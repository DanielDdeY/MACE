#include "mace_api.h"
#include "process_internal.h"
#include <psapi.h>
#include <string.h>

CP_API MaceStatus CP_SampleProcessV2(const MaceProcessIdentityV2* identity,
    MaceProcessSampleV2* out_sample, uint32_t out_capacity,
    uint32_t* out_win32_error) {
    if (out_win32_error) *out_win32_error = ERROR_SUCCESS;
    if (!identity || !identity->pid || identity->reserved ||
        !identity->creation_time_100ns_utc) return MACE_E_INVALID_ARGUMENT;
    if (out_capacity < sizeof(MaceProcessSampleV2)) return MACE_E_BUFFER_TOO_SMALL;
    if (!out_sample) return MACE_E_INVALID_ARGUMENT;

    HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | SYNCHRONIZE,
                                 FALSE, identity->pid);
    if (!process) {
        DWORD error = GetLastError();
        if (out_win32_error) *out_win32_error = error;
        return ProcessError_FromWin32(error);
    }
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
    MaceProcessIdentityV2 actual = {0};
    MaceStatus status = ProcessIdentity_Read(process, identity->pid, &actual,
                                             out_win32_error);
    if (status != MACE_OK) {
        CloseHandle(process);
        return status;
    }
    if (actual.creation_time_100ns_utc != identity->creation_time_100ns_utc) {
        CloseHandle(process);
        return MACE_E_PID_REUSED;
    }
    MaceProcessSampleV2 sample = {0};
    sample.struct_size = sizeof(sample);
    sample.version = MACE_V2_CONTRACT_VERSION;
    sample.identity = actual;
    sample.timestamp_monotonic_ns = GetTickCount64() * UINT64_C(1000000);
    PROCESS_MEMORY_COUNTERS_EX counters = {0};
    if (GetProcessMemoryInfo(process, (PROCESS_MEMORY_COUNTERS*)&counters,
                             sizeof(counters))) {
        sample.working_set_bytes.value = (uint64_t)counters.WorkingSetSize;
        sample.private_bytes.value = (uint64_t)counters.PrivateUsage;
        sample.working_set_bytes.state = MACE_METRIC_VALID;
        sample.private_bytes.state = MACE_METRIC_VALID;
    } else {
        DWORD error = GetLastError();
        if (out_win32_error) *out_win32_error = error;
        MaceMetricState state = error == ERROR_ACCESS_DENIED ?
            MACE_METRIC_ACCESS_DENIED : MACE_METRIC_READ_FAILED;
        sample.working_set_bytes.state = state;
        sample.private_bytes.state = state;
    }
    wait = WaitForSingleObject(process, 0);
    if (wait != WAIT_TIMEOUT) {
        if (wait == WAIT_FAILED && out_win32_error) *out_win32_error = GetLastError();
        CloseHandle(process);
        return wait == WAIT_OBJECT_0 ? MACE_E_PROCESS_EXITED : MACE_E_WINDOWS_ERROR;
    }
    CloseHandle(process);
    memcpy(out_sample, &sample, sizeof(sample));
    return MACE_OK;
}
