#ifndef MACE_LHM_TRUST_H
#define MACE_LHM_TRUST_H
#include <windows.h>
#include <stdbool.h>
typedef struct LhmTrustLease LhmTrustLease;
bool LhmTrust_Acquire(const wchar_t* executable,HANDLE stop,LhmTrustLease** lease);
void LhmTrust_Release(LhmTrustLease* lease);
const wchar_t* LhmTrust_Executable(const LhmTrustLease* lease);
const wchar_t* LhmTrust_Directory(const LhmTrustLease* lease);
const wchar_t* LhmTrust_Environment(const LhmTrustLease* lease);
#endif
