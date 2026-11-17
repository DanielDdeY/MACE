#include "lhm_retry.h"
#include <string.h>
static void prune(LhmRetry* r,uint64_t now) {
    uint32_t keep=0;
    for (uint32_t i=0;i<r->count;i++)
        if (now-r->restarts[i]<LHM_RETRY_WINDOW_MS) r->restarts[keep++]=r->restarts[i];
    r->count=keep;
}
void LhmRetry_Init(LhmRetry* r) {
    memset(r,0,sizeof(*r)); r->initial=true; r->backoff_ms=1000; r->slow_ms=30000;
}
bool LhmRetry_Start(LhmRetry* r,uint64_t now) {
    if (r->blocked || now<r->next_ms) return false;
    prune(r,now);
    if (!r->initial && r->count==3) { r->next_ms=r->restarts[0]+LHM_RETRY_WINDOW_MS; return false; }
    if (r->initial) r->initial=false;
    else r->restarts[r->count++]=now;
    r->attempts++; r->consecutive=0; return true;
}
void LhmRetry_Failed(LhmRetry* r,uint64_t now,bool permanent) {
    r->consecutive=0;
    if (permanent) { r->blocked=true; return; }
    prune(r,now);
    if (r->count==3) r->slow=true;
    uint32_t delay=r->slow ? r->slow_ms : r->backoff_ms;
    if (r->slow) { if (r->slow_ms<300000) r->slow_ms=r->slow_ms>150000 ? 300000 : r->slow_ms*2; }
    else if (r->backoff_ms<8000) r->backoff_ms*=2;
    r->next_ms=now+delay;
    if (r->count==3 && r->next_ms<r->restarts[0]+LHM_RETRY_WINDOW_MS)
        r->next_ms=r->restarts[0]+LHM_RETRY_WINDOW_MS;
}
void LhmRetry_Sample(LhmRetry* r,bool usable) {
    if (!usable) { r->consecutive=0; return; }
    if (r->consecutive<LHM_RETRY_STABLE_SAMPLES) r->consecutive++;
    if (r->consecutive==LHM_RETRY_STABLE_SAMPLES) {
        r->backoff_ms=1000; r->slow_ms=30000; r->slow=false;
        // Recovery never erases timestamps: even flapping after three good frames is rate-limited.
    }
}
