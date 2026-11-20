#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "mace_api.h"

#define REQUIRE(expr) do { if (!(expr)) { \
    fprintf(stderr, "FAILED line %d: %s\n", __LINE__, #expr); return 1; \
} } while (0)

int main(void) {
    uint32_t count = 0;
    REQUIRE(CP_EnumerateGpuAdapters(NULL, 0, NULL) == MACE_E_INVALID_ARGUMENT);
    MaceStatus query = CP_EnumerateGpuAdapters(NULL, 0, &count);
    REQUIRE((count == 0 && query == MACE_OK) ||
            (count > 0 && query == MACE_E_BUFFER_TOO_SMALL));
    MaceGpuAdapterInfoV2* adapters = calloc(count ? count : 1, sizeof(*adapters));
    REQUIRE(adapters != NULL);
    if (count) {
        memset(adapters, 0xA5, count * sizeof(*adapters));
        uint32_t needed = 0;
        REQUIRE(CP_EnumerateGpuAdapters(adapters, count - 1, &needed) == MACE_E_BUFFER_TOO_SMALL);
        REQUIRE(needed == count);
        for (size_t i = 0; i < count * sizeof(*adapters); ++i)
            REQUIRE(((unsigned char*)adapters)[i] == 0xA5);
        REQUIRE(CP_EnumerateGpuAdapters(adapters, count, &needed) == MACE_OK);
        REQUIRE(needed == count);
    }
    MaceSystemMemoryV2 memory = {0};
    memset(&memory, 0xA5, sizeof(memory));
    REQUIRE(CP_PollSystemMemoryV2(&memory, sizeof(memory) - 1) == MACE_E_BUFFER_TOO_SMALL);
    for (size_t i = 0; i < sizeof(memory); ++i)
        REQUIRE(((unsigned char*)&memory)[i] == 0xA5);
    REQUIRE(CP_PollSystemMemoryV2(&memory, sizeof(memory)) == MACE_OK);
    REQUIRE(memory.total_bytes.state == MACE_METRIC_VALID &&
            memory.available_bytes.state == MACE_METRIC_VALID &&
            memory.total_bytes.value >= memory.available_bytes.value);
    MaceCpuTelemetryV2 cpu = {0};
    REQUIRE(CP_PollCpuTelemetryV2(&cpu, sizeof(cpu)) == MACE_E_NOT_INITIALIZED);
    REQUIRE(CP_InitializeTelemetry() == 0);
    REQUIRE(CP_InitializeTelemetry() == 0);
    memset(&cpu, 0xA5, sizeof(cpu));
    REQUIRE(CP_PollCpuTelemetryV2(&cpu, sizeof(cpu) - 1) == MACE_E_BUFFER_TOO_SMALL);
    for (size_t i = 0; i < sizeof(cpu); ++i)
        REQUIRE(((unsigned char*)&cpu)[i] == 0xA5);
    REQUIRE(CP_PollCpuTelemetryV2(&cpu, sizeof(cpu)) == MACE_OK);
    Sleep(350);
    REQUIRE(CP_PollCpuTelemetryV2(&cpu, sizeof(cpu)) == MACE_OK);
    REQUIRE(cpu.usage_percent.state == MACE_METRIC_VALID);
    REQUIRE(cpu.usage_percent.value >= 0 && cpu.usage_percent.value <= 100);
    printf("CPU vendor=%u provider=%u usage=%.1f temp_state=%u power_state=%u\n",
           cpu.cpu_vendor, cpu.provider_id, cpu.usage_percent.value,
           cpu.temperature_c.state, cpu.power_w.state);
    for (uint32_t i = 0; i < count; ++i) {
        MaceGpuTelemetryV2 gpu = {0};
        REQUIRE(adapters[i].struct_size == sizeof(adapters[i]));
        REQUIRE(adapters[i].version == MACE_V2_CONTRACT_VERSION);
        memset(&gpu, 0xA5, sizeof(gpu));
        REQUIRE(CP_PollGpuTelemetryV2(&adapters[i].luid, &gpu, sizeof(gpu) - 1) == MACE_E_BUFFER_TOO_SMALL);
        for (size_t j = 0; j < sizeof(gpu); ++j)
            REQUIRE(((unsigned char*)&gpu)[j] == 0xA5);
        REQUIRE(CP_PollGpuTelemetryV2(&adapters[i].luid, &gpu, sizeof(gpu)) == MACE_OK);
        REQUIRE(gpu.struct_size == sizeof(gpu));
        REQUIRE(gpu.luid.low_part == adapters[i].luid.low_part);
        printf("GPU vendor=%04X device=%04X provider=%u temp=%u %.1f power=%u %.1f usage=%u %.1f fan_rpm=%u %u mem=%u %llu/%llu\n",
            adapters[i].vendor_id, adapters[i].device_id, gpu.provider_id,
            gpu.temperature_c.state, gpu.temperature_c.value,
            gpu.power_w.state, gpu.power_w.value,
            gpu.usage_percent.state, gpu.usage_percent.value,
            gpu.fan_rpm.state, gpu.fan_rpm.value,
            gpu.video_memory_used_bytes.state,
            (unsigned long long)gpu.video_memory_used_bytes.value,
            (unsigned long long)gpu.video_memory_total_bytes.value);
        if (gpu.fan_rpm.state == MACE_METRIC_VALID)
            REQUIRE(gpu.fan_rpm.value < 20000);
    }
    MaceAdapterLuidV2 impossible = {0xFFFFFFFFu, -1};
    MaceGpuTelemetryV2 absent = {0};
    REQUIRE(CP_PollGpuTelemetryV2(&impossible, &absent, sizeof(absent)) == MACE_E_DEVICE_NOT_FOUND);
    TelemetryData legacy = {0};
    REQUIRE(CP_PollTelemetry(&legacy) == 0);
    REQUIRE(legacy._padding[0] == 0 && legacy._padding[1] == 0);
    CP_ShutdownTelemetry();
    CP_ShutdownTelemetry();
    REQUIRE(CP_PollCpuTelemetryV2(&cpu, sizeof(cpu)) == MACE_E_NOT_INITIALIZED);
    free(adapters);
    return 0;
}
