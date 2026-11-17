#ifndef MACE_LHM_TRANSPORT_H
#define MACE_LHM_TRANSPORT_H
#include <windows.h>
#include <stdbool.h>
#include <stdint.h>
#include "lhm_trust.h"
#include "mace_status.h"
typedef struct { HANDLE pipe, process, job; DWORD pid; bool authenticated; uint64_t control_sequence; LhmTrustLease* trust; } LhmSession;
typedef enum { LHM_OPEN_TRANSIENT, LHM_OPEN_TRUST, LHM_OPEN_CONTAINMENT, LHM_OPEN_LAUNCH_BLOCKED } LhmOpenFailure;
static inline MaceMetricState LhmTransport_FailureState(LhmOpenFailure failure) {
    switch (failure) {
        case LHM_OPEN_TRUST: return MACE_METRIC_HELPER_UNTRUSTED;
        case LHM_OPEN_CONTAINMENT: return MACE_METRIC_HELPER_CONTAINMENT_FAILED;
        default: return MACE_METRIC_HELPER_UNAVAILABLE;
    }
}
bool LhmTransport_Open(const wchar_t* executable, HANDLE stop, LhmSession* session);
bool LhmTransport_OpenEx(const wchar_t* executable,HANDLE stop,LhmSession* session,LhmOpenFailure* failure);
bool LhmTransport_Read(LhmSession* session, HANDLE stop, unsigned char* data,
                       DWORD size, ULONGLONG deadline);
void LhmTransport_Close(LhmSession* session);
bool LhmTransport_SendShutdown(LhmSession* session);
/* Emergency containment only; never terminates an unrelated process. */
void LhmTransport_AbortActive(void);
#endif
