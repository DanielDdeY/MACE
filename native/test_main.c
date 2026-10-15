#include <intrin.h>
#include <locale.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <windows.h>
#include "include/mace_api.h"

static const char* cpu_vendor_name(uint32_t vendor) {
    switch (vendor) {
        case MACE_CPU_VENDOR_INTEL: return "Intel";
        case MACE_CPU_VENDOR_AMD: return "AMD";
        default: return "Desconocido";
    }
}

static const char* gpu_vendor_name(uint32_t vendor) {
    switch (vendor) {
        case 0x8086u: return "Intel";
        case 0x1002u: return "AMD / Radeon";
        case 0x10DEu: return "NVIDIA";
        default: return "Otro fabricante";
    }
}

static const char* provider_name(MaceProviderId provider) {
    switch (provider) {
        case MACE_PROVIDER_WINDOWS: return "Windows";
        case MACE_PROVIDER_INTEL_MSR: return "Intel MSR";
        case MACE_PROVIDER_AMD_RYZEN_SDK: return "AMD Ryzen SDK";
        case MACE_PROVIDER_AMD_UPROF: return "AMD uProf";
        case MACE_PROVIDER_NVIDIA_NVML: return "NVIDIA NVML";
        case MACE_PROVIDER_AMD_ADLX: return "AMD ADLX";
        case MACE_PROVIDER_INTEL_IGCL: return "Intel IGCL";
        default: return "Ninguno";
    }
}

static const char* metric_state_name(MaceMetricState state) {
    switch (state) {
        case MACE_METRIC_VALID: return "valida";
        case MACE_METRIC_NO_DEVICE: return "dispositivo ausente";
        case MACE_METRIC_NO_PROVIDER: return "sin proveedor";
        case MACE_METRIC_UNSUPPORTED: return "no soportada";
        case MACE_METRIC_ACCESS_DENIED: return "acceso denegado";
        case MACE_METRIC_WARMING_UP: return "inicializando";
        case MACE_METRIC_READ_FAILED: return "fallo de lectura";
        case MACE_METRIC_DEVICE_INACTIVE: return "GPU inactiva";
        case MACE_METRIC_NOT_INITIALIZED: return "no inicializada";
        default: return "estado desconocido";
    }
}

static const char* power_scope_name(MacePowerScope scope) {
    switch (scope) {
        case MACE_POWER_SCOPE_CPU_PACKAGE: return "paquete CPU";
        case MACE_POWER_SCOPE_CPU_RAIL: return "rail CPU";
        case MACE_POWER_SCOPE_GPU_BOARD: return "tarjeta GPU";
        case MACE_POWER_SCOPE_GPU_CHIP: return "chip GPU";
        case MACE_POWER_SCOPE_APU_SOC: return "SoC APU";
        default: return "no especificado";
    }
}

static const char* cpu_brand(void) {
    static char brand[49];
    int registers[4] = {0};
    __cpuid(registers, (int)0x80000000u);
    if ((uint32_t)registers[0] < 0x80000004u) return "No informado";
    for (int i = 0; i < 3; ++i) {
        __cpuid(registers, (int)(0x80000002u + (uint32_t)i));
        memcpy(brand + i * 16, registers, 16);
    }
    for (size_t length = strlen(brand); length > 0 && brand[length - 1] == ' '; --length)
        brand[length - 1] = '\0';
    const char* first = brand;
    while (*first == ' ') ++first;
    return *first ? first : "No informado";
}

static void print_float_metric(const char* name, MaceMetricF32V2 metric,
                               const char* unit) {
    if (metric.state == MACE_METRIC_VALID)
        printf(" -> %-23s: %.1f %s\n", name, metric.value, unit);
    else
        printf(" -> %-23s: No disponible (%s)\n", name,
               metric_state_name(metric.state));
}

static void print_uint_metric(const char* name, MaceMetricU32V2 metric,
                              const char* unit) {
    if (metric.state == MACE_METRIC_VALID)
        printf(" -> %-23s: %u %s\n", name, metric.value, unit);
    else
        printf(" -> %-23s: No disponible (%s)\n", name,
               metric_state_name(metric.state));
}

static void print_memory_metric(const char* name, MaceMetricU64V2 metric) {
    if (metric.state == MACE_METRIC_VALID)
        printf(" -> %-23s: %.1f MiB (%llu bytes)\n", name,
               (double)metric.value / (1024.0 * 1024.0),
               (unsigned long long)metric.value);
    else
        printf(" -> %-23s: No disponible (%s)\n", name,
               metric_state_name(metric.state));
}

static MaceStatus enumerate_adapters(MaceGpuAdapterInfoV2** out_adapters,
                                     uint32_t* out_count) {
    *out_adapters = NULL;
    *out_count = 0;
    for (int attempt = 0; attempt < 3; ++attempt) {
        uint32_t count = 0;
        MaceStatus result = CP_EnumerateGpuAdapters(NULL, 0, &count);
        if (result != MACE_OK && result != MACE_E_BUFFER_TOO_SMALL) return result;
        if (count == 0) return MACE_OK;

        MaceGpuAdapterInfoV2* adapters = calloc(count, sizeof(*adapters));
        if (!adapters) return MACE_E_WINDOWS_ERROR;
        result = CP_EnumerateGpuAdapters(adapters, count, &count);
        if (result == MACE_OK) {
            *out_adapters = adapters;
            *out_count = count;
            return MACE_OK;
        }
        free(adapters);
        if (result != MACE_E_BUFFER_TOO_SMALL && result != MACE_E_BUSY)
            return result;
    }
    return MACE_E_BUSY;
}

static void print_cpu(const MaceCpuTelemetryV2* cpu) {
    printf("\n CPU: %s\n", cpu_brand());
    printf(" -> Fabricante             : %s\n", cpu_vendor_name(cpu->cpu_vendor));
    printf(" -> Proveedor de telemetria: %s\n", provider_name(cpu->provider_id));
    print_float_metric("Uso", cpu->usage_percent, "%");
    print_float_metric("Temperatura", cpu->temperature_c, "C");
    print_float_metric("Potencia", cpu->power_w, "W");
    if (cpu->power_w.state == MACE_METRIC_VALID)
        printf(" -> Alcance de potencia    : %s\n", power_scope_name(cpu->power_scope));
}

static void print_gpu(const MaceGpuAdapterInfoV2* adapter,
                      const MaceGpuTelemetryV2* gpu, uint32_t number) {
    printf("\n GPU %u: %ls\n", number, adapter->description);
    printf(" -> Fabricante             : %s\n", gpu_vendor_name(adapter->vendor_id));
    printf(" -> ID PCI                 : %04X:%04X (subsistema %08X, revision %u)\n",
           adapter->vendor_id, adapter->device_id, adapter->subsystem_id,
           adapter->revision);
    printf(" -> Proveedor de telemetria: %s\n", provider_name(gpu->provider_id));
    print_float_metric("Uso", gpu->usage_percent, "%");
    print_float_metric("Temperatura", gpu->temperature_c, "C");
    print_float_metric("Potencia", gpu->power_w, "W");
    if (gpu->power_w.state == MACE_METRIC_VALID)
        printf(" -> Alcance de potencia    : %s\n", power_scope_name(gpu->power_scope));
    print_uint_metric("Ventilador (RPM)", gpu->fan_rpm, "RPM");
    print_uint_metric("Ventilador (%)", gpu->fan_percent, "%");
    print_memory_metric("Memoria de video usada", gpu->video_memory_used_bytes);
    print_memory_metric("Memoria de video total", gpu->video_memory_total_bytes);
}

int main(void) {
    setlocale(LC_ALL, "");
    int errors = 0;

    printf("=== PRUEBA DE SUBSISTEMA NATIVO (MACE) ===\n\n");
    printf("[1] Detectando CPU y GPU; midiendo durante 1 segundo...\n");
    if (CP_InitializeTelemetry() == 0) {
        MaceCpuTelemetryV2 cpu = {0};
        MaceGpuAdapterInfoV2* adapters = NULL;
        uint32_t gpu_count = 0;
        MaceStatus inventory = enumerate_adapters(&adapters, &gpu_count);
        if (inventory != MACE_OK) {
            printf(" [!] Error al enumerar las GPU (codigo %d).\n", inventory);
            errors = 1;
        }

        /* La primera lectura prepara los contadores que necesitan un intervalo. */
        CP_PollCpuTelemetryV2(&cpu, sizeof(cpu));
        for (uint32_t i = 0; i < gpu_count; ++i) {
            MaceGpuTelemetryV2 warmup = {0};
            CP_PollGpuTelemetryV2(&adapters[i].luid, &warmup, sizeof(warmup));
        }
        Sleep(1000);

        MaceStatus cpu_result = CP_PollCpuTelemetryV2(&cpu, sizeof(cpu));
        if (cpu_result == MACE_OK) print_cpu(&cpu);
        else {
            printf(" [!] Error al leer la CPU (codigo %d).\n", cpu_result);
            errors = 1;
        }

        if (inventory == MACE_OK) {
            printf("\n Adaptadores GPU fisicos detectados: %u\n", gpu_count);
            for (uint32_t i = 0; i < gpu_count; ++i) {
                MaceGpuTelemetryV2 gpu = {0};
                MaceStatus gpu_result = CP_PollGpuTelemetryV2(
                    &adapters[i].luid, &gpu, sizeof(gpu));
                if (gpu_result == MACE_OK) print_gpu(&adapters[i], &gpu, i + 1);
                else {
                    printf("\n GPU %u: %ls\n", i + 1, adapters[i].description);
                    printf(" [!] Error al leer sus sensores (codigo %d).\n", gpu_result);
                    errors = 1;
                }
            }
        }
        free(adapters);
        CP_ShutdownTelemetry();
    } else {
        printf(" [!] Error al inicializar telemetria.\n");
        errors = 1;
    }

    printf("\n------------------------------------------------------------\n");

    // 2. Probar enumeracion y filtrado de procesos (Userland)
    printf("[2] Enumerando procesos Userland (Max 128)...\n");
    ProcessData buffer[128];
    uint32_t count = 0;

    if (CP_EnumerateUserlandProcesses(buffer, 128, &count) == 0) {
        printf(" -> Total procesos no-sistema encontrados: %u\n\n", count);
        printf("%-8s | %-30s | %-12s | %s\n", "PID", "NOMBRE", "RAM (MB)", "RUTA");
        printf("--------------------------------------------------------------------------------\n");

        uint32_t limit = count < 10 ? count : 10;
        for (uint32_t i = 0; i < limit; i++) {
            double ram_mb = (double)buffer[i].working_set_bytes / (1024.0 * 1024.0);
            wprintf(L"%-8u | %-30ls | %8.2f MB | %ls\n",
                    buffer[i].pid,
                    buffer[i].name,
                    ram_mb,
                    buffer[i].path);
        }
        if (count > 10) {
            printf("... y %u procesos mas.\n", count - 10);
        }
    } else {
        printf(" [!] Error al enumerar procesos.\n");
        errors = 1;
    }

    printf("\n=== PRUEBA FINALIZADA %s ===\n", errors ? "CON ERRORES" : "CON EXITO");
    return errors;
}
