#include "lhm_trust.h"
#include "lhm_security_config.h"
#include "lhm_diagnostics.h"
#include <bcrypt.h>
#include <shlobj.h>
#include <wintrust.h>
#include <softpub.h>
#include <stdio.h>
#include <string.h>
#include <wctype.h>

#define TRUST_MAX_FILES 512u
#define TRUST_PATH 32768u
struct LhmTrustLease {
    HANDLE handles[TRUST_MAX_FILES+2]; DWORD count,files_count;
    wchar_t root[TRUST_PATH],exe[TRUST_PATH],environment[TRUST_PATH];
    wchar_t names[TRUST_MAX_FILES][128];
};

static bool hash_handle(HANDLE file,HANDLE stop,char hex[65]) {
    LHM_DIAG_STAGE("trust-hash-file-read");
    LARGE_INTEGER zero={0},size;
    if (!GetFileSizeEx(file,&size) || size.QuadPart>512LL*1024*1024 ||
        !SetFilePointerEx(file,zero,NULL,FILE_BEGIN)) { LHM_DIAG_OS(); return false; }
    BCRYPT_ALG_HANDLE algorithm=NULL; BCRYPT_HASH_HANDLE hash=NULL;
    unsigned char digest[32],buffer[8192]; DWORD read=0; bool ok=false;
    NTSTATUS status=BCryptOpenAlgorithmProvider(&algorithm,BCRYPT_SHA256_ALGORITHM,NULL,0);
    if (status!=0) { LHM_DIAG_NT("trust-hash-algorithm",status); goto done; }
    status=BCryptCreateHash(algorithm,&hash,NULL,0,NULL,0,0);
    if (status!=0) { LHM_DIAG_NT("trust-hash-create",status); goto done; }
    for (;;) {
        if (stop && WaitForSingleObject(stop,0)!=WAIT_TIMEOUT) { LHM_DIAG_VALID("trust-cancelled",ERROR_OPERATION_ABORTED); goto done; }
        LHM_DIAG_STAGE("trust-hash-read");
        if (!ReadFile(file,buffer,sizeof(buffer),&read,NULL)) { LHM_DIAG_OS(); goto done; }
        if (!read) break;
        status=BCryptHashData(hash,buffer,read,0);
        if (status!=0) { LHM_DIAG_NT("trust-hash-data",status); goto done; }
    }
    status=BCryptFinishHash(hash,digest,sizeof(digest),0);
    if (status!=0) { LHM_DIAG_NT("trust-hash-finish",status); goto done; }
    for (unsigned i=0;i<32;i++) sprintf_s(hex+i*2,65-i*2,"%02x",digest[i]);
    ok=true;
done:
    if (hash) BCryptDestroyHash(hash);
    if (algorithm) BCryptCloseAlgorithmProvider(algorithm,0);
    return ok;
}

void LhmTrust_Release(LhmTrustLease* lease) {
    if (!lease) return;
    for (DWORD i=0;i<lease->count;i++) CloseHandle(lease->handles[i]);
    SecureZeroMemory(lease,sizeof(*lease)); HeapFree(GetProcessHeap(),0,lease);
}
const wchar_t* LhmTrust_Executable(const LhmTrustLease* lease) { return lease->exe; }
const wchar_t* LhmTrust_Directory(const LhmTrustLease* lease) { return lease->root; }
const wchar_t* LhmTrust_Environment(const LhmTrustLease* lease) { return lease->environment; }

static bool final_name(HANDLE file,wchar_t* name) {
    LHM_DIAG_STAGE("trust-canonical-name");
    DWORD n=GetFinalPathNameByHandleW(file,name,TRUST_PATH,FILE_NAME_NORMALIZED|VOLUME_NAME_DOS);
    if (!n) { LHM_DIAG_OS(); }
    return n && n<TRUST_PATH && _wcsnicmp(name,L"\\\\?\\UNC\\",8)!=0;
}
static HANDLE lock_file(LhmTrustLease* lease,const wchar_t* name,bool directory) {
    LHM_DIAG_STAGE(directory ? "trust-lock-directory" : "trust-lock-file");
    HANDLE file=CreateFileW(name,directory ? FILE_READ_ATTRIBUTES : GENERIC_READ,FILE_SHARE_READ,
        NULL,OPEN_EXISTING,FILE_FLAG_OPEN_REPARSE_POINT|(directory ? FILE_FLAG_BACKUP_SEMANTICS : FILE_FLAG_SEQUENTIAL_SCAN),NULL);
    if (file==INVALID_HANDLE_VALUE) { LHM_DIAG_OS(); return NULL; }
    LHM_DIAG_STAGE("trust-file-attributes");
    FILE_ATTRIBUTE_TAG_INFO attributes={0};
    if (lease->count>=TRUST_MAX_FILES+2 || !GetFileInformationByHandleEx(file,FileAttributeTagInfo,&attributes,sizeof(attributes)) ||
        (attributes.FileAttributes&FILE_ATTRIBUTE_REPARSE_POINT) ||
        !!(attributes.FileAttributes&FILE_ATTRIBUTE_DIRECTORY)!=directory) { LHM_DIAG_OS(); CloseHandle(file); return NULL; }
    lease->handles[lease->count++]=file; return file;
}
static bool env_add(LhmTrustLease* lease,size_t* offset,const wchar_t* name,const wchar_t* value) {
    int n=swprintf_s(lease->environment+*offset,TRUST_PATH-*offset,L"%s=%s",name,value);
    if (n<0 || *offset+(size_t)n+2>TRUST_PATH) { LHM_DIAG_VALID("child-environment-size",ERROR_INVALID_DATA); return false; }
    *offset+=(size_t)n+1; lease->environment[*offset]=0; return true;
}
static bool environment(LhmTrustLease* lease) {
    wchar_t windows[MAX_PATH],system[MAX_PATH],local[MAX_PATH],temp[MAX_PATH],path[MAX_PATH*2+2];
    LHM_DIAG_STAGE("child-windows-directory");
    DWORD wn=GetWindowsDirectoryW(windows,MAX_PATH);
    if (!wn) { LHM_DIAG_OS(); }
    LHM_DIAG_STAGE("child-system-directory");
    DWORD sn=GetSystemDirectoryW(system,MAX_PATH);
    if (!sn) { LHM_DIAG_OS(); }
    if (!wn || wn>=MAX_PATH || !sn || sn>=MAX_PATH) { LHM_DIAG_OS(); return false; }
    HRESULT local_result=SHGetFolderPathW(NULL,CSIDL_LOCAL_APPDATA,NULL,SHGFP_TYPE_CURRENT,local);
    if (local_result!=S_OK) { LHM_DIAG_HR("child-local-appdata",local_result); return false; }
    if (swprintf_s(temp,MAX_PATH,L"%s\\Temp",local)<0 ||
        swprintf_s(path,sizeof(path)/sizeof(*path),L"%s;%s",system,windows)<0) {
        LHM_DIAG_VALID("child-environment-format",ERROR_INVALID_DATA); return false;
    }
    size_t offset=0;
#if MACE_LHM_DEVELOPMENT
    wchar_t scenario[64]; DWORD n=GetEnvironmentVariableW(L"MACE_SIM_SCENARIO",scenario,64);
    if (n>=64) { LHM_DIAG_VALID("child-scenario-length",ERROR_INVALID_DATA); return false; }
    if (n && !env_add(lease,&offset,L"MACE_SIM_SCENARIO",scenario)) return false;
#endif
    return env_add(lease,&offset,L"PATH",path) && env_add(lease,&offset,L"SystemRoot",windows) &&
        env_add(lease,&offset,L"TEMP",temp) && env_add(lease,&offset,L"TMP",temp) && env_add(lease,&offset,L"WINDIR",windows);
}

#if !MACE_LHM_DEVELOPMENT
static bool production_root(LhmTrustLease* lease) {
    HMODULE module=NULL; wchar_t dll[TRUST_PATH],canonical[TRUST_PATH],expected[TRUST_PATH];
    if (!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
        (LPCWSTR)(uintptr_t)&LhmTrust_Acquire,&module)) return false;
    DWORD n=GetModuleFileNameW(module,dll,TRUST_PATH); if (!n || n>=TRUST_PATH) return false;
    HANDLE file=CreateFileW(dll,FILE_READ_ATTRIBUTES,FILE_SHARE_READ|FILE_SHARE_WRITE|FILE_SHARE_DELETE,NULL,OPEN_EXISTING,0,NULL);
    if (file==INVALID_HANDLE_VALUE) return false;
    bool ok=final_name(file,canonical); CloseHandle(file); if (!ok) return false;
    wchar_t* slash=wcsrchr(canonical,L'\\'); if (!slash) return false; *slash=0;
    return swprintf_s(expected,TRUST_PATH,L"%s\\lhm",canonical)>0 && _wcsicmp(expected,lease->root)==0;
}
static bool safe_name(const char* name) {
    size_t n=strlen(name);
    if (n<2 || n>127 || name[0]=='.' || name[n-1]=='.' || strstr(name,"..")) return false;
    for (size_t i=0;i<n;i++) if (!((name[i]>='A'&&name[i]<='Z') || (name[i]>='a'&&name[i]<='z') ||
        (name[i]>='0'&&name[i]<='9') || name[i]=='.' || name[i]=='_' || name[i]=='-')) return false;
    char stem[128]; strcpy_s(stem,sizeof(stem),name); char* dot=strchr(stem,'.'); if (dot) *dot=0;
    if (!_stricmp(stem,"CON") || !_stricmp(stem,"PRN") || !_stricmp(stem,"AUX") || !_stricmp(stem,"NUL") ||
        (strlen(stem)==4 && (!_strnicmp(stem,"COM",3)||!_strnicmp(stem,"LPT",3)) && stem[3]>='1' && stem[3]<='9')) return false;
    return true;
}
static bool signature(const wchar_t* executable) {
#if MACE_LHM_REQUIRE_AUTHENTICODE
    WINTRUST_FILE_INFO file={0}; file.cbStruct=sizeof(file); file.pcwszFilePath=executable;
    WINTRUST_DATA data={0}; data.cbStruct=sizeof(data); data.dwUIChoice=WTD_UI_NONE;
    data.fdwRevocationChecks=WTD_REVOKE_WHOLECHAIN; data.dwUnionChoice=WTD_CHOICE_FILE; data.pFile=&file;
    data.dwStateAction=WTD_STATEACTION_VERIFY; data.dwProvFlags=WTD_CACHE_ONLY_URL_RETRIEVAL|WTD_DISABLE_MD2_MD4;
    GUID action=WINTRUST_ACTION_GENERIC_VERIFY_V2; LONG status=WinVerifyTrust(NULL,&action,&data);
    data.dwStateAction=WTD_STATEACTION_CLOSE; WinVerifyTrust(NULL,&action,&data);
    return status==ERROR_SUCCESS;
#else
    (void)executable; OutputDebugStringA("LHM_SIGNATURE_SKIPPED: certificate pending.\n"); return true;
#endif
}
static bool manifest(LhmTrustLease* lease,HANDLE stop) {
    wchar_t path[TRUST_PATH],actual[TRUST_PATH]; char digest[65];
    if (swprintf_s(path,TRUST_PATH,L"%s\\helper-runtime.manifest",lease->root)<0) return false;
    HANDLE file=lock_file(lease,path,false); if (!file) return false;
    if (!final_name(file,actual) || _wcsicmp(path,actual) || !hash_handle(file,stop,digest) ||
        _stricmp(digest,MACE_LHM_MANIFEST_SHA256)) return false;
    LARGE_INTEGER size,zero={0};
    if (!GetFileSizeEx(file,&size) || size.QuadPart<20 || size.QuadPart>131072 || !SetFilePointerEx(file,zero,NULL,FILE_BEGIN)) return false;
    DWORD length=(DWORD)size.QuadPart,read=0;
    char* text=HeapAlloc(GetProcessHeap(),HEAP_ZERO_MEMORY,(size_t)length+1); if (!text) return false;
    bool ok=ReadFile(file,text,length,&read,NULL) && read==length && !memchr(text,0,length);
    const char prefix[]="MACE-LHM-RUNTIME 2\n";
    if (!ok || length<sizeof(prefix)-1 || memcmp(text,prefix,sizeof(prefix)-1)) { HeapFree(GetProcessHeap(),0,text); return false; }
    char* cursor=text+sizeof(prefix)-1; bool have_executable=false;
    while (*cursor && ok) {
        char* end=strchr(cursor,'\n'); if (!end || end-cursor<68 || end-cursor>193 || lease->files_count>=TRUST_MAX_FILES) { ok=false; break; }
        *end=0;
        if (cursor[64]!=' ' || cursor[65]!=' ' || !safe_name(cursor+66)) { ok=false; break; }
        for (unsigned i=0;i<64;i++) if (!((cursor[i]>='0'&&cursor[i]<='9')||(cursor[i]>='a'&&cursor[i]<='f'))) ok=false;
        if (!ok) break;
        char expected[65]; memcpy(expected,cursor,64); expected[64]=0;
        wchar_t* name=lease->names[lease->files_count];
        for (size_t i=0;i<=strlen(cursor+66);i++) name[i]=(wchar_t)(unsigned char)cursor[66+i];
        for (DWORD i=0;i<lease->files_count;i++) if (!_wcsicmp(name,lease->names[i])) ok=false;
        if (!ok || swprintf_s(path,TRUST_PATH,L"%s\\%s",lease->root,name)<0) { ok=false; break; }
        HANDLE dependency=lock_file(lease,path,false);
        if (!dependency || !final_name(dependency,actual) || _wcsicmp(path,actual) ||
            !hash_handle(dependency,stop,digest) || strcmp(expected,digest)) { ok=false; break; }
        if (!_wcsicmp(name,L"Mace.Lhm.Helper.exe")) have_executable=true;
        lease->files_count++; cursor=end+1;
    }
    HeapFree(GetProcessHeap(),0,text);
    if (!ok || !have_executable) return false;
    if (swprintf_s(path,TRUST_PATH,L"%s\\*",lease->root)<0) return false;
    WIN32_FIND_DATAW entry; HANDLE find=FindFirstFileW(path,&entry); if (find==INVALID_HANDLE_VALUE) return false;
    DWORD count=0;
    do {
        if (!_wcsicmp(entry.cFileName,L".") || !_wcsicmp(entry.cFileName,L"..")) continue;
        bool listed=!_wcsicmp(entry.cFileName,L"helper-runtime.manifest");
        for (DWORD i=0;i<lease->files_count;i++) if (!_wcsicmp(entry.cFileName,lease->names[i])) listed=true;
        if (!listed || (entry.dwFileAttributes&(FILE_ATTRIBUTE_DIRECTORY|FILE_ATTRIBUTE_REPARSE_POINT))) { ok=false; break; }
        count++;
    } while (FindNextFileW(find,&entry));
    DWORD error=GetLastError(); FindClose(find);
    return ok && error==ERROR_NO_MORE_FILES && count==lease->files_count+1 && signature(lease->exe);
}
#endif

bool LhmTrust_Acquire(const wchar_t* executable,HANDLE stop,LhmTrustLease** output) {
    LHM_DIAG_STAGE("trust-acquire");
    *output=NULL;
    LhmTrustLease* lease=HeapAlloc(GetProcessHeap(),HEAP_ZERO_MEMORY,sizeof(*lease));
    if (!lease) { LHM_DIAG_VALID("trust-allocation",ERROR_NOT_ENOUGH_MEMORY); return false; }
    if (wcscpy_s(lease->exe,TRUST_PATH,executable)) goto failed;
    wchar_t* slash=wcsrchr(lease->exe,L'\\');
    wchar_t* forward=wcsrchr(lease->exe,L'/'); if (forward && (!slash || forward>slash)) slash=forward;
    if (!slash) goto failed;
#if MACE_LHM_DEVELOPMENT
    wchar_t basename[128]; if (wcscpy_s(basename,128,slash+1)) goto failed;
#endif
    *slash=0;
    HANDLE folder=lock_file(lease,lease->exe,true);
    if (!folder || !final_name(folder,lease->root)) goto failed;
#if MACE_LHM_DEVELOPMENT
    OutputDebugStringA("MACE_LHM_PROFILE=DEVELOPMENT\n");
    if (swprintf_s(lease->exe,TRUST_PATH,L"%s\\%s",lease->root,basename)<0) goto failed;
    HANDLE file=lock_file(lease,lease->exe,false); if (!file) goto failed;
    wchar_t expected[65]; DWORD n=GetEnvironmentVariableW(L"MACE_LHM_HELPER_SHA256",expected,65);
    if (n>=65) { LHM_DIAG_VALID("dev-hash-length",ERROR_INVALID_DATA); goto failed; }
    if (n) {
        char actual[65];
        if (n!=64) { LHM_DIAG_VALID("dev-hash-length",ERROR_INVALID_DATA); goto failed; }
        if (!hash_handle(file,stop,actual)) goto failed;
        for (unsigned i=0;i<64;i++) if ((wchar_t)actual[i]!=towlower(expected[i])) {
            LHM_DIAG_VALID("dev-hash-mismatch",ERROR_CRC); goto failed;
        }
    }
#else
    OutputDebugStringA("MACE_LHM_PROFILE=PRODUCTION;MACE_LHM_MANIFEST_SHA256=" MACE_LHM_MANIFEST_SHA256 "\n");
    if (!production_root(lease) || swprintf_s(lease->exe,TRUST_PATH,L"%s\\Mace.Lhm.Helper.exe",lease->root)<0 || !manifest(lease,stop)) goto failed;
#endif
    LHM_DIAG_STAGE("trust-child-environment");
    if (!environment(lease)) goto failed;
    *output=lease; return true;
failed:
    LHM_DIAG_OS(); // Preserve the first error BEFORE release/CloseHandle can replace it.
    OutputDebugStringA("LHM_TRUST_REJECTED\n"); LhmTrust_Release(lease); return false;
}
