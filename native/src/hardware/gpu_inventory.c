#define COBJMACROS
#include <windows.h>
#include <dxgi1_2.h>
#include <d3dkmthk.h>
#include <string.h>
#include "gpu_inventory.h"

static bool is_hardware(const DXGI_ADAPTER_DESC1* desc) {
    return (desc->Flags & DXGI_ADAPTER_FLAG_SOFTWARE) == 0;
}

static void fill_adapter(MaceGpuAdapterInfoV2* dst,
                         const DXGI_ADAPTER_DESC1* src, UINT index) {
    memset(dst, 0, sizeof(*dst));
    dst->struct_size = (uint32_t)sizeof(*dst);
    dst->version = MACE_V2_CONTRACT_VERSION;
    dst->luid.low_part = src->AdapterLuid.LowPart;
    dst->luid.high_part = src->AdapterLuid.HighPart;
    dst->vendor_id = src->VendorId;
    dst->device_id = src->DeviceId;
    dst->subsystem_id = src->SubSysId;
    dst->revision = src->Revision;
    dst->dxgi_flags = src->Flags;
    dst->adapter_index = index;
    memcpy(dst->description, src->Description, sizeof(dst->description));
    dst->description[127] = L'\0';
}

MaceStatus GpuInventory_Enumerate(MaceGpuAdapterInfoV2* out_adapters,
                                  uint32_t capacity, uint32_t* out_count) {
    if (out_count == NULL) return MACE_E_INVALID_ARGUMENT;
    *out_count = 0;
    IDXGIFactory1* factory = NULL;
    if (FAILED(CreateDXGIFactory1(&IID_IDXGIFactory1, (void**)&factory)))
        return MACE_E_WINDOWS_ERROR;

    uint32_t count = 0;
    for (UINT i = 0; ; ++i) {
        IDXGIAdapter1* adapter = NULL;
        HRESULT hr = IDXGIFactory1_EnumAdapters1(factory, i, &adapter);
        if (hr == DXGI_ERROR_NOT_FOUND) break;
        if (FAILED(hr)) { IDXGIFactory1_Release(factory); return MACE_E_WINDOWS_ERROR; }
        DXGI_ADAPTER_DESC1 desc = {0};
        hr = IDXGIAdapter1_GetDesc1(adapter, &desc);
        IDXGIAdapter1_Release(adapter);
        if (FAILED(hr)) { IDXGIFactory1_Release(factory); return MACE_E_WINDOWS_ERROR; }
        if (is_hardware(&desc)) ++count;
    }
    *out_count = count;
    if (capacity < count) { IDXGIFactory1_Release(factory); return MACE_E_BUFFER_TOO_SMALL; }
    if (count != 0 && out_adapters == NULL) {
        IDXGIFactory1_Release(factory);
        return MACE_E_INVALID_ARGUMENT;
    }

    uint32_t written = 0;
    for (UINT i = 0; written < count; ++i) {
        IDXGIAdapter1* adapter = NULL;
        HRESULT hr = IDXGIFactory1_EnumAdapters1(factory, i, &adapter);
        if (hr == DXGI_ERROR_NOT_FOUND) break;
        if (FAILED(hr)) { IDXGIFactory1_Release(factory); return MACE_E_WINDOWS_ERROR; }
        DXGI_ADAPTER_DESC1 desc = {0};
        hr = IDXGIAdapter1_GetDesc1(adapter, &desc);
        IDXGIAdapter1_Release(adapter);
        if (FAILED(hr)) { IDXGIFactory1_Release(factory); return MACE_E_WINDOWS_ERROR; }
        if (is_hardware(&desc)) fill_adapter(&out_adapters[written++], &desc, i);
    }
    IDXGIFactory1_Release(factory);
    if (written != count) { *out_count = written; return MACE_E_BUSY; }
    return MACE_OK;
}

MaceStatus GpuInventory_Find(const MaceAdapterLuidV2* luid,
                             MaceGpuAdapterInfoV2* out_adapter) {
    if (luid == NULL || out_adapter == NULL) return MACE_E_INVALID_ARGUMENT;
    IDXGIFactory1* factory = NULL;
    if (FAILED(CreateDXGIFactory1(&IID_IDXGIFactory1, (void**)&factory)))
        return MACE_E_WINDOWS_ERROR;
    MaceStatus result = MACE_E_DEVICE_NOT_FOUND;
    for (UINT i = 0; ; ++i) {
        IDXGIAdapter1* adapter = NULL;
        HRESULT hr = IDXGIFactory1_EnumAdapters1(factory, i, &adapter);
        if (hr == DXGI_ERROR_NOT_FOUND) break;
        if (FAILED(hr)) { result = MACE_E_WINDOWS_ERROR; break; }
        DXGI_ADAPTER_DESC1 desc = {0};
        hr = IDXGIAdapter1_GetDesc1(adapter, &desc);
        IDXGIAdapter1_Release(adapter);
        if (FAILED(hr)) { result = MACE_E_WINDOWS_ERROR; break; }
        if (is_hardware(&desc) && desc.AdapterLuid.LowPart == luid->low_part &&
            desc.AdapterLuid.HighPart == luid->high_part) {
            fill_adapter(out_adapter, &desc, i);
            result = MACE_OK;
            break;
        }
    }
    IDXGIFactory1_Release(factory);
    return result;
}

bool GpuInventory_GetPciAddress(const MaceAdapterLuidV2* luid,
                                uint32_t* bus, uint32_t* device,
                                uint32_t* function) {
    if (luid == NULL || bus == NULL || device == NULL || function == NULL)
        return false;
    D3DKMT_OPENADAPTERFROMLUID open = {0};
    open.AdapterLuid.LowPart = luid->low_part;
    open.AdapterLuid.HighPart = luid->high_part;
    if (D3DKMTOpenAdapterFromLuid(&open) != 0) return false;
    D3DKMT_ADAPTERADDRESS address = {0};
    D3DKMT_QUERYADAPTERINFO query = {0};
    query.hAdapter = open.hAdapter;
    query.Type = KMTQAITYPE_ADAPTERADDRESS;
    query.pPrivateDriverData = &address;
    query.PrivateDriverDataSize = sizeof(address);
    bool ok = D3DKMTQueryAdapterInfo(&query) == 0;
    D3DKMT_CLOSEADAPTER close = {0};
    close.hAdapter = open.hAdapter;
    D3DKMTCloseAdapter(&close);
    if (ok) {
        *bus = address.BusNumber;
        *device = address.DeviceNumber;
        *function = address.FunctionNumber;
    }
    return ok;
}
