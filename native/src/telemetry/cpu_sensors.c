#include <windows.h>
#include <intrin.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>

#include "cpu_sensors.h"

/* =========================================================================
 * 1. USO DE CPU REAL (%) VÍA GetSystemTimes()
 * ========================================================================= */

static unsigned long long filetime_to_u64(const FILETIME* ft) {
    ULARGE_INTEGER u;
    u.LowPart = ft->dwLowDateTime;
    u.HighPart = ft->dwHighDateTime;
    return u.QuadPart;
}

/*
 * Estado entre sondeos para calcular el uso por diferencia de tiempos. El sondeo
 * de MACE es de un solo hilo ("mace-monitoring" a 1 Hz), asi que no se sincroniza.
 */
static unsigned long long g_prev_idle = 0;
static unsigned long long g_prev_kernel = 0;
static unsigned long long g_prev_user = 0;
static bool g_have_prev = false;

/*
 * GetSystemTimes entrega tiempo ocioso, de kernel y de usuario acumulados. El de
 * kernel INCLUYE el ocioso, asi que el trabajo util del intervalo es
 * (dKernel + dUser) - dIdle sobre (dKernel + dUser). La primera llamada informa 0 %.
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
        unsigned long long d_total = d_kernel + d_user; // El tiempo kernel ya incluye idle

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

/* =========================================================================
 * 2. TELEMETRÍA DE HARDWARE REAL VÍA REGISTROS MSR (WINRING0, OPCIONAL)
 *
 * WinRing0x64.dll NO se distribuye con MACE: se carga solo si el usuario la
 * instala junto a la DLL y ejecuta como administrador. Su driver tiene una
 * vulnerabilidad conocida (CVE-2020-14979) y Windows 11 lo bloquea con la
 * integridad de memoria activa. Sin ella, temperatura y energia se reportan
 * como no disponibles (la UI muestra "N/D") en lugar de inventar valores.
 * ========================================================================= */

// Registros MSR de energia RAPL (Intel Core). AMD Zen usa otros MSR
// (0xC0010299 / 0xC001029B) que este modulo no implementa.
#define MSR_RAPL_POWER_UNIT       0x00000606
#define MSR_PKG_ENERGY_STATUS     0x00000611

// Registros MSR de temperatura (Intel Digital Thermal Sensor). Solo Intel.
#define MSR_IA32_THERM_STATUS     0x0000019C
#define MSR_TEMPERATURE_TARGET    0x000001A2

typedef BOOL (*fn_InitializeOls)(void);
typedef VOID (*fn_DeinitializeOls)(void);
typedef BOOL (*fn_Rdmsr)(DWORD index, DWORD* eax, DWORD* edx);

static HMODULE g_winring0_mod = NULL;
static fn_InitializeOls p_InitializeOls = NULL;
static fn_DeinitializeOls p_DeinitializeOls = NULL;
static fn_Rdmsr p_Rdmsr = NULL;

static bool g_init_attempted = false;
static bool g_sensors_ready = false;
static double g_energy_unit_joules = 0.0;
static uint32_t g_last_energy_raw = 0;
static LARGE_INTEGER g_last_power_time = {0};
static LARGE_INTEGER g_timer_freq = {0};
static float g_cached_watts = 0.0f;

/* Los MSR que se leen aqui son de Intel: en otro fabricante no se intenta. */
static bool IsIntelCpu(void) {
    int regs[4] = {0};
    char vendor[13] = {0};
    __cpuid(regs, 0);
    memcpy(vendor + 0, &regs[1], 4); // EBX
    memcpy(vendor + 4, &regs[3], 4); // EDX
    memcpy(vendor + 8, &regs[2], 4); // ECX
    return strcmp(vendor, "GenuineIntel") == 0;
}

bool CpuSensors_Init(void) {
    // Un solo intento: si WinRing0 no esta, no se reintenta en cada sondeo.
    if (g_init_attempted) return g_sensors_ready;
    g_init_attempted = true;

    if (!IsIntelCpu()) {
        return false;
    }

    QueryPerformanceFrequency(&g_timer_freq);

    g_winring0_mod = LoadLibraryA("WinRing0x64.dll");
    if (!g_winring0_mod) {
        return false;
    }

    p_InitializeOls = (fn_InitializeOls)GetProcAddress(g_winring0_mod, "InitializeOls");
    p_DeinitializeOls = (fn_DeinitializeOls)GetProcAddress(g_winring0_mod, "DeinitializeOls");
    p_Rdmsr = (fn_Rdmsr)GetProcAddress(g_winring0_mod, "Rdmsr");

    if (!p_InitializeOls || !p_DeinitializeOls || !p_Rdmsr || !p_InitializeOls()) {
        FreeLibrary(g_winring0_mod);
        g_winring0_mod = NULL;
        return false;
    }

    // 1. Escala de energía RAPL desde MSR 0x606
    DWORD eax = 0, edx = 0;
    if (p_Rdmsr(MSR_RAPL_POWER_UNIT, &eax, &edx)) {
        uint32_t energy_status_units = (eax >> 8) & 0x1F;
        g_energy_unit_joules = 1.0 / (double)(1ULL << energy_status_units);
    } else {
        g_energy_unit_joules = 0.0;
    }

    // 2. Primera lectura de energía de referencia (el reloj se toma siempre para
    //    que el primer intervalo no se calcule desde el instante 0)
    QueryPerformanceCounter(&g_last_power_time);
    if (p_Rdmsr(MSR_PKG_ENERGY_STATUS, &eax, &edx)) {
        g_last_energy_raw = eax;
    }

    g_sensors_ready = true;
    return true;
}

void CpuSensors_Shutdown(void) {
    if (g_sensors_ready && p_DeinitializeOls) {
        p_DeinitializeOls();
    }
    if (g_winring0_mod) {
        FreeLibrary(g_winring0_mod);
        g_winring0_mod = NULL;
    }
    g_sensors_ready = false;
    g_init_attempted = false;
}

bool CpuSensors_HardwareAvailable(void) {
    return g_sensors_ready;
}

/**
 * Lectura real de temperatura mediante el DTS del procesador.
 * Devuelve 0.0f si no se puede leer el registro (sin inventar datos).
 */
static float ReadCpuTemperature(void) {
    if (!g_sensors_ready) return 0.0f;

    DWORD eax = 0, edx = 0;

    // 1. Obtener TjMax (límite térmico del empaque) en MSR 0x1A2
    uint32_t tj_max = 100;
    if (p_Rdmsr(MSR_TEMPERATURE_TARGET, &eax, &edx)) {
        uint32_t target = (eax >> 16) & 0xFF;
        if (target > 50 && target < 125) {
            tj_max = target;
        }
    }

    // 2. Leer estado térmico actual (MSR 0x19C)
    if (p_Rdmsr(MSR_IA32_THERM_STATUS, &eax, &edx)) {
        // Bit 31: indica si el sensor entrega una lectura válida
        if (eax & 0x80000000) {
            uint32_t delta = (eax >> 16) & 0x7F;
            if (tj_max >= delta) {
                return (float)(tj_max - delta);
            }
        }
    }

    return 0.0f; // Dato estrictamente real: 0.0 si el registro no responde
}

/**
 * Lectura real de potencia acumulada RAPL (Joules / segundo).
 * Devuelve 0.0f si el controlador no responde.
 */
static float ReadCpuPowerWatts(void) {
    if (!g_sensors_ready) return 0.0f;

    if (g_energy_unit_joules <= 0.0) return 0.0f;

    DWORD current_raw = 0, edx = 0;
    LARGE_INTEGER current_time;

    if (!p_Rdmsr(MSR_PKG_ENERGY_STATUS, &current_raw, &edx)) {
        return 0.0f;
    }

    QueryPerformanceCounter(&current_time);

    double delta_sec = (double)(current_time.QuadPart - g_last_power_time.QuadPart) / (double)g_timer_freq.QuadPart;

    if (delta_sec >= 0.2) {
        // Resta sin signo: soporta el desborde del contador de 32 bits
        uint32_t delta_energy_raw = current_raw - g_last_energy_raw;
        double joules = (double)delta_energy_raw * g_energy_unit_joules;

        g_cached_watts = (float)(joules / delta_sec);
        g_last_energy_raw = current_raw;
        g_last_power_time = current_time;
    }

    return g_cached_watts;
}

/* =========================================================================
 * 3. FUNCIÓN DE SONDEO UNIFICADA
 * ========================================================================= */
void CpuSensors_Poll(float* out_temp, float* out_watts, float* out_usage) {
    // 1. Temperatura: lectura DTS real de MSR (0.0f si no es accesible)
    if (out_temp) {
        *out_temp = ReadCpuTemperature();
    }

    // 2. Energía: cálculo diferencial RAPL real (0.0f si no es accesible)
    if (out_watts) {
        *out_watts = ReadCpuPowerWatts();
    }

    // 3. Uso de CPU: cálculo diferencial real de GetSystemTimes()
    if (out_usage) {
        *out_usage = ReadCpuUsagePercent();
    }
}
