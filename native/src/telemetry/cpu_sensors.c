#include <windows.h>
#include <stdbool.h>
#include "cpu_sensors.h"

static unsigned long long filetime_to_u64(const FILETIME* ft) {
    ULARGE_INTEGER u;
    u.LowPart = ft->dwLowDateTime;
    u.HighPart = ft->dwHighDateTime;
    return u.QuadPart;
}

/* Estado diferencial protegido por MaceRuntime_BeginPoll. */
static unsigned long long g_prev_idle = 0;
static unsigned long long g_prev_kernel = 0;
static unsigned long long g_prev_user = 0;
static bool g_have_prev = false;
static bool g_last_usage_valid = false;

/*
 * GetSystemTimes entrega tiempo ocioso, de kernel y de usuario acumulados. El de
 * kernel INCLUYE el ocioso, asi que el trabajo util del intervalo es
 * (dKernel + dUser) - dIdle sobre (dKernel + dUser). La primera llamada informa 0 %.
 */
static float ReadCpuUsagePercent(void) {
    FILETIME idle_ft, kernel_ft, user_ft;
    if (!GetSystemTimes(&idle_ft, &kernel_ft, &user_ft)) {
        g_last_usage_valid = false;
        return 0.0f;
    }

    unsigned long long idle = filetime_to_u64(&idle_ft);
    unsigned long long kernel = filetime_to_u64(&kernel_ft);
    unsigned long long user = filetime_to_u64(&user_ft);

    float usage = 0.0f;
    g_last_usage_valid = false;
    if (g_have_prev && idle >= g_prev_idle && kernel >= g_prev_kernel &&
        user >= g_prev_user) {
        unsigned long long d_idle = idle - g_prev_idle;
        unsigned long long d_kernel = kernel - g_prev_kernel;
        unsigned long long d_user = user - g_prev_user;
        unsigned long long d_total = d_kernel + d_user; // El tiempo kernel ya incluye idle

        if (d_total > 0 && d_idle <= d_total) {
            unsigned long long busy = d_total - d_idle;
            usage = (float)((double)busy * 100.0 / (double)d_total);
            if (usage < 0.0f) usage = 0.0f;
            if (usage > 100.0f) usage = 100.0f;
            g_last_usage_valid = true;
        }
    }

    g_prev_idle = idle;
    g_prev_kernel = kernel;
    g_prev_user = user;
    g_have_prev = true;

    return usage;
}

void CpuSensors_Shutdown(void) {
    g_prev_idle = 0;
    g_prev_kernel = 0;
    g_prev_user = 0;
    g_have_prev = false;
    g_last_usage_valid = false;
}

bool CpuSensors_UsageReady(void) { return g_last_usage_valid; }

void CpuSensors_Poll(float* out_usage) {
    if (out_usage) {
        *out_usage = ReadCpuUsagePercent();
    }
}
