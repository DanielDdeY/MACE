#include <stdio.h>
#include <locale.h>
#include "mace_api.h"

int main(void) {
    // Configurar la consola para imprimir caracteres Unicode correctamente
    setlocale(LC_ALL, "");

    printf("=== PRUEBA DE SUBSISTEMA NATIVO (MACE) ===\n\n");

    // 1. Probar telemetría de sensores
    printf("[1] Inicializando telemetria...\n");
    CP_InitializeTelemetry();

    TelemetryData telemetry = {0};
    if (CP_PollTelemetry(&telemetry) == 0) {
        printf(" -> CPU Temp : %.1f C\n", telemetry.cpu_temp);
        printf(" -> CPU Power: %.1f W\n", telemetry.cpu_watts);
        printf(" -> GPU Disponible: %s\n", telemetry.is_gpu_available ? "SI (NVIDIA detectada)" : "NO (Fallback)");
        if (telemetry.is_gpu_available) {
            printf(" -> GPU Temp : %.1f C\n", telemetry.gpu_temp);
            printf(" -> GPU Power: %.1f W\n", telemetry.gpu_watts);
            printf(" -> GPU Fan  : %u RPM\n", telemetry.gpu_fan_rpm);
        }
    } else {
        printf(" [!] Error al leer telemetria.\n");
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

        uint32_t limit = count < 10 ? count : 10; // Mostrar los primeros 10
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
    }

    CP_ShutdownTelemetry();
    printf("\n=== PRUEBA FINALIZADA CON EXITO ===\n");
    return 0;
}