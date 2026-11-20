#include "mace_api.h"
#include <windows.h>
#include <stdio.h>
#include <wchar.h>

int wmain(void) {
    wchar_t helper[32768];
    DWORD length = GetModuleFileNameW(NULL, helper, 32768);
    if (!length || length >= 32768) return 1;
    wchar_t* slash = wcsrchr(helper, L'\\');
    if (!slash) return 1;
    wcscpy_s(slash + 1, 32768 - (size_t)(slash + 1 - helper), L"process_helper.exe");

    STARTUPINFOW startup = {0};
    PROCESS_INFORMATION child = {0};
    startup.cb = sizeof(startup);
    if (!CreateProcessW(helper, NULL, NULL, NULL, FALSE, CREATE_NO_WINDOW,
                        NULL, NULL, &startup, &child)) {
        fprintf(stderr, "CreateProcessW error %lu\n", GetLastError());
        return 1;
    }

    uint32_t error = 0;
    MaceProcessIdentityV2 identity = {0};
    MaceStatus lookup = CP_GetProcessIdentityV2(child.dwProcessId, &identity,
                                                sizeof(identity), &error);
    MaceProcessIdentityV2 altered = identity;
    ++altered.creation_time_100ns_utc;
    MaceStatus rejected = lookup == MACE_OK ?
        CP_TerminateProcessV2(&altered, &error) : lookup;
    DWORD still_running = WaitForSingleObject(child.hProcess, 0);
    MaceStatus terminated = (rejected == MACE_E_PID_REUSED &&
                             still_running == WAIT_TIMEOUT) ?
        CP_TerminateProcessV2(&identity, &error) : MACE_E_WINDOWS_ERROR;
    DWORD finished = WaitForSingleObject(child.hProcess, 5000);
    if (finished != WAIT_OBJECT_0) TerminateProcess(child.hProcess, 1);
    CloseHandle(child.hThread);
    CloseHandle(child.hProcess);
    if (lookup != MACE_OK || rejected != MACE_E_PID_REUSED ||
        still_running != WAIT_TIMEOUT || terminated != MACE_OK ||
        finished != WAIT_OBJECT_0) {
        fprintf(stderr, "lookup=%d reused=%d alive=%lu terminate=%d wait=%lu win32=%u\n",
                lookup, rejected, still_running, terminated, finished, error);
        return 1;
    }
    puts("controlled child: reused identity rejected and exact identity terminated: OK");
    return 0;
}
