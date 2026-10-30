#include <windows.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include "mace_types_v2.h"
#include "igcl_api.h"

static HMODULE module;
static ctl_api_handle_t api;
static ctl_result_t (CTL_APICALL *close_fn)(ctl_api_handle_t);
static ctl_result_t (CTL_APICALL *enumerate_fn)(ctl_api_handle_t, uint32_t*, ctl_device_adapter_handle_t*);
static ctl_result_t (CTL_APICALL *properties_fn)(ctl_device_adapter_handle_t, ctl_device_adapter_properties_t*);
static ctl_result_t (CTL_APICALL *telemetry_fn)(ctl_device_adapter_handle_t, ctl_power_telemetry_v2_t*);
static ctl_result_t (CTL_APICALL *memory_modules_fn)(ctl_device_adapter_handle_t, uint32_t*, ctl_mem_handle_t*);
static ctl_result_t (CTL_APICALL *memory_properties_fn)(ctl_mem_handle_t, ctl_mem_properties_t*);
static ctl_result_t (CTL_APICALL *memory_state_fn)(ctl_mem_handle_t, ctl_mem_state_t*);

typedef struct {
    MaceAdapterLuidV2 luid;
    double time_s, energy_j, activity_s;
    bool valid;
} IgclPrevious;
static IgclPrevious previous[16];

static void clear_functions(void) {
    close_fn = NULL;
    enumerate_fn = NULL;
    properties_fn = NULL;
    telemetry_fn = NULL;
    memory_modules_fn = NULL;
    memory_properties_fn = NULL;
    memory_state_fn = NULL;
}

static MaceMetricState igcl_state(ctl_result_t result) {
    if (result == CTL_RESULT_ERROR_DEVICE_LOST ||
        result == CTL_RESULT_ERROR_DEVICE_UNAVAILABLE)
        return MACE_METRIC_DEVICE_INACTIVE;
    if (result == CTL_RESULT_ERROR_INSUFFICIENT_PERMISSIONS)
        return MACE_METRIC_ACCESS_DENIED;
    return MACE_METRIC_READ_FAILED;
}

bool Igcl_Init(void) {
    if (api) return true;
    module = LoadLibraryExW(L"igcl.dll", NULL, LOAD_LIBRARY_SEARCH_SYSTEM32);
    if (!module) return false;
    ctl_result_t (CTL_APICALL *init_fn)(ctl_init_args_t*, ctl_api_handle_t*) = NULL;
#define RESOLVE(dst, name) do { FARPROC symbol = GetProcAddress(module, name); \
    memcpy(&(dst), &symbol, sizeof(symbol)); } while (0)
    RESOLVE(init_fn, "ctlInit");
    RESOLVE(close_fn, "ctlClose");
    RESOLVE(enumerate_fn, "ctlEnumerateDevices");
    RESOLVE(properties_fn, "ctlGetDeviceProperties");
    RESOLVE(telemetry_fn, "ctlPowerTelemetryGetV2");
    RESOLVE(memory_modules_fn, "ctlEnumMemoryModules");
    RESOLVE(memory_properties_fn, "ctlMemoryGetProperties");
    RESOLVE(memory_state_fn, "ctlMemoryGetState");
#undef RESOLVE
    if (!init_fn || !close_fn || !enumerate_fn || !properties_fn || !telemetry_fn)
        goto failed;
    ctl_init_args_t args = {0};
    args.Size = sizeof(args);
    args.Version = 0;
    args.AppVersion = CTL_IMPL_VERSION;
    args.flags = CTL_INIT_FLAG_USE_LEVEL_ZERO;
    if (init_fn(&args, &api) != CTL_RESULT_SUCCESS || !api) goto failed;
    return true;
failed:
    if (api && close_fn) close_fn(api);
    if (module) FreeLibrary(module);
    api = NULL;
    module = NULL;
    clear_functions();
    return false;
}

void Igcl_Shutdown(void) {
    if (api && close_fn) close_fn(api);
    if (module) FreeLibrary(module);
    api = NULL;
    module = NULL;
    clear_functions();
    memset(previous, 0, sizeof(previous));
}

static double numeric_value(const ctl_oc_telemetry_item_t* item) {
    switch (item->type) {
    case CTL_DATA_TYPE_DOUBLE: return item->value.datadouble;
    case CTL_DATA_TYPE_FLOAT: return item->value.datafloat;
    case CTL_DATA_TYPE_UINT64: return (double)item->value.datau64;
    case CTL_DATA_TYPE_INT64: return (double)item->value.data64;
    case CTL_DATA_TYPE_UINT32: return item->value.datau32;
    case CTL_DATA_TYPE_INT32: return item->value.data32;
    default: return 0.0;
    }
}

static bool item_valid(const ctl_oc_telemetry_item_t* item, ctl_units_t units) {
    return item->bSupported && item->units == units &&
           item->type <= CTL_DATA_TYPE_DOUBLE;
}

static IgclPrevious* slot_for(const MaceAdapterLuidV2* luid) {
    IgclPrevious* empty = NULL;
    for (unsigned i = 0; i < 16; ++i) {
        if (previous[i].valid &&
            previous[i].luid.low_part == luid->low_part &&
            previous[i].luid.high_part == luid->high_part) return &previous[i];
        if (!previous[i].valid && !empty) empty = &previous[i];
    }
    return empty;
}

bool Igcl_PollAdapter(const MaceGpuAdapterInfoV2* adapter,
                      MaceGpuTelemetryV2* out) {
    if (!api || adapter->vendor_id != 0x8086u) return false;
    uint32_t count = 0;
    if (enumerate_fn(api, &count, NULL) != CTL_RESULT_SUCCESS || count == 0 || count > 64)
        return false;
    ctl_device_adapter_handle_t devices[64] = {0};
    if (enumerate_fn(api, &count, devices) != CTL_RESULT_SUCCESS) return false;
    ctl_device_adapter_handle_t matched = NULL;
    for (uint32_t i = 0; i < count; ++i) {
        LUID luid = {0};
        ctl_device_adapter_properties_t props = {0};
        props.Size = sizeof(props);
        props.Version = 0;
        props.pDeviceID = &luid;
        props.device_id_size = sizeof(luid);
        if (properties_fn(devices[i], &props) == CTL_RESULT_SUCCESS &&
            props.pci_vendor_id == 0x8086u &&
            luid.LowPart == adapter->luid.low_part &&
            luid.HighPart == adapter->luid.high_part) {
            matched = devices[i];
            break;
        }
    }
    if (!matched) return false;
    ctl_power_telemetry_v2_t sample = {0};
    sample.Size = sizeof(sample);
    sample.Version = 1;
    ctl_result_t telemetry_result = telemetry_fn(matched, &sample);
    out->provider_id = MACE_PROVIDER_INTEL_IGCL;
    out->power_scope = MACE_POWER_SCOPE_GPU_CHIP;
    if (telemetry_result != CTL_RESULT_SUCCESS) {
        MaceMetricState state = igcl_state(telemetry_result);
        out->temperature_c.state = state;
        out->power_w.state = state;
        out->usage_percent.state = state;
        out->fan_rpm.state = state;
        return true;
    }
    if (item_valid(&sample.gpuCurrentTemperature, CTL_UNITS_TEMPERATURE_CELSIUS)) {
        out->temperature_c.value = (float)numeric_value(&sample.gpuCurrentTemperature);
        out->temperature_c.state = MACE_METRIC_VALID;
    } else out->temperature_c.state = MACE_METRIC_UNSUPPORTED;
    if (item_valid(&sample.fanSpeed[0], CTL_UNITS_ANGULAR_SPEED_RPM)) {
        out->fan_rpm.value = (uint32_t)numeric_value(&sample.fanSpeed[0]);
        out->fan_rpm.state = MACE_METRIC_VALID;
    } else out->fan_rpm.state = MACE_METRIC_UNSUPPORTED;
    bool have_time = item_valid(&sample.timeStamp, CTL_UNITS_TIME_SECONDS);
    bool have_energy = item_valid(&sample.gpuEnergyCounter, CTL_UNITS_ENERGY_JOULES);
    bool have_activity = item_valid(&sample.globalActivityCounter, CTL_UNITS_TIME_SECONDS);
    IgclPrevious* slot = slot_for(&adapter->luid);
    if (slot && have_time) {
        double now = numeric_value(&sample.timeStamp);
        double energy = numeric_value(&sample.gpuEnergyCounter);
        double activity = numeric_value(&sample.globalActivityCounter);
        if (slot->valid && now > slot->time_s + 0.05) {
            double seconds = now - slot->time_s;
            if (have_energy && energy >= slot->energy_j) {
                out->power_w.value = (float)((energy - slot->energy_j) / seconds);
                out->power_w.state = MACE_METRIC_VALID;
            }
            if (have_activity && activity >= slot->activity_s) {
                double percent = (activity - slot->activity_s) * 100.0 / seconds;
                if (percent > 100.0) percent = 100.0;
                out->usage_percent.value = (float)percent;
                out->usage_percent.state = MACE_METRIC_VALID;
            }
        } else {
            if (have_energy) out->power_w.state = MACE_METRIC_WARMING_UP;
            if (have_activity) out->usage_percent.state = MACE_METRIC_WARMING_UP;
        }
        slot->luid = adapter->luid;
        slot->time_s = now;
        slot->energy_j = energy;
        slot->activity_s = activity;
        slot->valid = true;
    }
    if (!have_energy) out->power_w.state = MACE_METRIC_UNSUPPORTED;
    if (!have_activity) out->usage_percent.state = MACE_METRIC_UNSUPPORTED;
    if (memory_modules_fn && memory_properties_fn && memory_state_fn) {
        uint32_t module_count = 0;
        if (memory_modules_fn(matched, &module_count, NULL) == CTL_RESULT_SUCCESS &&
            module_count > 0 && module_count <= 16) {
            ctl_mem_handle_t modules[16] = {0};
            if (memory_modules_fn(matched, &module_count, modules) == CTL_RESULT_SUCCESS) {
                uint64_t used = 0, total = 0;
                for (uint32_t i = 0; i < module_count; ++i) {
                    ctl_mem_properties_t props = {0};
                    ctl_mem_state_t state = {0};
                    props.Size = sizeof(props);
                    state.Size = sizeof(state);
                    if (memory_properties_fn(modules[i], &props) != CTL_RESULT_SUCCESS ||
                        props.location != CTL_MEM_LOC_DEVICE ||
                        memory_state_fn(modules[i], &state) != CTL_RESULT_SUCCESS ||
                        state.free > state.size) continue;
                    used += state.size - state.free;
                    total += state.size;
                }
                if (total != 0) {
                    out->video_memory_used_bytes.value = used;
                    out->video_memory_used_bytes.state = MACE_METRIC_VALID;
                    out->video_memory_total_bytes.value = total;
                    out->video_memory_total_bytes.state = MACE_METRIC_VALID;
                }
            }
        }
    }
    return true;
}
