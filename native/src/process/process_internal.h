#ifndef MACE_PROCESS_INTERNAL_H
#define MACE_PROCESS_INTERNAL_H

#include <windows.h>
#include <stdbool.h>
#include <stdint.h>
#include "mace_types.h"
#include "mace_types_v2.h"

int EnumerateProcesses(ProcessData* out_buffer, uint32_t max_count, uint32_t* out_count);
int TerminateProcessByPid(uint32_t pid);

MaceStatus ProcessPath_Read(HANDLE process, wchar_t** out_path, uint32_t* win32_error);
void ProcessPath_Free(wchar_t* path);
MaceStatus ProcessIdentity_Read(HANDLE process, uint32_t pid,
                                MaceProcessIdentityV2* out, uint32_t* win32_error);
MaceStatus ProcessSecurity_Check(HANDLE process, uint32_t pid,
                                 const wchar_t* path, uint32_t* win32_error);
bool ProcessSecurity_IsBlacklistedName(const wchar_t* name);
bool ProcessSecurity_IsSystemPathWithRoots(const wchar_t* path,
                                           const wchar_t* windows_dir,
                                           const wchar_t* system_dir);
MaceStatus ProcessError_FromWin32(DWORD error);
MaceStatus ProcessController_Terminate(uint32_t pid,
                                       const MaceProcessIdentityV2* expected,
                                       uint32_t* win32_error);

#endif
