#ifndef MACE_LHM_RETRY_FIXTURE_H
#define MACE_LHM_RETRY_FIXTURE_H
#include <windows.h>
/* Shared only by test executable and its simulated child, no product setting. */
typedef struct { volatile LONG launches, phase, pid; } LhmRetryFixture;
#endif
