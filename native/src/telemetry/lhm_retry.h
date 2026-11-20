#ifndef MACE_LHM_RETRY_H
#define MACE_LHM_RETRY_H
#include <stdint.h>
#include <stdbool.h>
#define LHM_RETRY_WINDOW_MS UINT64_C(60000)
#define LHM_RETRY_STABLE_SAMPLES 3u
/* Private worker policy. Never part of the exported telemetry ABI or wire protocol. */
typedef struct {
    uint64_t restarts[3], next_ms;
    uint32_t count, backoff_ms, slow_ms, consecutive, attempts;
    bool initial, slow, blocked;
} LhmRetry;
void LhmRetry_Init(LhmRetry* retry);
bool LhmRetry_Start(LhmRetry* retry,uint64_t awake_ms);
void LhmRetry_Failed(LhmRetry* retry,uint64_t awake_ms,bool permanent);
void LhmRetry_Sample(LhmRetry* retry,bool usable);
#endif
