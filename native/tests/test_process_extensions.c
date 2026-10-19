#include "mace_api.h"
#include <windows.h>
#include <stdio.h>
#include <string.h>

#define CHECK(x) do { if (!(x)) { \
    fprintf(stderr, "check failed at line %d: %s\n", __LINE__, #x); return 1; \
} } while (0)

int wmain(int argc, wchar_t** argv) {
    (void)argv;
    if (argc > 1) { Sleep(1000); return 0; } /* Controlled helper. */
    MaceProcessIdentityV2 self = {0};
    uint32_t error = 0;
    CHECK(CP_GetProcessIdentityV2(GetCurrentProcessId(), &self, sizeof(self),
                                  &error) == MACE_OK);
    MaceProcessSampleV2 sample;
    memset(&sample, 0xA5, sizeof(sample));
    CHECK(CP_SampleProcessV2(&self, &sample, sizeof(sample) - 1,
                             &error) == MACE_E_BUFFER_TOO_SMALL);
    for (size_t i = 0; i < sizeof(sample); ++i)
        CHECK(((const unsigned char*)&sample)[i] == 0xA5);
    MaceProcessIdentityV2 altered = self;
    ++altered.creation_time_100ns_utc;
    CHECK(CP_SampleProcessV2(&altered, &sample, sizeof(sample),
                             &error) == MACE_E_PID_REUSED);
    for (size_t i = 0; i < sizeof(sample); ++i)
        CHECK(((const unsigned char*)&sample)[i] == 0xA5);
    CHECK(CP_SampleProcessV2(&self, &sample, sizeof(sample), &error) == MACE_OK);
    CHECK(sample.struct_size == sizeof(sample) && sample.version == 2);
    CHECK(sample.identity.pid == self.pid &&
          sample.identity.creation_time_100ns_utc == self.creation_time_100ns_utc);
    CHECK(sample.timestamp_monotonic_ns > 0);
    CHECK(sample.working_set_bytes.state == MACE_METRIC_VALID);
    CHECK(sample.working_set_bytes.value > 0);
    CHECK(sample.private_bytes.state == sample.working_set_bytes.state);

    MaceResponsivenessV2 response;
    memset(&response, 0xA5, sizeof(response));
    CHECK(CP_CheckProcessResponsivenessV2(&self, &response,
           sizeof(response) - 1) == MACE_E_BUFFER_TOO_SMALL);
    for (size_t i = 0; i < sizeof(response); ++i)
        CHECK(((const unsigned char*)&response)[i] == 0xA5);
    CHECK(CP_CheckProcessResponsivenessV2(&altered, &response,
           sizeof(response)) == MACE_E_PID_REUSED);
    CHECK(CP_CheckProcessResponsivenessV2(&self, &response,
           sizeof(response)) == MACE_OK);
    CHECK(response.struct_size == sizeof(response) && response.version == 2);
    CHECK(response.identity.pid == self.pid);
    CHECK(response.windows_hung <= response.windows_examined);
    CHECK(response.outcome == MACE_RESPONSIVENESS_NO_WINDOW ||
          response.outcome == MACE_RESPONSIVENESS_RESPONDING ||
          response.outcome == MACE_RESPONSIVENESS_HUNG ||
          (response.outcome == MACE_RESPONSIVENESS_ERROR && response.win32_error != 0));
    CHECK(CP_CheckProcessResponsiveness(self.pid) == 0 ||
          CP_CheckProcessResponsiveness(self.pid) == 1);

    wchar_t executable[MAX_PATH] = {0};
    CHECK(GetModuleFileNameW(NULL, executable, MAX_PATH) > 0);
    wchar_t command[MAX_PATH + 32] = {0};
    swprintf_s(command, MAX_PATH + 32, L"\"%ls\" --helper", executable);
    STARTUPINFOW startup = {0};
    startup.cb = sizeof(startup);
    PROCESS_INFORMATION child = {0};
    CHECK(CreateProcessW(executable, command, NULL, NULL, FALSE,
          CREATE_NO_WINDOW, NULL, NULL, &startup, &child));
    MaceProcessIdentityV2 child_identity = {0};
    CHECK(CP_GetProcessIdentityV2(child.dwProcessId, &child_identity,
          sizeof(child_identity), &error) == MACE_OK);
    CHECK(WaitForSingleObject(child.hProcess, 5000) == WAIT_OBJECT_0);
    CHECK(CP_SampleProcessV2(&child_identity, &sample, sizeof(sample),
          &error) == MACE_E_PROCESS_EXITED);
    CHECK(CP_CheckProcessResponsivenessV2(&child_identity, &response,
          sizeof(response)) == MACE_OK);
    CHECK(response.outcome == MACE_RESPONSIVENESS_PROCESS_EXITED);
    CloseHandle(child.hThread);
    CloseHandle(child.hProcess);
    puts("Process samples, identity and window status: OK");
    return 0;
}
