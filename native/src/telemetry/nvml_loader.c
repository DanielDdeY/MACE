#include <windows.h>
#include <stdint.h>
#include <stdbool.h>

typedef int nvmlReturn_t;
typedef void* nvmlDevice_t;

#define NVML_SUCCESS 0
#define NVML_TEMPERATURE_GPU 0

typedef nvmlReturn_t (*fn_nvmlInit_v2)(void);
typedef nvmlReturn_t (*fn_nvmlShutdown)(void);
typedef nvmlReturn_t (*fn_nvmlDeviceGetHandleByIndex_v2)(unsigned int, nvmlDevice_t*);
typedef nvmlReturn_t (*fn_nvmlDeviceGetTemperature)(nvmlDevice_t, int, unsigned int*);
typedef nvmlReturn_t (*fn_nvmlDeviceGetPowerUsage)(nvmlDevice_t, unsigned int*);
typedef nvmlReturn_t (*fn_nvmlDeviceGetFanSpeed)(nvmlDevice_t, unsigned int*);

static HMODULE g_nvml_module = NULL;
static nvmlDevice_t g_device = NULL;
static bool g_nvml_initialized = false;

static fn_nvmlInit_v2 p_nvmlInit_v2 = NULL;
static fn_nvmlShutdown p_nvmlShutdown = NULL;
static fn_nvmlDeviceGetHandleByIndex_v2 p_nvmlDeviceGetHandleByIndex_v2 = NULL;
static fn_nvmlDeviceGetTemperature p_nvmlDeviceGetTemperature = NULL;
static fn_nvmlDeviceGetPowerUsage p_nvmlDeviceGetPowerUsage = NULL;
static fn_nvmlDeviceGetFanSpeed p_nvmlDeviceGetFanSpeed = NULL;

bool Nvml_Init(void) {
    if (g_nvml_initialized) return true;

    g_nvml_module = LoadLibraryA("nvml.dll");
    if (!g_nvml_module) return false;

    p_nvmlInit_v2 = (fn_nvmlInit_v2)GetProcAddress(g_nvml_module, "nvmlInit_v2");
    p_nvmlShutdown = (fn_nvmlShutdown)GetProcAddress(g_nvml_module, "nvmlShutdown");
    p_nvmlDeviceGetHandleByIndex_v2 = (fn_nvmlDeviceGetHandleByIndex_v2)GetProcAddress(g_nvml_module, "nvmlDeviceGetHandleByIndex_v2");
    p_nvmlDeviceGetTemperature = (fn_nvmlDeviceGetTemperature)GetProcAddress(g_nvml_module, "nvmlDeviceGetTemperature");
    p_nvmlDeviceGetPowerUsage = (fn_nvmlDeviceGetPowerUsage)GetProcAddress(g_nvml_module, "nvmlDeviceGetPowerUsage");
    p_nvmlDeviceGetFanSpeed = (fn_nvmlDeviceGetFanSpeed)GetProcAddress(g_nvml_module, "nvmlDeviceGetFanSpeed");

    if (!p_nvmlInit_v2 || !p_nvmlShutdown || !p_nvmlDeviceGetHandleByIndex_v2 ||
        !p_nvmlDeviceGetTemperature || !p_nvmlDeviceGetPowerUsage || !p_nvmlDeviceGetFanSpeed) {
        FreeLibrary(g_nvml_module);
        g_nvml_module = NULL;
        return false;
    }

    if (p_nvmlInit_v2() != NVML_SUCCESS) {
        FreeLibrary(g_nvml_module);
        g_nvml_module = NULL;
        return false;
    }

    if (p_nvmlDeviceGetHandleByIndex_v2(0, &g_device) != NVML_SUCCESS) {
        p_nvmlShutdown();
        FreeLibrary(g_nvml_module);
        g_nvml_module = NULL;
        return false;
    }

    g_nvml_initialized = true;
    return true;
}

void Nvml_Shutdown(void) {
    if (g_nvml_initialized && p_nvmlShutdown) {
        p_nvmlShutdown();
    }
    if (g_nvml_module) {
        FreeLibrary(g_nvml_module);
        g_nvml_module = NULL;
    }
    g_nvml_initialized = false;
    g_device = NULL;
}

bool Nvml_PollMetrics(float* out_temp, float* out_watts, uint32_t* out_fan_rpm) {
    if (!g_nvml_initialized || !g_device) return false;

    unsigned int temp = 0;
    unsigned int milliwatts = 0;
    unsigned int fan_speed_pct = 0;

    if (p_nvmlDeviceGetTemperature(g_device, NVML_TEMPERATURE_GPU, &temp) == NVML_SUCCESS) {
        *out_temp = (float)temp;
    } else {
        *out_temp = 0.0f;
    }

    if (p_nvmlDeviceGetPowerUsage(g_device, &milliwatts) == NVML_SUCCESS) {
        *out_watts = (float)milliwatts / 1000.0f;
    } else {
        *out_watts = 0.0f;
    }

    if (p_nvmlDeviceGetFanSpeed(g_device, &fan_speed_pct) == NVML_SUCCESS) {
        *out_fan_rpm = fan_speed_pct;
    } else {
        *out_fan_rpm = 0;
    }

    return true;
}