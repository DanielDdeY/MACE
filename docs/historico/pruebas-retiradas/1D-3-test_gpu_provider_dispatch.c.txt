#include <stdint.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include "gpu_providers.h"

#define REQUIRE(expr) do { if (!(expr)) { \
    fprintf(stderr, "FAILED line %d: %s\n", __LINE__, #expr); return 1; \
} } while (0)

static unsigned nvml_calls, igcl_calls, adlx_calls;

/* Inyección por enlace: los tres proveedores son simulados solo en este
 * ejecutable de pruebas. La DLL de distribución enlaza implementaciones reales. */
bool Nvml_Init(void) { return true; }
void Nvml_Shutdown(void) { }
bool Nvml_PollAdapter(const MaceGpuAdapterInfoV2* adapter,
                      MaceGpuTelemetryV2* out) {
    (void)adapter;
    ++nvml_calls;
    out->provider_id = MACE_PROVIDER_NVIDIA_NVML;
    out->temperature_c.state = MACE_METRIC_VALID;
    out->temperature_c.value = 40;
    return true;
}
bool Igcl_Init(void) { return true; }
void Igcl_Shutdown(void) { }
bool Igcl_PollAdapter(const MaceGpuAdapterInfoV2* adapter,
                      MaceGpuTelemetryV2* out) {
    (void)adapter;
    ++igcl_calls;
    out->provider_id = MACE_PROVIDER_INTEL_IGCL;
    out->temperature_c.state = MACE_METRIC_VALID;
    out->temperature_c.value = 41;
    return true;
}
bool Adlx_Init(void) { return true; }
void Adlx_Shutdown(void) { }
bool Adlx_PollAdapter(const MaceGpuAdapterInfoV2* adapter,
                      MaceGpuTelemetryV2* out) {
    (void)adapter;
    ++adlx_calls;
    out->provider_id = MACE_PROVIDER_AMD_ADLX;
    out->temperature_c.state = MACE_METRIC_VALID;
    out->temperature_c.value = 42;
    return true;
}

int main(void) {
    GpuProviders_Init();
    const uint32_t vendors[] = {0x10DEu, 0x1002u, 0x8086u, 0x1234u};
    const uint32_t providers[] = {MACE_PROVIDER_NVIDIA_NVML,
                                  MACE_PROVIDER_AMD_ADLX,
                                  MACE_PROVIDER_INTEL_IGCL,
                                  MACE_PROVIDER_NONE};
    for (unsigned i = 0; i < 4; ++i) {
        MaceGpuAdapterInfoV2 adapter = {0};
        adapter.vendor_id = vendors[i];
        adapter.adapter_index = UINT32_MAX; /* ningún DXGI real en este test */
        MaceGpuTelemetryV2 out = {0};
        GpuProviders_Poll(&adapter, &out);
        REQUIRE(out.provider_id == providers[i]);
        REQUIRE(out.temperature_c.state == (MaceMetricState)(i == 3 ?
            MACE_METRIC_NO_PROVIDER : MACE_METRIC_VALID));
    }
    REQUIRE(nvml_calls == 1 && adlx_calls == 1 && igcl_calls == 1);
    GpuProviders_Shutdown();
    return 0;
}
