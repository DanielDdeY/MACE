#include "mace_api.h"
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define CHECK(x) do { if (!(x)) { \
    fprintf(stderr, "check failed at line %d: %s\n", __LINE__, #x); return 1; \
} } while (0)

int main(void) {
    uint32_t count = 0, skipped = 0, error = 0;
    MaceStatus status = CP_EnumerateUserlandProcessesV2(NULL, 0, &count,
                                                         &skipped, &error);
    CHECK(status == MACE_E_BUFFER_TOO_SMALL || (status == MACE_OK && count == 0));
    CHECK(error == 0);
    if (count) {
        MaceProcessInfoV2 sentinel;
        memset(&sentinel, 0xA5, sizeof(sentinel));
        CHECK(CP_EnumerateUserlandProcessesV2(&sentinel, 0, &count,
                                               &skipped, &error) == MACE_E_BUFFER_TOO_SMALL);
        const unsigned char* bytes = (const unsigned char*)&sentinel;
        for (size_t i = 0; i < sizeof(sentinel); ++i) CHECK(bytes[i] == 0xA5);
    }
    uint32_t capacity = count + 64;
    MaceProcessInfoV2* entries = NULL;
    for (int attempt = 0; attempt < 3; ++attempt) {
        entries = malloc((size_t)capacity * sizeof(*entries));
        CHECK(entries != NULL);
        status = CP_EnumerateUserlandProcessesV2(entries, capacity, &count,
                                                   &skipped, &error);
        if (status != MACE_E_BUFFER_TOO_SMALL) break;
        free(entries);
        entries = NULL;
        capacity = count + 64;
    }
    CHECK(status == MACE_OK && error == 0);
    for (uint32_t i = 0; i < count; ++i) {
        CHECK(entries[i].struct_size == sizeof(*entries));
        CHECK(entries[i].version == MACE_V2_CONTRACT_VERSION);
        CHECK(entries[i].identity.pid > 4);
        CHECK(entries[i].identity.creation_time_100ns_utc != 0);
        CHECK(entries[i].name[0] != L'\0');
        CHECK(entries[i].working_set_bytes.state == MACE_METRIC_VALID ||
              entries[i].working_set_bytes.state == MACE_METRIC_ACCESS_DENIED ||
              entries[i].working_set_bytes.state == MACE_METRIC_READ_FAILED);
    }

    MaceProcessIdentityV2 self = {0};
    CHECK(CP_GetProcessIdentityV2(GetCurrentProcessId(), &self,
          sizeof(self) - 1, &error) == MACE_E_BUFFER_TOO_SMALL);
    CHECK(CP_GetProcessIdentityV2(GetCurrentProcessId(), &self,
          sizeof(self), &error) == MACE_OK);
    CHECK(self.pid == GetCurrentProcessId() && self.creation_time_100ns_utc != 0);
    CHECK(CP_TerminateProcessV2(&self, &error) == MACE_E_PROTECTED_PROCESS);
    CHECK(CP_TerminateProcess(GetCurrentProcessId()) == ERROR_ACCESS_DENIED);
    uint32_t required = 0;
    CHECK(CP_GetProcessImagePathV2(&self, NULL, 0, &required, &error)
          == MACE_E_PROTECTED_PROCESS);
    CHECK(CP_GetProcessIdentityV2(UINT32_MAX, &self, sizeof(self), &error)
          == MACE_E_PROCESS_EXITED);
    CHECK(error == ERROR_INVALID_PARAMETER);
    MaceProcessIdentityV2 altered = self;
    ++altered.creation_time_100ns_utc;
    CHECK(CP_GetProcessImagePathV2(&altered, NULL, 0, &required, &error)
          == MACE_E_PID_REUSED);

    bool path_checked = false;
    for (uint32_t i = 0; i < count; ++i) {
        required = 0;
        status = CP_GetProcessImagePathV2(&entries[i].identity, NULL, 0,
                                           &required, &error);
        if (status != MACE_E_BUFFER_TOO_SMALL) continue;
        CHECK(required > 2);
        wchar_t sentinel = L'X';
        CHECK(CP_GetProcessImagePathV2(&entries[i].identity, &sentinel, 1,
              &required, &error) == MACE_E_BUFFER_TOO_SMALL);
        CHECK(sentinel == L'X');
        wchar_t* path = malloc((size_t)required * sizeof(wchar_t));
        CHECK(path != NULL);
        status = CP_GetProcessImagePathV2(&entries[i].identity, path,
                                            required, &required, &error);
        if (status == MACE_OK) {
            CHECK(path[0] != L'\0' && path[required - 1] == L'\0');
            path_checked = true;
            free(path);
            break;
        }
        free(path);
    }
    CHECK(count == 0 || path_checked);
    free(entries);
    puts("V2 process identities, buffer boundaries, paths and protected self: OK");
    return 0;
}
