#include "mace_api.h"
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>

#define CHECK(x) do { if (!(x)) { \
    fprintf(stderr, "check failed at line %d: %s\n", __LINE__, #x); return 1; \
} } while (0)

typedef struct {
    HANDLE start;
    volatile LONG failures;
    volatile LONG completed;
    volatile LONG stop;
    volatile LONG initialized_polls;
    volatile LONG uninitialized_polls;
    MaceAdapterLuidV2 gpu;
    int have_gpu;
} PollContext;

static DWORD WINAPI poll_worker(void* parameter) {
    PollContext* context = parameter;
    if (WaitForSingleObject(context->start, 10000) != WAIT_OBJECT_0) {
        InterlockedIncrement(&context->failures);
        return 0;
    }
    while (InterlockedCompareExchange(&context->stop, 0, 0) == 0) {
        MaceCpuTelemetryV2 cpu = {0};
        MaceStatus status = CP_PollCpuTelemetryV2(&cpu, sizeof(cpu));
        if (status != MACE_OK && status != MACE_E_NOT_INITIALIZED)
            InterlockedIncrement(&context->failures);
        if (status == MACE_OK) InterlockedIncrement(&context->initialized_polls);
        if (status == MACE_E_NOT_INITIALIZED)
            InterlockedIncrement(&context->uninitialized_polls);
        if (status == MACE_OK &&
            (cpu.struct_size != sizeof(cpu) || cpu.version != 2 ||
             (cpu.usage_percent.state == MACE_METRIC_VALID &&
              (cpu.usage_percent.value < 0 || cpu.usage_percent.value > 100))))
            InterlockedIncrement(&context->failures);
        if (context->have_gpu) {
            MaceGpuTelemetryV2 gpu = {0};
            status = CP_PollGpuTelemetryV2(&context->gpu, &gpu, sizeof(gpu));
            if (status != MACE_OK && status != MACE_E_NOT_INITIALIZED &&
                status != MACE_E_DEVICE_NOT_FOUND)
                InterlockedIncrement(&context->failures);
            if (status == MACE_OK && (gpu.struct_size != sizeof(gpu) ||
                                      gpu.version != 2))
                InterlockedIncrement(&context->failures);
        }
        TelemetryData legacy = {0};
        if (CP_PollTelemetry(&legacy) != 0 || legacy._padding[0] || legacy._padding[1])
            InterlockedIncrement(&context->failures);
        InterlockedIncrement(&context->completed);
        Sleep(1);
    }
    return 0;
}

int main(void) {
    CP_ShutdownTelemetry();
    PollContext context = {0};
    context.start = CreateEventW(NULL, TRUE, FALSE, NULL);
    CHECK(context.start != NULL);
    uint32_t gpu_count = 0;
    MaceStatus inventory = CP_EnumerateGpuAdapters(NULL, 0, &gpu_count);
    if (inventory == MACE_E_BUFFER_TOO_SMALL && gpu_count > 0 && gpu_count < 64) {
        MaceGpuAdapterInfoV2* adapters = calloc(gpu_count, sizeof(*adapters));
        CHECK(adapters != NULL);
        if (CP_EnumerateGpuAdapters(adapters, gpu_count, &gpu_count) == MACE_OK) {
            context.gpu = adapters[0].luid;
            context.have_gpu = 1;
        }
        free(adapters);
    }
    HANDLE workers[4] = {0};
    for (int i = 0; i < 4; ++i) {
        workers[i] = CreateThread(NULL, 0, poll_worker, &context, 0, NULL);
        CHECK(workers[i] != NULL);
    }
    SetEvent(context.start);
    Sleep(10); /* Guarantee a read while shutdown. */
    for (int cycle = 0; cycle < 12; ++cycle) {
        CHECK(CP_InitializeTelemetry() == 0);
        Sleep(10);
        CP_ShutdownTelemetry();
        Sleep(2);
    }
    InterlockedExchange(&context.stop, 1);
    CHECK(WaitForMultipleObjects(4, workers, TRUE, 90000) == WAIT_OBJECT_0);
    for (int i = 0; i < 4; ++i) CloseHandle(workers[i]);
    CloseHandle(context.start);
    CHECK(context.completed > 0 && context.failures == 0);
    CHECK(context.initialized_polls > 0 && context.uninitialized_polls > 0);
    MaceCpuTelemetryV2 cpu = {0};
    CHECK(CP_PollCpuTelemetryV2(&cpu, sizeof(cpu)) == MACE_E_NOT_INITIALIZED);
    printf("%ld concurrent poll cycles across 12 init/shutdown cycles: OK\n",
           context.completed);
    return 0;
}
