#include <intrin.h>
#include <locale.h>
#include <stdbool.h>
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

static const char* status_name(MaceStatus status) {
    switch (status) {
        case MACE_OK: return "correcto";
        case MACE_E_INVALID_ARGUMENT: return "argumento invalido";
        case MACE_E_BUFFER_TOO_SMALL: return "buffer insuficiente";
        case MACE_E_NOT_INITIALIZED: return "telemetria no inicializada";
        case MACE_E_UNSUPPORTED_VERSION: return "version no compatible";
        case MACE_E_ACCESS_DENIED: return "acceso denegado";
        case MACE_E_PROCESS_EXITED: return "proceso finalizado";
        case MACE_E_PID_REUSED: return "PID reutilizado";
        case MACE_E_SENSOR_UNAVAILABLE: return "sensor no disponible";
        case MACE_E_DEVICE_NOT_FOUND: return "dispositivo no encontrado";
        case MACE_E_BUSY: return "inventario cambio durante la consulta";
        case MACE_E_WINDOWS_ERROR: return "error de Windows";
        case MACE_E_PROTECTED_PROCESS: return "proceso protegido";
        default: return "estado desconocido";
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

static const char* elevation_name(void) {
    HANDLE token = NULL;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token))
        return "No se pudo consultar";
    TOKEN_ELEVATION elevation = {0};
    DWORD bytes = 0;
    BOOL queried = GetTokenInformation(token, TokenElevation, &elevation,
                                       sizeof(elevation), &bytes);
    CloseHandle(token);
    return queried ? (elevation.TokenIsElevated ? "Si" : "No") :
                     "No se pudo consultar";
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

static MaceStatus enumerate_processes(MaceProcessInfoV2** out_processes,
                                     uint32_t* out_count, uint32_t* out_skipped,
                                     uint32_t* out_win32_error) {
    *out_processes = NULL;
    *out_count = 0;
    *out_skipped = 0;
    *out_win32_error = 0;
    for (int attempt = 0; attempt < 5; ++attempt) {
        uint32_t count = 0, skipped = 0, error = 0;
        MaceStatus status = CP_EnumerateUserlandProcessesV2(NULL, 0, &count,
                                                               &skipped, &error);
        if (status == MACE_OK && count == 0) {
            *out_skipped = skipped;
            return MACE_OK;
        }
        if (status != MACE_E_BUFFER_TOO_SMALL) {
            *out_win32_error = error;
            return status;
        }
        uint32_t capacity = count <= UINT32_MAX - 32 ? count + 32 : count;
        MaceProcessInfoV2* processes = calloc(capacity, sizeof(*processes));
        if (!processes) return MACE_E_WINDOWS_ERROR;
        status = CP_EnumerateUserlandProcessesV2(processes, capacity, &count,
                                                   &skipped, &error);
        if (status == MACE_OK) {
            *out_processes = processes;
            *out_count = count;
            *out_skipped = skipped;
            return MACE_OK;
        }
        free(processes);
        if (status != MACE_E_BUFFER_TOO_SMALL) {
            *out_win32_error = error;
            return status;
        }
    }
    return MACE_E_BUSY;
}

static void print_process(const MaceProcessInfoV2* process) {
    printf("\n -> PID %u | %ls\n", process->identity.pid, process->name);
    print_memory_metric("RAM residente", process->working_set_bytes);
    print_memory_metric("Memoria privada", process->private_bytes);
    FILETIME created = {
        (DWORD)process->identity.creation_time_100ns_utc,
        (DWORD)(process->identity.creation_time_100ns_utc >> 32)
    };
    SYSTEMTIME utc = {0};
    if (FileTimeToSystemTime(&created, &utc))
        printf(" -> Creado (UTC)           : %04u-%02u-%02u %02u:%02u:%02u\n",
               utc.wYear, utc.wMonth, utc.wDay, utc.wHour, utc.wMinute, utc.wSecond);

    uint32_t required = 0, win32_error = 0;
    MaceStatus status = CP_GetProcessImagePathV2(&process->identity, NULL, 0,
                                                  &required, &win32_error);
    if (status == MACE_E_BUFFER_TOO_SMALL && required > 0) {
        wchar_t* path = calloc(required, sizeof(*path));
        if (!path) {
            puts(" -> Ruta                   : No disponible (memoria insuficiente)");
            return;
        }
        status = CP_GetProcessImagePathV2(&process->identity, path, required,
                                          &required, &win32_error);
        if (status == MACE_OK) printf(" -> Ruta                   : %ls\n", path);
        free(path);
    }
    if (status != MACE_OK)
        printf(" -> Ruta                   : No disponible (%s, Windows %u)\n",
               status_name(status), win32_error);
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
    printf(" -> Indice DXGI            : %u\n", adapter->adapter_index);
    printf(" -> LUID de esta sesion    : %08X:%08X\n",
           (uint32_t)adapter->luid.high_part, adapter->luid.low_part);
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

int main(int argc, char** argv) {
    bool all_processes = false;
    if (argc == 2 && strcmp(argv[1], "--all-processes") == 0)
        all_processes = true;
    else if (argc == 2 && strcmp(argv[1], "--help") == 0) {
        puts("Uso: test_mace.exe [--all-processes]");
        puts("Sin opcion muestra diez procesos; --all-processes imprime todos los elegibles.");
        puts("No termina procesos ni modifica configuraciones del equipo.");
        return 0;
    } else if (argc != 1) {
        fputs("Opcion no reconocida. Use --help.\n", stderr);
        return 2;
    }
    if (!setlocale(LC_ALL, ".UTF-8")) setlocale(LC_ALL, "");
    DWORD console_mode = 0;
    if (GetConsoleMode(GetStdHandle(STD_OUTPUT_HANDLE), &console_mode))
        SetConsoleOutputCP(CP_UTF8);
    int errors = 0;

    printf("=== DIAGNOSTICO DE SUBSISTEMA NATIVO (MACE) ===\n");
    MaceAbiInfoV2 abi = {0};
    uint32_t abi_size = 0;
    MaceStatus abi_status = CP_GetAbiInfo(&abi, sizeof(abi), &abi_size);
    if (abi_status != MACE_OK || abi_size != sizeof(abi) ||
        abi.abi_major != 1 || abi.abi_minor < 4) {
        printf(" [!] DLL incompatible (estado %d, ABI %u.%u).\n",
               abi_status, abi.abi_major, abi.abi_minor);
        return 1;
    }
    printf(" -> DLL                    : %u.%u.%u (ABI %u.%u)\n",
           abi.build_major, abi.build_minor, abi.build_patch,
           abi.abi_major, abi.abi_minor);
    printf(" -> Proceso de prueba      : PID %lu\n", GetCurrentProcessId());
    printf(" -> Administrador          : %s\n", elevation_name());
    printf(" -> Arquitectura           : x64\n");
    printf(" -> Sensores opcionales    : 'No disponible' es un resultado normal.\n");

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
        MaceStatus warmup_status = CP_PollCpuTelemetryV2(&cpu, sizeof(cpu));
        if (warmup_status != MACE_OK)
            printf(" [!] CPU: primera muestra (%s, codigo %d).\n",
                   status_name(warmup_status), warmup_status);
        for (uint32_t i = 0; i < gpu_count; ++i) {
            MaceGpuTelemetryV2 warmup = {0};
            MaceStatus gpu_warmup = CP_PollGpuTelemetryV2(&adapters[i].luid,
                                                           &warmup, sizeof(warmup));
            if (gpu_warmup != MACE_OK)
                printf(" [!] GPU %u: primera muestra (%s, codigo %d).\n",
                       i + 1, status_name(gpu_warmup), gpu_warmup);
        }
        Sleep(1000);

        MaceStatus cpu_result = CP_PollCpuTelemetryV2(&cpu, sizeof(cpu));
        if (cpu_result == MACE_OK) print_cpu(&cpu);
        else {
            printf(" [!] Error al leer la CPU (%s, codigo %d).\n",
                   status_name(cpu_result), cpu_result);
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
                    printf(" [!] Error al leer sus sensores (%s, codigo %d).\n",
                           status_name(gpu_result), gpu_result);
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

    puts("\n Consumo total del equipo: No medido por esta DLL.");
    puts(" Las potencias CPU/GPU no equivalen al consumo del enchufe.");

    printf("\n------------------------------------------------------------\n");
    puts("[2] Memoria RAM fisica del sistema...");
    MaceSystemMemoryV2 memory = {0};
    MaceStatus memory_status = CP_PollSystemMemoryV2(&memory, sizeof(memory));
    if (memory_status == MACE_OK) {
        print_memory_metric("RAM total", memory.total_bytes);
        print_memory_metric("RAM disponible", memory.available_bytes);
        if (memory.total_bytes.state == MACE_METRIC_VALID &&
            memory.available_bytes.state == MACE_METRIC_VALID &&
            memory.total_bytes.value >= memory.available_bytes.value)
            printf(" -> RAM en uso             : %.1f MiB (calculada)\n",
                   (double)(memory.total_bytes.value - memory.available_bytes.value) /
                   (1024.0 * 1024.0));
    } else {
        printf(" [!] Error al leer RAM (%s, codigo %d).\n",
               status_name(memory_status), memory_status);
        errors = 1;
    }

    printf("\n------------------------------------------------------------\n");
    puts("[3] Procesos de usuario (API V2, sin limite fijo de 128)...");
    MaceProcessInfoV2* processes = NULL;
    uint32_t count = 0, skipped = 0, process_error = 0;
    MaceStatus listed = enumerate_processes(&processes, &count, &skipped,
                                            &process_error);
    if (listed == MACE_OK) {
        printf(" -> Procesos elegibles     : %u\n", count);
        printf(" -> Omitidos por consulta  : %u\n", skipped);
        uint32_t limit = all_processes || count < 10 ? count : 10;
        for (uint32_t i = 0; i < limit; ++i) print_process(&processes[i]);
        if (limit < count)
            printf("\n... %u procesos mas. Use --all-processes para mostrarlos.\n",
                   count - limit);
        free(processes);
    } else {
        printf(" [!] Error al enumerar procesos (%s, codigo %d, Windows %u).\n",
               status_name(listed), listed, process_error);
        errors = 1;
    }

    printf("\n------------------------------------------------------------\n");
    printf("[4] Comprobando extensiones con este proceso de prueba...\n");
    MaceProcessIdentityV2 self = {0};
    uint32_t win32_error = 0;
    if (CP_GetProcessIdentityV2(GetCurrentProcessId(), &self, sizeof(self),
                                 &win32_error) == MACE_OK) {
        MaceProcessSampleV2 sample = {0};
        MaceStatus sampled = CP_SampleProcessV2(&self, &sample, sizeof(sample),
                                                &win32_error);
        if (sampled == MACE_OK) {
            printf(" -> Identidad propia       : PID %u, fecha FILETIME %llu\n",
                   self.pid, (unsigned long long)self.creation_time_100ns_utc);
            print_memory_metric("Working Set propio", sample.working_set_bytes);
            print_memory_metric("Private Bytes propios", sample.private_bytes);
            uint64_t blackbox = 0, sequence = 0;
            MaceStatus created = CP_BlackboxCreateV2(NULL, 4, &blackbox,
                                                       &win32_error);
            if (created == MACE_OK) {
                MaceStatus appended = CP_BlackboxAppendV2(blackbox,
                    MACE_RECORD_PROCESS_SAMPLE, &sample, sizeof(sample), &sequence);
                MaceBlackboxRecordV2 record = {0};
                uint32_t required = 0;
                MaceStatus read = appended == MACE_OK ?
                    CP_BlackboxReadV2(blackbox, 0, &record, 1, &required) : appended;
                if (read == MACE_OK && required == 1 &&
                    record.sequence == sequence &&
                    record.record_type == MACE_RECORD_PROCESS_SAMPLE)
                    printf(" -> Caja negra en RAM      : escritura/lectura OK (secuencia %llu)\n",
                           (unsigned long long)sequence);
                else {
                    printf(" [!] Caja negra en RAM     : fallo de prueba (codigo %d).\n", read);
                    errors = 1;
                }
                if (CP_BlackboxCloseV2(blackbox) != MACE_OK) {
                    puts(" [!] Caja negra en RAM     : error al cerrar el token.");
                    errors = 1;
                }
            } else {
                printf(" [!] Caja negra en RAM     : %s (codigo %d, Windows %u).\n",
                       status_name(created), created, win32_error);
                errors = 1;
            }
        } else {
            printf(" -> Muestra de proceso     : no disponible (codigo %d, Windows %u)\n",
                   sampled, win32_error);
            errors = 1;
        }
        MaceResponsivenessV2 response = {0};
        MaceStatus checked = CP_CheckProcessResponsivenessV2(&self, &response,
                                                               sizeof(response));
        if (checked == MACE_OK) {
            printf(" -> Ventanas visibles      : %u examinadas, %u colgadas\n",
                   response.windows_examined, response.windows_hung);
            printf(" -> Estado de ventanas     : %s",
                   response.outcome == MACE_RESPONSIVENESS_NO_WINDOW ? "Sin ventana" :
                   response.outcome == MACE_RESPONSIVENESS_RESPONDING ? "Responden" :
                   response.outcome == MACE_RESPONSIVENESS_HUNG ? "Alguna colgada" :
                   response.outcome == MACE_RESPONSIVENESS_PROCESS_EXITED ? "Proceso finalizado" :
                   "Error de consulta");
            if (response.outcome == MACE_RESPONSIVENESS_ERROR)
                printf(" (Windows %u)", response.win32_error);
            putchar('\n');
        } else {
            printf(" -> Estado de ventanas     : no disponible (codigo %d)\n", checked);
            errors = 1;
        }
    } else {
        printf(" -> Identidad propia       : no disponible (Windows %u)\n", win32_error);
        errors = 1;
    }

    puts("\nLa prueba no finaliza procesos ni cambia sensores del equipo.");
    printf("\n=== PRUEBA FINALIZADA %s ===\n", errors ? "CON ERRORES" : "CON EXITO");
    return errors;
}
