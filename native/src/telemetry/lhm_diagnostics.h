#ifndef MACE_LHM_DIAGNOSTICS_H
#define MACE_LHM_DIAGNOSTICS_H
/* Test executable instrumentation only. No exported ABI, paths, hashes or identity. */
#if defined(MACE_LHM_DIAGNOSTICS) && MACE_LHM_DIAGNOSTICS
#include <windows.h>
enum { LHM_DIAG_NONE, LHM_DIAG_WIN32, LHM_DIAG_VALIDATION, LHM_DIAG_NTSTATUS, LHM_DIAG_HRESULT, LHM_DIAG_WAIT_RESULT };
typedef struct { const char* stage; DWORD code; unsigned domain; } LhmOpenDiagnostic;
extern __declspec(thread) LhmOpenDiagnostic lhm_open_diagnostic;
#define LHM_DIAG_RESET() (lhm_open_diagnostic=(LhmOpenDiagnostic){"open",0,LHM_DIAG_NONE})
#define LHM_DIAG_STAGE(s) do { if (!lhm_open_diagnostic.domain) { lhm_open_diagnostic.stage=(s); SetLastError(ERROR_SUCCESS); } } while (0)
#define LHM_DIAG_CODE(s,d,c) do { if (!lhm_open_diagnostic.domain) lhm_open_diagnostic=(LhmOpenDiagnostic){(s),(DWORD)(c),(d)}; } while (0)
#define LHM_DIAG_OS() do { DWORD lhm_error=GetLastError(); if (!lhm_open_diagnostic.domain) { \
    lhm_open_diagnostic.code=lhm_error ? lhm_error : ERROR_INVALID_DATA; \
    lhm_open_diagnostic.domain=lhm_error ? LHM_DIAG_WIN32 : LHM_DIAG_VALIDATION; } } while (0)
#define LHM_DIAG_VALID(s,c) LHM_DIAG_CODE(s,LHM_DIAG_VALIDATION,c)
#define LHM_DIAG_NT(s,c) LHM_DIAG_CODE(s,LHM_DIAG_NTSTATUS,c)
#define LHM_DIAG_HR(s,c) LHM_DIAG_CODE(s,LHM_DIAG_HRESULT,c)
#define LHM_DIAG_WAIT(c) LHM_DIAG_CODE(lhm_open_diagnostic.stage,LHM_DIAG_WAIT_RESULT,c)
#else
#define LHM_DIAG_RESET() ((void)0)
#define LHM_DIAG_STAGE(s) ((void)0)
#define LHM_DIAG_OS() ((void)0)
#define LHM_DIAG_VALID(s,c) ((void)0)
#define LHM_DIAG_NT(s,c) ((void)0)
#define LHM_DIAG_HR(s,c) ((void)0)
#define LHM_DIAG_WAIT(c) ((void)0)
#endif
#endif
