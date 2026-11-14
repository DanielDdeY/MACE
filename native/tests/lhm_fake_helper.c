#include <windows.h>
#include <aclapi.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "../src/telemetry/lhm_wire.h"
static wchar_t scenario[64];
static void put32(unsigned char* b,uint32_t v) { memcpy(b,&v,4); }
static void put64(unsigned char* b,uint64_t v) { memcpy(b,&v,8); }
static void metric(unsigned char* d,unsigned i,double value,uint32_t meaning) {
    memcpy(d+32+i*16,&value,8); put32(d+40+i*16,1); put32(d+44+i*16,meaning);
}
static bool mode(const wchar_t* name) { return wcscmp(scenario,name)==0; }
static bool restricted_acl(HANDLE pipe) {
    PSECURITY_DESCRIPTOR sd=NULL; PACL acl=NULL;
    if (GetSecurityInfo(pipe,SE_KERNEL_OBJECT,DACL_SECURITY_INFORMATION,NULL,NULL,&acl,NULL,&sd)!=ERROR_SUCCESS) return false;
    SECURITY_DESCRIPTOR_CONTROL control=0; DWORD revision=0;
    bool ok=GetSecurityDescriptorControl(sd,&control,&revision) && (control&SE_DACL_PROTECTED) && acl && acl->AceCount==2;
    HANDLE token=NULL; DWORD size=0; TOKEN_USER* user=NULL;
    BYTE system_sid[SECURITY_MAX_SID_SIZE]; DWORD system_size=sizeof(system_sid);
    bool saw_user=false,saw_system=false;
    if (!OpenProcessToken(GetCurrentProcess(),TOKEN_QUERY,&token)) ok=false;
    if (token) {
        GetTokenInformation(token,TokenUser,NULL,0,&size); user=HeapAlloc(GetProcessHeap(),0,size);
        if (!user || !GetTokenInformation(token,TokenUser,user,size,&size)) ok=false;
    }
    if (!CreateWellKnownSid(WinLocalSystemSid,NULL,system_sid,&system_size)) ok=false;
    if (ok) for (DWORD i=0;i<2;i++) {
        ACCESS_ALLOWED_ACE* ace=NULL;
        if (!GetAce(acl,i,(void**)&ace) || ace->Header.AceType!=ACCESS_ALLOWED_ACE_TYPE) { ok=false; break; }
        PSID sid=&ace->SidStart;
        if (EqualSid(sid,user->User.Sid)) saw_user=true;
        else if (EqualSid(sid,system_sid)) saw_system=true;
        else ok=false;
    }
    if (user) HeapFree(GetProcessHeap(),0,user);
    if (token) CloseHandle(token);
    LocalFree(sd); return ok && saw_user && saw_system;
}
static void frame(unsigned char* b,uint64_t seq,bool warming) {
    memset(b,0,LHM_MAX_FRAME);
    put32(b,0x314D484C); put32(b+4,0x00300001); put32(b+8,warming ? 48 : 624);
    put32(b+12,mode(L"no-elevation") || mode(L"zero-unprivileged") ? 0 : 1);
    put64(b+16,seq); put64(b+24,GetTickCount64()-(mode(L"stale-arrival") ? 3001 : 0));
    put32(b+32,GetCurrentProcessId()+(mode(L"bad-pid") ? 1 : 0));
    put32(b+36,mode(L"driver-missing") || mode(L"zero-unprivileged") ? 1 : 3);
    put32(b+40,warming ? 1 : 2); put32(b+44,warming ? 0 : 4);
    if (warming) return;
    for (unsigned i=0;i<4;i++) {
        unsigned char* d=b+48+i*144;
        put32(d,i==0 ? 1 : i==1 ? 3 : i==2 ? 2 : 4); put32(d+4,i==0 ? 1 : 2);
        if (i) { put32(d+16,i==1 ? 0x1002 : i==2 ? 0x10DE : 0x8086);
            put32(d+20,i*0x1111); put32(d+24,0x12345678); put32(d+28,1); }
        for (unsigned m=0;m<7;m++) put32(d+40+m*16,4);
        metric(d,0,i ? 44 : mode(L"zero-cpu") || mode(L"zero-unprivileged") ? 0 : 55,i ? 3 : 1);
        metric(d,1,i ? mode(L"gpu-zero") ? 0 : 0.05 : mode(L"zero-cpu") || mode(L"zero-unprivileged") ? 0 : 15,i ? mode(L"unknown-power") ? 6 : mode(L"amd-chip") && i==1 ? 5 : 4 : 1);
        if (i) {
            metric(d,2,0,0); metric(d,3,0,0); metric(d,4,0,0);
            metric(d,5,1048576,0); metric(d,6,4294967296.0,0);
        }
        if (i==1 && mode(L"unknown-gpu")) {
            put32(d+4,0);
            for (unsigned m=0;m<7;m++) { memset(d+32+m*16,0,16); put32(d+40+m*16,4); }
        }
    }
    if (mode(L"malformed")) put32(b+8,0xFFFFFFFF);
    if (mode(L"nan")) { double value=0.0; uint64_t nanbits=UINT64_C(0x7ff8000000000000); memcpy(&value,&nanbits,8); memcpy(b+80,&value,8); }
}
static bool pipe_security(const wchar_t* path) {
    HANDLE duplicate=CreateNamedPipeW(path,PIPE_ACCESS_INBOUND|FILE_FLAG_FIRST_PIPE_INSTANCE,PIPE_REJECT_REMOTE_CLIENTS,1,4096,4096,0,NULL);
    if (duplicate!=INVALID_HANDLE_VALUE) { CloseHandle(duplicate); return false; }
    return GetLastError()==ERROR_ACCESS_DENIED;
}
int wmain(int argc,wchar_t** argv) {
    GetEnvironmentVariableW(L"MACE_SIM_SCENARIO",scenario,64);
    if (mode(L"wrong-client") && !(argc==2 && wcscmp(argv[1],L"--fake-child")==0)) {
        wchar_t exe[32768],cmd[32768];
        GetModuleFileNameW(NULL,exe,32768); swprintf_s(cmd,32768,L"\"%s\" --fake-child",exe);
        STARTUPINFOW si={0}; si.cb=sizeof(si); si.dwFlags=STARTF_USESTDHANDLES;
        si.hStdInput=GetStdHandle(STD_INPUT_HANDLE); si.hStdOutput=GetStdHandle(STD_OUTPUT_HANDLE); si.hStdError=GetStdHandle(STD_ERROR_HANDLE);
        PROCESS_INFORMATION pi={0};
        if (!CreateProcessW(exe,cmd,NULL,NULL,TRUE,CREATE_NO_WINDOW,NULL,NULL,&si,&pi)) return 10;
        CloseHandle(pi.hThread); CloseHandle(pi.hProcess); Sleep(15000); return 0;
    }
    unsigned char bootstrap[128]; DWORD read=0,offset=0;
    while (offset<128) {
        if (!ReadFile(GetStdHandle(STD_INPUT_HANDLE),bootstrap+offset,128-offset,&read,NULL) || !read) return 2;
        offset+=read;
    }
    if (LhmWire_U32(bootstrap)!=0x3142434D || LhmWire_U32(bootstrap+4)!=1 || LhmWire_U32(bootstrap+12)!=73) return 3;
    wchar_t path[96]=L"\\\\.\\pipe\\";
    for (unsigned i=0;i<73;i++) path[9+i]=(wchar_t)bootstrap[16+i];
    path[82]=0;
    if (wcsstr(GetCommandLineW(),path+9)) return 8;
    if (mode(L"slow")) Sleep(1200);
    if (!pipe_security(path)) return 4;
    HANDLE pipe=CreateFileW(path,GENERIC_WRITE|READ_CONTROL,0,NULL,OPEN_EXISTING,SECURITY_SQOS_PRESENT|SECURITY_IDENTIFICATION,NULL);
    if (pipe==INVALID_HANDLE_VALUE) return 5;
    if (!restricted_acl(pipe)) { CloseHandle(pipe); return 9; }
    ULONG pid=0;
    if (!GetNamedPipeServerProcessId(pipe,&pid) || pid!=LhmWire_U32(bootstrap+8)+(mode(L"bad-server-pid") ? 1 : 0)) { CloseHandle(pipe); return 6; }
    unsigned char bytes[LHM_MAX_FRAME]; DWORD written=0;
    frame(bytes,1,true);
    if (!WriteFile(pipe,bytes,48,&written,NULL)) { CloseHandle(pipe); return 7; }
    if (mode(L"slow")) Sleep(1200);
    for (uint64_t sequence=2;;sequence++) {
        frame(bytes,mode(L"replay") ? 2 : sequence,false);
        DWORD size=624;
        if (mode(L"fragmented")) {
            for (DWORD i=0;i<size;i++) if (!WriteFile(pipe,bytes+i,1,&written,NULL)) { CloseHandle(pipe); return 0; }
        } else if (!WriteFile(pipe,bytes,size,&written,NULL)) { CloseHandle(pipe); return 0; }
        if (mode(L"stale")) { Sleep(8000); CloseHandle(pipe); return 0; }
        if (mode(L"disconnect")) { Sleep(600); CloseHandle(pipe); return 0; }
        Sleep(1000);
    }
}
