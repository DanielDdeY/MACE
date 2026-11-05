#include <windows.h>
#include <shlobj.h>
#include <winver.h>

#include <cmath>
#include <filesystem>
#include <string>
#include <vector>

#include "ICPUEx.h"
#include "IDeviceManager.h"
#include "IPlatform.h"
#include "amd_ryzen_provider.h"

namespace {

using GetPlatformFunc = IPlatform& (__stdcall*)();

HMODULE g_module = nullptr;
IPlatform* g_platform = nullptr;
ICPUEx* g_cpu = nullptr;
MaceMetricState g_state = MACE_METRIC_NO_PROVIDER;
MaceMetricState g_cached_state = MACE_METRIC_WARMING_UP;
float g_cached_temperature = 0.0f;
ULONGLONG g_last_query_ms = 0;
bool g_attempted = false;
bool g_queried = false;

std::wstring installed_root() {
    DWORD bytes = 0;
    constexpr wchar_t key[] = L"SOFTWARE\\AMD\\RyzenMasterMonitoringSDK";
    if (RegGetValueW(HKEY_LOCAL_MACHINE, key, L"InstallationPath",
                     RRF_RT_REG_SZ, nullptr, nullptr, &bytes) != ERROR_SUCCESS ||
        bytes < sizeof(wchar_t) || bytes > 32768 * sizeof(wchar_t)) {
        return {};
    }
    std::wstring value(bytes / sizeof(wchar_t), L'\0');
    if (RegGetValueW(HKEY_LOCAL_MACHINE, key, L"InstallationPath",
                     RRF_RT_REG_SZ, nullptr, value.data(), &bytes) != ERROR_SUCCESS) {
        return {};
    }
    value.resize(wcsnlen_s(value.c_str(), value.size()));
    return value;
}

void release_sdk() {
    g_cpu = nullptr;
    if (g_platform) {
        try { g_platform->UnInit(); } catch (...) { /* Cerrar lo restante. */ }
        g_platform = nullptr;
    }
    if (g_module) {
        FreeLibrary(g_module);
        g_module = nullptr;
    }
}

bool expected_sdk_version(const std::filesystem::path& dll) {
    DWORD ignored = 0;
    const DWORD bytes = GetFileVersionInfoSizeW(dll.c_str(), &ignored);
    if (bytes == 0) return false;
    std::vector<BYTE> version_data(bytes);
    VS_FIXEDFILEINFO* info = nullptr;
    UINT info_bytes = 0;
    if (!GetFileVersionInfoW(dll.c_str(), 0, bytes, version_data.data()) ||
        !VerQueryValueW(version_data.data(), L"\\", reinterpret_cast<void**>(&info),
                        &info_bytes) ||
        !info || info_bytes < sizeof(*info) || info->dwSignature != 0xFEEF04BD) {
        return false;
    }
    return HIWORD(info->dwFileVersionMS) == 3 &&
           LOWORD(info->dwFileVersionMS) == 1 &&
           HIWORD(info->dwFileVersionLS) == 1 &&
           LOWORD(info->dwFileVersionLS) == 5478;
}

} // namespace

extern "C" void AmdRyzenProvider_Init(void) {
    if (g_attempted) return;
    g_attempted = true;

    try {
        std::wstring root = installed_root();
        if (root.empty()) return;
        std::filesystem::path sdk_root(root);
        if (!sdk_root.is_absolute()) return;

        std::filesystem::path platform_dll = sdk_root / L"bin" / L"Platform.dll";
        std::filesystem::path device_dll = sdk_root / L"bin" / L"Device.dll";
        if (GetFileAttributesW(platform_dll.c_str()) == INVALID_FILE_ATTRIBUTES ||
            GetFileAttributesW(device_dll.c_str()) == INVALID_FILE_ATTRIBUTES) return;
        if (!expected_sdk_version(platform_dll) || !expected_sdk_version(device_dll)) {
            g_state = MACE_METRIC_UNSUPPORTED;
            return;
        }
        g_module = LoadLibraryExW(platform_dll.c_str(), nullptr,
                                  LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR |
                                  LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
        if (!g_module) return;
        g_state = MACE_METRIC_READ_FAILED;

        auto get_platform = reinterpret_cast<GetPlatformFunc>(
            GetProcAddress(g_module, "GetPlatform"));
        if (!get_platform) return;
        if (!IsUserAnAdmin()) {
            g_state = MACE_METRIC_ACCESS_DENIED;
            return;
        }

        g_platform = &get_platform();
        if (!g_platform->Init(nullptr, true)) {
            g_platform = nullptr;
            return;
        }
        IDevice* device = g_platform->GetIDeviceManager().GetDevice(dtCPU, 0);
        if (!device) {
            g_state = MACE_METRIC_UNSUPPORTED;
            return;
        }
        g_cpu = static_cast<ICPUEx*>(device);
        g_state = MACE_METRIC_WARMING_UP;
    } catch (...) {
        g_state = MACE_METRIC_READ_FAILED;
        release_sdk();
    }
}

extern "C" void AmdRyzenProvider_Shutdown(void) {
    try { release_sdk(); } catch (...) { /* Nunca cruzar la frontera C con excepciones. */ }
    g_state = MACE_METRIC_NO_PROVIDER;
    g_cached_state = MACE_METRIC_WARMING_UP;
    g_cached_temperature = 0.0f;
    g_last_query_ms = 0;
    g_attempted = false;
    g_queried = false;
}

extern "C" void AmdRyzenProvider_Poll(MaceCpuTelemetryV2* sample) {
    if (!sample || !g_attempted || g_state == MACE_METRIC_NO_PROVIDER) return;
    sample->provider_id = MACE_PROVIDER_AMD_RYZEN_SDK;
    if (!g_cpu) {
        sample->temperature_c.state = g_state;
        return;
    }

    const ULONGLONG now = GetTickCount64();
    if (!g_queried || now - g_last_query_ms >= 1000) {
        g_queried = true;
        g_last_query_ms = now;
        CPUParameters parameters{};
        try {
            const int result = g_cpu->GetCPUParameters(parameters);
            if (result == 4) {
                g_cached_state = MACE_METRIC_UNSUPPORTED;
            } else if (result != 0 || !std::isfinite(parameters.dTemperature) ||
                       parameters.dTemperature <= 0.0 ||
                       parameters.dTemperature > 150.0) {
                g_cached_state = MACE_METRIC_READ_FAILED;
            } else {
                g_cached_temperature = static_cast<float>(parameters.dTemperature);
                g_cached_state = MACE_METRIC_VALID;
            }
        } catch (...) {
            g_cached_state = MACE_METRIC_READ_FAILED;
        }
    }
    sample->temperature_c.state = g_cached_state;
    if (g_cached_state == MACE_METRIC_VALID)
        sample->temperature_c.value = g_cached_temperature;
}
