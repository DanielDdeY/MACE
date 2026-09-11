#include <windows.h>
#include <stdint.h>
#include <stdbool.h>

int TerminateProcessByPid(uint32_t pid) {
    if (pid == 0 || pid == 4) { // Prevenir terminación de System Idle Process o System
        return -1;
    }

    HANDLE hProcess = OpenProcess(PROCESS_TERMINATE, FALSE, pid);
    if (hProcess == NULL) {
        return (int)GetLastError();
    }

    UINT exit_code = 1;
    BOOL result = TerminateProcess(hProcess, exit_code);
    CloseHandle(hProcess);

    return result ? 0 : (int)GetLastError();
}