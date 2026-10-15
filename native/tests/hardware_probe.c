#define COBJMACROS
#include <windows.h>
#include <dxgi1_2.h>
#include <intrin.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

static void print_cpu(void) {
    int regs[4] = {0};
    char vendor[13] = {0};
    char brand[49] = {0};
    __cpuid(regs, 0);
    memcpy(vendor, &regs[1], 4);
    memcpy(vendor + 4, &regs[3], 4);
    memcpy(vendor + 8, &regs[2], 4);

    __cpuid(regs, 1);
    uint32_t signature = (uint32_t)regs[0];
    uint32_t base_family = (signature >> 8) & 0xFu;
    uint32_t family = base_family;
    uint32_t model = (signature >> 4) & 0xFu;
    if (base_family == 0x6u || base_family == 0xFu) model += ((signature >> 16) & 0xFu) << 4;
    if (base_family == 0xFu) family += (signature >> 20) & 0xFFu;

    __cpuid(regs, 0x80000000);
    if ((uint32_t)regs[0] >= 0x80000004u) {
        for (int i = 0; i < 3; ++i) {
            __cpuid(regs, (int)(0x80000002u + (uint32_t)i));
            memcpy(brand + i * 16, regs, 16);
        }
    }

    printf("CPU vendor=%s family=%u model=%u brand=%s\n", vendor, family, model, brand);
}

static const char* gpu_vendor(uint32_t vendor_id) {
    switch (vendor_id) {
        case 0x8086u: return "Intel";
        case 0x1002u: return "AMD";
        case 0x10DEu: return "NVIDIA";
        default: return "Other";
    }
}

static int print_gpus(void) {
    IDXGIFactory1* factory = NULL;
    HRESULT hr = CreateDXGIFactory1(&IID_IDXGIFactory1, (void**)&factory);
    if (FAILED(hr)) {
        printf("DXGI unavailable: HRESULT=0x%08X\n", (unsigned int)hr);
        return 1;
    }

    unsigned int count = 0;
    for (UINT index = 0; ; ++index) {
        IDXGIAdapter1* adapter = NULL;
        hr = IDXGIFactory1_EnumAdapters1(factory, index, &adapter);
        if (hr == DXGI_ERROR_NOT_FOUND) break;
        if (FAILED(hr)) {
            printf("DXGI adapter %u failed: HRESULT=0x%08X\n", index, (unsigned int)hr);
            IDXGIFactory1_Release(factory);
            return 1;
        }

        DXGI_ADAPTER_DESC1 desc = {0};
        hr = IDXGIAdapter1_GetDesc1(adapter, &desc);
        if (SUCCEEDED(hr)) {
            printf("GPU index=%u vendor=%s vendor_id=%04X device_id=%04X software=%u name=%ls\n",
                    index, gpu_vendor(desc.VendorId), desc.VendorId, desc.DeviceId,
                    (desc.Flags & DXGI_ADAPTER_FLAG_SOFTWARE) != 0, desc.Description);
            ++count;
        } else {
            printf("DXGI GetDesc1 failed: HRESULT=0x%08X\n", (unsigned int)hr);
        }
        IDXGIAdapter1_Release(adapter);
    }
    IDXGIFactory1_Release(factory);
    printf("GPU adapters enumerated=%u\n", count);
    return 0;
}

int main(void) {
    print_cpu();
    return print_gpus();
}
