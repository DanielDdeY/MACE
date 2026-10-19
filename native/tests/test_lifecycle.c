#include "mace_api.h"
#include <windows.h>
#include <psapi.h>
#include <stdio.h>

#define CHECK(x) do { if (!(x)) { \
    fprintf(stderr, "check failed at line %d: %s\n", __LINE__, #x); return 1; \
} } while (0)

static SIZE_T private_bytes(void) {
    PROCESS_MEMORY_COUNTERS_EX counters = {0};
    if (!GetProcessMemoryInfo(GetCurrentProcess(), (PROCESS_MEMORY_COUNTERS*)&counters,
                              sizeof(counters))) return 0;
    return counters.PrivateUsage;
}

int main(void) {
    CP_ShutdownTelemetry();
    DWORD handles_before = 0, handles_after = 0;
    SIZE_T memory_before = 0;

    for (uint32_t cycle = 0; cycle < 11; ++cycle) {
        CHECK(CP_InitializeTelemetry() == 0);
        CHECK(CP_InitializeTelemetry() == 0); /* Idempotent; no baseline reset. */
        MaceCpuTelemetryV2 cpu = {0};
        CHECK(CP_PollCpuTelemetryV2(&cpu, sizeof(cpu)) == MACE_OK);
        CHECK(cpu.usage_percent.state == MACE_METRIC_WARMING_UP);
        Sleep(30);
        CHECK(CP_PollCpuTelemetryV2(&cpu, sizeof(cpu)) == MACE_OK);
        CHECK(cpu.usage_percent.state == MACE_METRIC_VALID);
        CHECK(cpu.usage_percent.value >= 0.0f && cpu.usage_percent.value <= 100.0f);

        uint64_t token = 0;
        CHECK(CP_BlackboxCreateV2(NULL, 32, &token, NULL) == MACE_OK);
        wchar_t name[100];
        swprintf_s(name, 100, L"Local\\MaceLife_%lu_%u",
                   GetCurrentProcessId(), cycle);
        void* legacy = CP_InitSharedTelemetryBuffer(name, 4096);
        CHECK(legacy != NULL);
        CP_ShutdownTelemetry();
        CHECK(CP_BlackboxCloseV2(token) == MACE_E_INVALID_ARGUMENT);
        CHECK(CP_CloseSharedTelemetryBuffer(legacy) == MACE_E_INVALID_ARGUMENT);
        CHECK(CP_PollCpuTelemetryV2(&cpu, sizeof(cpu)) == MACE_E_NOT_INITIALIZED);
        if (cycle == 0) {
            /* First use can lazily load Windows and vendor DLLs. Measure steady state. */
            CHECK(GetProcessHandleCount(GetCurrentProcess(), &handles_before));
            memory_before = private_bytes();
            CHECK(memory_before != 0);
        }
    }
    CP_ShutdownTelemetry();
    CHECK(GetProcessHandleCount(GetCurrentProcess(), &handles_after));
    SIZE_T memory_after = private_bytes();
    CHECK(memory_after != 0);
    CHECK(handles_after <= handles_before + 8);
    CHECK(memory_after <= memory_before + 32u * 1024u * 1024u);
    printf("10 repeated cycles after warm-up; handles %lu -> %lu; private bytes %zu -> %zu: OK\n",
           handles_before, handles_after, (size_t)memory_before, (size_t)memory_after);
    return 0;
}
