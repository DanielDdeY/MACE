#ifndef MACE_LHM_RETRY_EVIDENCE_H
#define MACE_LHM_RETRY_EVIDENCE_H
#include "../src/telemetry/lhm_retry.h"
typedef struct { LhmRetry policy; uint32_t cause; } LhmRetryEvidence;
/* Exported ONLY by disposable fixture DLLs, never mace_native.dll. */
#ifdef MACE_LHM_TEST_HOOKS
__declspec(dllexport) void LhmReceiver_TestEvidence(LhmRetryEvidence* evidence);
#else
__declspec(dllimport) void LhmReceiver_TestEvidence(LhmRetryEvidence* evidence);
#endif
#endif
