#include <windows.h>
#include <winver.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "mace_api.h"

typedef MaceStatus (*GetAbiInfoFn)(MaceAbiInfoV2*, uint32_t, uint32_t*);

static int check_version_resource(const wchar_t* dll_path) {
    DWORD unused = 0;
    DWORD size = GetFileVersionInfoSizeW(dll_path, &unused);
    if (size == 0) {
        fprintf(stderr, "Missing Windows version resource (error %lu)\n", GetLastError());
        return 1;
    }

    void* data = malloc(size);
    if (data == NULL) return 1;
    VS_FIXEDFILEINFO* fixed = NULL;
    UINT fixed_size = 0;
    BOOL ok = GetFileVersionInfoW(dll_path, 0, size, data) &&
              VerQueryValueW(data, L"\\", (void**)&fixed, &fixed_size);
    int failed = !ok || fixed == NULL || fixed_size < sizeof(*fixed) ||
                 fixed->dwSignature != 0xFEEF04BD ||
                 HIWORD(fixed->dwFileVersionMS) != 1 ||
                 LOWORD(fixed->dwFileVersionMS) != 4 ||
                 HIWORD(fixed->dwFileVersionLS) != 2 ||
                 LOWORD(fixed->dwFileVersionLS) != 0;
    if (failed) fprintf(stderr, "Incorrect Windows version resource\n");
    free(data);
    return failed;
}

int wmain(int argc, wchar_t** argv) {
    if (argc != 2) {
        fputs("Usage: test_abi_exports <absolute DLL path>\n", stderr);
        return 2;
    }

    HMODULE library = LoadLibraryExW(argv[1], NULL,
        LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
    if (library == NULL) {
        fprintf(stderr, "Could not load DLL (error %lu)\n", GetLastError());
        return 1;
    }

    const char* symbols[] = {
        "CP_InitializeTelemetry",
        "CP_ShutdownTelemetry",
        "CP_PollTelemetry",
        "CP_EnumerateUserlandProcesses",
        "CP_TerminateProcess",
        "CP_CheckProcessResponsiveness",
        "CP_InitSharedTelemetryBuffer",
        "CP_GetAbiInfo",
        "CP_EnumerateGpuAdapters",
        "CP_PollCpuTelemetryV2",
        "CP_PollGpuTelemetryV2",
        "CP_PollSystemMemoryV2",
        "CP_EnumerateUserlandProcessesV2",
        "CP_GetProcessIdentityV2",
        "CP_GetProcessImagePathV2",
        "CP_TerminateProcessV2",
        "CP_SampleProcessV2",
        "CP_CheckProcessResponsivenessV2",
        "CP_BlackboxCreateV2",
        "CP_BlackboxGetStatsV2",
        "CP_BlackboxAppendV2",
        "CP_BlackboxReadV2",
        "CP_BlackboxCloseV2",
        "CP_CloseSharedTelemetryBuffer"
    };
    for (size_t i = 0; i < sizeof(symbols) / sizeof(symbols[0]); ++i) {
        if (GetProcAddress(library, symbols[i]) == NULL) {
            fprintf(stderr, "Missing export: %s\n", symbols[i]);
            FreeLibrary(library);
            return 1;
        }
    }

    union {
        FARPROC raw;
        GetAbiInfoFn typed;
    } getter;
    getter.raw = GetProcAddress(library, "CP_GetAbiInfo");

    uint32_t required = 0;
    if (getter.typed(NULL, 0, &required) != MACE_E_BUFFER_TOO_SMALL ||
        required != sizeof(MaceAbiInfoV2)) {
        fputs("Size query failed\n", stderr);
        FreeLibrary(library);
        return 1;
    }
    if (getter.typed(NULL, required, &required) != MACE_E_INVALID_ARGUMENT ||
        getter.typed(NULL, 0, NULL) != MACE_E_INVALID_ARGUMENT) {
        fputs("Argument validation failed\n", stderr);
        FreeLibrary(library);
        return 1;
    }

    struct {
        MaceAbiInfoV2 info;
        uint8_t canary[16];
    } buffer;
    memset(&buffer, 0xA5, sizeof(buffer));
    if (getter.typed(&buffer.info, (uint32_t)sizeof(buffer.info) - 1, &required)
            != MACE_E_BUFFER_TOO_SMALL) {
        fputs("Short buffer not rejected\n", stderr);
        FreeLibrary(library);
        return 1;
    }
    for (size_t i = 0; i < sizeof(buffer); ++i) {
        if (((const uint8_t*)&buffer)[i] != 0xA5) {
            fputs("Short buffer was modified\n", stderr);
            FreeLibrary(library);
            return 1;
        }
    }

    if (getter.typed(&buffer.info, (uint32_t)sizeof(buffer), &required) != MACE_OK ||
        required != sizeof(MaceAbiInfoV2) ||
        buffer.info.struct_size != sizeof(MaceAbiInfoV2) ||
        buffer.info.version != MACE_V2_CONTRACT_VERSION ||
        buffer.info.abi_major != 1 || buffer.info.abi_minor != 4 ||
        buffer.info.process_data_size != sizeof(ProcessData) ||
        buffer.info.telemetry_data_size != sizeof(TelemetryData) ||
        buffer.info.blackbox_header_size != sizeof(BlackboxSharedHeader) ||
        buffer.info.wchar_size != 2 || buffer.info.pointer_size != 8 ||
        buffer.info.build_major != 1 || buffer.info.build_minor != 4 ||
        buffer.info.build_patch != 2 ||
        (buffer.info.implemented_capabilities &
          (MACE_CAP_CPU_USAGE | MACE_CAP_GPU_INVENTORY |
           MACE_CAP_GPU_MEMORY | MACE_CAP_SYSTEM_MEMORY |
           MACE_CAP_PROCESS_IDENTITY | MACE_CAP_SAFE_TERMINATION |
           MACE_CAP_PROCESS_SAMPLES | MACE_CAP_WINDOW_RESPONSIVENESS |
           MACE_CAP_BLACKBOX_BUFFER)) !=
          (MACE_CAP_CPU_USAGE | MACE_CAP_GPU_INVENTORY |
           MACE_CAP_GPU_MEMORY | MACE_CAP_SYSTEM_MEMORY |
           MACE_CAP_PROCESS_IDENTITY | MACE_CAP_SAFE_TERMINATION |
           MACE_CAP_PROCESS_SAMPLES | MACE_CAP_WINDOW_RESPONSIVENESS |
           MACE_CAP_BLACKBOX_BUFFER) ||
        buffer.info.reserved0 != 0 || buffer.info.reserved1 != 0) {
        fputs("ABI record contents are incorrect\n", stderr);
        FreeLibrary(library);
        return 1;
    }
    for (size_t i = 0; i < sizeof(buffer.canary); ++i) {
        if (buffer.canary[i] != 0xA5) {
            fputs("ABI query wrote beyond the record\n", stderr);
            FreeLibrary(library);
            return 1;
        }
    }

    int failed = check_version_resource(argv[1]);
    FreeLibrary(library);
    if (failed) return 1;

    puts("24 exports, ABI 1.4, legacy layouts and file version 1.4.2: OK");
    return 0;
}
