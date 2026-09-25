#include <windows.h>
#include <stdbool.h>

/**
 * Convierte un FILETIME (dos DWORD de 32 bits) en un entero de 64 bits.
 * Las horas de GetSystemTimes vienen en unidades de 100 ns.
 */
static unsigned long long filetime_to_u64(const FILETIME* ft) {
    ULARGE_INTEGER u;
    u.LowPart = ft->dwLowDateTime;
    u.HighPart = ft->dwHighDateTime;
    return u.QuadPart;
}

/*
 * Estado entre sondeos para calcular el uso de CPU por diferencia de tiempos.
 * El sondeo de MACE es de un solo hilo (hilo "mace-monitoring" a 1 Hz), por lo
 * que estas estaticas no necesitan sincronizacion adicional.
 */
static unsigned long long g_prev_idle = 0;
static unsigned long long g_prev_kernel = 0;
static unsigned long long g_prev_user = 0;
static bool g_have_prev = false;

/**
 * Uso de CPU real de todo el sistema, en porcentaje [0..100].
 *
 * GetSystemTimes entrega tiempo ocioso, de kernel y de usuario acumulados. El
 * tiempo de kernel INCLUYE el ocioso, asi que el trabajo util del intervalo es
 * (dKernel + dUser) - dIdle sobre el total (dKernel + dUser). En la primera
 * llamada no hay intervalo previo y se informa 0 %.
 */
static float ReadCpuUsagePercent(void) {
    FILETIME idle_ft, kernel_ft, user_ft;
    if (!GetSystemTimes(&idle_ft, &kernel_ft, &user_ft)) {
        return 0.0f;
    }

    unsigned long long idle = filetime_to_u64(&idle_ft);
    unsigned long long kernel = filetime_to_u64(&kernel_ft);
    unsigned long long user = filetime_to_u64(&user_ft);

    float usage = 0.0f;
    if (g_have_prev) {
        unsigned long long d_idle = idle - g_prev_idle;
        unsigned long long d_kernel = kernel - g_prev_kernel;
        unsigned long long d_user = user - g_prev_user;
        unsigned long long d_total = d_kernel + d_user;  // kernel ya incluye idle

        if (d_total > 0) {
            unsigned long long busy = d_total - d_idle;
            usage = (float)((double)busy * 100.0 / (double)d_total);
            if (usage < 0.0f) usage = 0.0f;
            if (usage > 100.0f) usage = 100.0f;
        }
    }

    g_prev_idle = idle;
    g_prev_kernel = kernel;
    g_prev_user = user;
    g_have_prev = true;

    return usage;
}

void CpuSensors_Poll(float* out_temp, float* out_watts, float* out_usage) {
    // Temperatura y energia: la lectura directa de MSR/RAPL desde user-space
    // requiere un controlador privilegiado; se mantiene el fallback pasivo.
    if (out_temp)  *out_temp = 42.0f;
    if (out_watts) *out_watts = 25.5f;

    // Uso de CPU: dato REAL del sistema via GetSystemTimes().
    if (out_usage) *out_usage = ReadCpuUsagePercent();
}
