#include "lhm_transport.h"
#include "lhm_wire.h"
#include <bcrypt.h>
#include <sddl.h>
#include <stdio.h>
#include <string.h>

static SRWLOCK active_lock=SRWLOCK_INIT;
static HANDLE active_job;

void LhmTransport_AbortActive(void) {
    AcquireSRWLockShared(&active_lock);
    if (active_job) TerminateJobObject(active_job,1);
    ReleaseSRWLockShared(&active_lock);
}

static bool wait_io(HANDLE pipe, HANDLE stop, OVERLAPPED* operation, DWORD timeout, DWORD* transferred) {
    HANDLE waits[2]={stop,operation->hEvent};
    if (WaitForMultipleObjects(2,waits,FALSE,timeout)!=WAIT_OBJECT_0+1) {
        CancelIoEx(pipe,operation);
        GetOverlappedResult(pipe,operation,transferred,TRUE);
        return false;
    }
    return GetOverlappedResult(pipe,operation,transferred,FALSE)!=0;
}

bool LhmTransport_Read(LhmSession* s, HANDLE stop, unsigned char* data, DWORD size, ULONGLONG deadline) {
    DWORD offset=0;
    while (offset<size) {
        ULONGLONG now=GetTickCount64();
        if (now>=deadline) return false;
        OVERLAPPED op={0}; op.hEvent=CreateEventW(NULL,TRUE,FALSE,NULL);
        if (!op.hEvent) return false;
        DWORD read=0;
        BOOL done=ReadFile(s->pipe,data+offset,size-offset,&read,&op);
        bool ok=done!=0;
        if (!done && GetLastError()==ERROR_IO_PENDING)
            ok=wait_io(s->pipe,stop,&op,(DWORD)(deadline-now),&read);
        CloseHandle(op.hEvent);
        if (!ok || !read) return false;
        offset+=read;
    }
    return true;
}

static PSECURITY_DESCRIPTOR private_descriptor(void) {
    HANDLE token=NULL; DWORD size=0;
    if (!OpenProcessToken(GetCurrentProcess(),TOKEN_QUERY,&token)) return NULL;
    GetTokenInformation(token,TokenUser,NULL,0,&size);
    TOKEN_USER* user=HeapAlloc(GetProcessHeap(),0,size);
    LPWSTR sid=NULL; PSECURITY_DESCRIPTOR descriptor=NULL;
    if (user && GetTokenInformation(token,TokenUser,user,size,&size) && ConvertSidToStringSidW(user->User.Sid,&sid)) {
        wchar_t sddl[256];
        if (swprintf_s(sddl,256,L"D:P(A;;GRGW;;;SY)(A;;GRGW;;;%s)",sid)>0)
            ConvertStringSecurityDescriptorToSecurityDescriptorW(sddl,SDDL_REVISION_1,&descriptor,NULL);
    }
    if (sid) LocalFree(sid);
    if (user) HeapFree(GetProcessHeap(),0,user);
    CloseHandle(token);
    return descriptor;
}

bool LhmTransport_SendShutdown(LhmSession* s) {
    if (!s->authenticated || !s->pipe || s->pipe==INVALID_HANDLE_VALUE || s->control_sequence) return false;
    unsigned char message[LHM_CONTROL_SIZE]={0};
    uint32_t magic=0x3143434Du, header=(LHM_CONTROL_SIZE<<16)|LHM_PROTOCOL_VERSION;
    uint32_t size=LHM_CONTROL_SIZE, type=1, pid=GetCurrentProcessId(); uint64_t sequence=1;
    memcpy(message,&magic,4); memcpy(message+4,&header,4); memcpy(message+8,&size,4);
    memcpy(message+12,&type,4); memcpy(message+16,&sequence,8); memcpy(message+24,&pid,4);
    OVERLAPPED op={0}; op.hEvent=CreateEventW(NULL,TRUE,FALSE,NULL);
    if (!op.hEvent) return false;
    DWORD written=0; BOOL done=WriteFile(s->pipe,message,sizeof(message),&written,&op);
    bool ok=done!=0;
    if (!done && GetLastError()==ERROR_IO_PENDING) {
        if (WaitForSingleObject(op.hEvent,100)!=WAIT_OBJECT_0) {
            CancelIoEx(s->pipe,&op);
            GetOverlappedResult(s->pipe,&op,&written,TRUE); // Keep stack storage alive until cancellation completes.
        } else ok=GetOverlappedResult(s->pipe,&op,&written,FALSE)!=0;
    }
    CloseHandle(op.hEvent);
    if (ok && written==sizeof(message)) { s->control_sequence=1; return true; }
    return false;
}

void LhmTransport_Close(LhmSession* s) {
    ULONGLONG deadline=GetTickCount64()+2000;
    bool sent=s->control_sequence==1 || LhmTransport_SendShutdown(s);
    if (!sent && s->pipe && s->pipe!=INVALID_HANDLE_VALUE) {
        CloseHandle(s->pipe); s->pipe=NULL; // Failure to send never disables EOF fallback.
    }
    if (s->process) {
        ULONGLONG now=GetTickCount64();
        if (WaitForSingleObject(s->process,now<deadline ? (DWORD)(deadline-now) : 0)!=WAIT_OBJECT_0) {
            if (s->pipe && s->pipe!=INVALID_HANDLE_VALUE) { CloseHandle(s->pipe); s->pipe=NULL; }
            if (s->job) TerminateJobObject(s->job,1);
            else TerminateProcess(s->process,1);
        }
        WaitForSingleObject(s->process,1000);
        CloseHandle(s->process);
    }
    if (s->pipe && s->pipe!=INVALID_HANDLE_VALUE) CloseHandle(s->pipe);
    if (s->job) {
        AcquireSRWLockExclusive(&active_lock);
        if (active_job==s->job) active_job=NULL;
        CloseHandle(s->job);
        ReleaseSRWLockExclusive(&active_lock);
    }
    memset(s,0,sizeof(*s));
}

bool LhmTransport_Open(const wchar_t* executable, HANDLE stop, LhmSession* s) {
    OutputDebugStringA(LHM_PROTOCOL_MARKER);
    memset(s,0,sizeof(*s));
    unsigned char random[32], bootstrap[128]={0};
    wchar_t name[74]=L"mace-lhm-", path[96], command[32768];
    const wchar_t hex[]=L"0123456789abcdef";
    if (BCryptGenRandom(NULL,random,sizeof(random),BCRYPT_USE_SYSTEM_PREFERRED_RNG)!=0) return false;
    for (unsigned i=0;i<32;i++) { name[9+i*2]=hex[random[i]>>4]; name[10+i*2]=hex[random[i]&15]; }
    if (swprintf_s(path,96,L"\\\\.\\pipe\\%s",name)<0) return false;
    PSECURITY_DESCRIPTOR descriptor=private_descriptor();
    if (!descriptor) return false;
    SECURITY_ATTRIBUTES sa={sizeof(sa),descriptor,FALSE};
    s->pipe=CreateNamedPipeW(path,PIPE_ACCESS_DUPLEX|FILE_FLAG_OVERLAPPED|FILE_FLAG_FIRST_PIPE_INSTANCE,
        PIPE_TYPE_BYTE|PIPE_READMODE_BYTE|PIPE_REJECT_REMOTE_CLIENTS,1,LHM_MAX_FRAME,LHM_MAX_FRAME,0,&sa);
    if (s->pipe==INVALID_HANDLE_VALUE) { LocalFree(descriptor); s->pipe=NULL; return false; }
    HANDLE input=NULL, writer=NULL, null_output=INVALID_HANDLE_VALUE;
    sa.bInheritHandle=TRUE;
    bool ok=CreatePipe(&input,&writer,&sa,4096)!=0;
    LocalFree(descriptor);
    sa.lpSecurityDescriptor=NULL;
    if (!ok) goto cleanup;
    ok=SetHandleInformation(writer,HANDLE_FLAG_INHERIT,0)!=0;
    if (!ok) goto cleanup;
    null_output=CreateFileW(L"NUL",GENERIC_WRITE,FILE_SHARE_READ|FILE_SHARE_WRITE,&sa,OPEN_EXISTING,0,NULL);
    /* NUL uses the default descriptor, never the freed pipe descriptor. */
    if (null_output==INVALID_HANDLE_VALUE) { ok=false; goto cleanup; }
    STARTUPINFOEXW start={0}; start.StartupInfo.cb=sizeof(start);
    start.StartupInfo.dwFlags=STARTF_USESTDHANDLES;
    start.StartupInfo.hStdInput=input; start.StartupInfo.hStdOutput=null_output; start.StartupInfo.hStdError=null_output;
    SIZE_T attribute_size=0;
    InitializeProcThreadAttributeList(NULL,2,0,&attribute_size);
    start.lpAttributeList=HeapAlloc(GetProcessHeap(),0,attribute_size);
    if (!start.lpAttributeList) { ok=false; goto cleanup; }
    bool attributes_ready=InitializeProcThreadAttributeList(start.lpAttributeList,2,0,&attribute_size)!=0;
    HANDLE inherited[2]={input,null_output};
    PROCESS_INFORMATION child={0};
    if (attributes_ready && UpdateProcThreadAttribute(start.lpAttributeList,0,PROC_THREAD_ATTRIBUTE_HANDLE_LIST,
        inherited,sizeof(inherited),NULL,NULL) && swprintf_s(command,32768,L"\"%s\" --bootstrap-stdin",executable)>0) {
        s->job=CreateJobObjectW(NULL,NULL);
        JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits={0};
        limits.BasicLimitInformation.LimitFlags=JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        if (s->job && SetInformationJobObject(s->job,JobObjectExtendedLimitInformation,&limits,sizeof(limits)) &&
            UpdateProcThreadAttribute(start.lpAttributeList,0,PROC_THREAD_ATTRIBUTE_JOB_LIST,
                &s->job,sizeof(s->job),NULL,NULL)) {
            AcquireSRWLockExclusive(&active_lock);
            active_job=s->job;
            ReleaseSRWLockExclusive(&active_lock);
            /* Creation-time assignment closes the create/assign crash window.
             * No breakaway flags; restrictive enclosing jobs fail closed. */
            ok=WaitForSingleObject(stop,0)==WAIT_TIMEOUT &&
                CreateProcessW(executable,command,NULL,NULL,TRUE,
                    CREATE_SUSPENDED|CREATE_NO_WINDOW|EXTENDED_STARTUPINFO_PRESENT,
                    NULL,NULL,&start.StartupInfo,&child)!=0;
        } else ok=false;
        if (ok) {
            s->process=child.hProcess; s->pid=child.dwProcessId;
            BOOL assigned=FALSE;
            if (IsProcessInJob(s->process,s->job,&assigned) && assigned &&
                WaitForSingleObject(stop,0)==WAIT_TIMEOUT && ResumeThread(child.hThread)!=(DWORD)-1) {
                uint32_t magic=0x3142434Du, version=LHM_PROTOCOL_VERSION, pid=GetCurrentProcessId(), length=73;
                memcpy(bootstrap,&magic,4); memcpy(bootstrap+4,&version,4);
                memcpy(bootstrap+8,&pid,4); memcpy(bootstrap+12,&length,4);
                for (unsigned i=0;i<73;i++) bootstrap[16+i]=(unsigned char)name[i];
                DWORD written=0;
                ok=WriteFile(writer,bootstrap,sizeof(bootstrap),&written,NULL) && written==sizeof(bootstrap);
            } else ok=false;
            CloseHandle(child.hThread);
        } else OutputDebugStringW(L"MACE LHM: contained helper creation failed; telemetry degraded.\n");
    } else ok=false;
    if (attributes_ready) DeleteProcThreadAttributeList(start.lpAttributeList);
    HeapFree(GetProcessHeap(),0,start.lpAttributeList);
    if (!ok || !s->process) goto cleanup;
    OVERLAPPED connect={0}; connect.hEvent=CreateEventW(NULL,TRUE,FALSE,NULL);
    if (!connect.hEvent) { ok=false; goto cleanup; }
    ok=ConnectNamedPipe(s->pipe,&connect)!=0;
    if (!ok) {
        DWORD error=GetLastError(), transferred=0;
        ok=error==ERROR_PIPE_CONNECTED || (error==ERROR_IO_PENDING && wait_io(s->pipe,stop,&connect,5000,&transferred));
    }
    CloseHandle(connect.hEvent);
    ULONG peer=0;
    if (ok) ok=GetNamedPipeClientProcessId(s->pipe,&peer) && peer==s->pid && WaitForSingleObject(s->process,0)==WAIT_TIMEOUT;
    s->authenticated=ok;
cleanup:
    ok=ok && s->process!=NULL;
    if (input) CloseHandle(input);
    if (writer) CloseHandle(writer);
    if (null_output!=INVALID_HANDLE_VALUE) CloseHandle(null_output);
    SecureZeroMemory(bootstrap,sizeof(bootstrap));
    if (!ok) LhmTransport_Close(s);
    return ok;
}

bool LhmTransport_VerifyHash(const wchar_t* executable, const wchar_t* expected) {
    if (!expected[0]) { OutputDebugStringW(L"MACE LHM: signature pending; no pinned helper hash configured.\n"); return true; }
    if (wcslen(expected)!=64) return false;
    HANDLE file=CreateFileW(executable,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,FILE_FLAG_SEQUENTIAL_SCAN,NULL);
    if (file==INVALID_HANDLE_VALUE) return false;
    BCRYPT_ALG_HANDLE algorithm=NULL; BCRYPT_HASH_HANDLE hash=NULL;
    unsigned char digest[32], buffer[4096]; DWORD read=0; bool ok=false;
    if (BCryptOpenAlgorithmProvider(&algorithm,BCRYPT_SHA256_ALGORITHM,NULL,0)!=0) goto finish;
    if (BCryptCreateHash(algorithm,&hash,NULL,0,NULL,0,0)!=0) goto finish;
    for (;;) {
        if (!ReadFile(file,buffer,sizeof(buffer),&read,NULL)) goto finish;
        if (!read) break;
        if (BCryptHashData(hash,buffer,read,0)!=0) goto finish;
    }
    if (BCryptFinishHash(hash,digest,sizeof(digest),0)!=0) goto finish;
    wchar_t actual[65];
    for (unsigned i=0;i<32;i++) swprintf_s(actual+i*2,65-i*2,L"%02x",digest[i]);
    ok=_wcsicmp(actual,expected)==0;
finish:
    if (hash) BCryptDestroyHash(hash);
    if (algorithm) BCryptCloseAlgorithmProvider(algorithm,0);
    CloseHandle(file);
    return ok;
}
