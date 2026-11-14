#ifndef MACE_LHM_TRANSPORT_H
#define MACE_LHM_TRANSPORT_H
#include <windows.h>
#include <stdbool.h>
typedef struct { HANDLE pipe, process, job; DWORD pid; } LhmSession;
bool LhmTransport_Open(const wchar_t* executable, HANDLE stop, LhmSession* session);
bool LhmTransport_Read(LhmSession* session, HANDLE stop, unsigned char* data,
                       DWORD size, ULONGLONG deadline);
void LhmTransport_Close(LhmSession* session);
bool LhmTransport_VerifyHash(const wchar_t* executable, const wchar_t* expected);
#endif
