#include "mace_api.h"
#include <windows.h>
#include <stdio.h>

#define CHECK(x) do { if (!(x)) { \
    fprintf(stderr, "check failed at line %d: %s\n", __LINE__, #x); return 1; \
} } while (0)

typedef struct {
    uint64_t handle;
    HANDLE start;
    volatile LONG writes;
    volatile LONG failures;
    volatile LONG saw_closed;
} Context;

static DWORD WINAPI writer(void* parameter) {
    Context* context = parameter;
    WaitForSingleObject(context->start, INFINITE);
    MaceSystemMemoryV2 sample = {0};
    sample.struct_size = sizeof(sample);
    sample.version = 2;
    for (int i = 0; i < 100000; ++i) {
        sample.total_bytes.value = (uint64_t)i;
        MaceStatus status = CP_BlackboxAppendV2(context->handle,
            MACE_RECORD_SYSTEM_MEMORY, &sample, sizeof(sample), NULL);
        if (status == MACE_E_INVALID_ARGUMENT) {
            InterlockedExchange(&context->saw_closed, 1);
            break;
        }
        if (status != MACE_OK) InterlockedIncrement(&context->failures);
        InterlockedIncrement(&context->writes);
        Sleep(0);
    }
    return 0;
}

static DWORD WINAPI reader(void* parameter) {
    Context* context = parameter;
    WaitForSingleObject(context->start, INFINITE);
    MaceBlackboxRecordV2 records[16];
    for (int i = 0; i < 100000; ++i) {
        uint32_t required = 0;
        MaceStatus status = CP_BlackboxReadV2(context->handle, 0,
            records, 16, &required);
        if (status == MACE_E_INVALID_ARGUMENT) break;
        if (status != MACE_OK || required > 16)
            InterlockedIncrement(&context->failures);
        if (status == MACE_OK)
            for (uint32_t j = 1; j < required; ++j)
                if (records[j].sequence != records[j - 1].sequence + 1)
                    InterlockedIncrement(&context->failures);
        Sleep(0);
    }
    return 0;
}

int main(void) {
    CP_ShutdownTelemetry();
    Context context = {0};
    context.start = CreateEventW(NULL, TRUE, FALSE, NULL);
    CHECK(context.start != NULL);
    CHECK(CP_BlackboxCreateV2(NULL, 16, &context.handle, NULL) == MACE_OK);
    HANDLE threads[2];
    threads[0] = CreateThread(NULL, 0, writer, &context, 0, NULL);
    threads[1] = CreateThread(NULL, 0, reader, &context, 0, NULL);
    CHECK(threads[0] && threads[1]);
    SetEvent(context.start);
    for (int i = 0; i < 5000 && context.writes < 50; ++i) Sleep(1);
    CHECK(context.writes >= 50);
    CP_ShutdownTelemetry(); /* Must wait for in-flight copy/append. */
    CHECK(WaitForMultipleObjects(2, threads, TRUE, 10000) == WAIT_OBJECT_0);
    CHECK(context.failures == 0 && context.saw_closed == 1);
    CHECK(CP_BlackboxCloseV2(context.handle) == MACE_E_INVALID_ARGUMENT);
    CloseHandle(threads[0]);
    CloseHandle(threads[1]);
    CloseHandle(context.start);
    printf("Concurrent ring read/append while shutdown closes the mapping: OK\n");
    return 0;
}
