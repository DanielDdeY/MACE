"""Generate test-only native audit injection outside candidate sources/product.
No process launched or terminated. CMake injection explicitly excludes production DLL.
"""
from pathlib import Path
import sys
REPO=Path(__file__).resolve().parents[2]
ROOT=REPO/'native/build/candidata-20261126-02'
AUDIT=ROOT/'test-audit';AUDIT.mkdir(exist_ok=True)
header=r'''
#ifndef MACE_CANDIDATE_PROCESS_AUDIT_H
#define MACE_CANDIDATE_PROCESS_AUDIT_H
#include <windows.h>
#include <tlhelp32.h>
#include <stdio.h>
static __inline BOOL MaceAuditPath(HANDLE process,wchar_t *path,ULONGLONG *born,DWORD *pid) {
    wchar_t temp[4096],marker[4096]; DWORD n=0;
    HANDLE active=CreateFileW(L"@@ACTIVE@@",GENERIC_READ,FILE_SHARE_READ|FILE_SHARE_WRITE,NULL,OPEN_EXISTING,0,NULL);
    if(active!=INVALID_HANDLE_VALUE){DWORD bytes=0;if(ReadFile(active,temp,sizeof(temp)-sizeof(wchar_t),&bytes,NULL) && bytes%2==0){n=bytes/2;temp[n]=0;}CloseHandle(active);}
    if(!n)n=GetEnvironmentVariableW(L"TEMP",temp,4096);
    if (!n || n>=4096) return FALSE;
    if (swprintf_s(marker,4096,L"%s\\.mace-candidate-process-audit",temp)<0 || GetFileAttributesW(marker)==INVALID_FILE_ATTRIBUTES) return FALSE;
    FILETIME created,ended,kernel,user; ULARGE_INTEGER value;
    if (!GetProcessTimes(process,&created,&ended,&kernel,&user)) return FALSE;
    value.LowPart=created.dwLowDateTime;value.HighPart=created.dwHighDateTime;
    *born=value.QuadPart/10000ULL-11644473600000ULL;*pid=GetProcessId(process);
    return *pid && swprintf_s(path,4096,L"%s\\p_%lu_%llu.audit",temp,*pid,*born)>0;
}
static __inline DWORD MaceAuditParent(void) {
    HANDLE snap=CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS,0);if(snap==INVALID_HANDLE_VALUE)return 0;
    PROCESSENTRY32W item={0};item.dwSize=sizeof(item);DWORD parent=0;
    if(Process32FirstW(snap,&item))do{if(item.th32ProcessID==GetCurrentProcessId()){parent=item.th32ParentProcessID;break;}}while(Process32NextW(snap,&item));
    CloseHandle(snap);return parent;
}
static __inline BOOL MaceAuditRecord(const char *event,HANDLE process,DWORD parent) {
    wchar_t path[4096];ULONGLONG born=0;DWORD pid=0;
    if(!MaceAuditPath(process,path,&born,&pid))return FALSE;
    HANDLE file=CreateFileW(path,FILE_APPEND_DATA,FILE_SHARE_READ|FILE_SHARE_WRITE,NULL,OPEN_ALWAYS,FILE_ATTRIBUTE_NORMAL,NULL);
    if(file==INVALID_HANDLE_VALUE)return FALSE;
    char line[160];int length=sprintf_s(line,sizeof(line),"%s %lu %llu %lu %lu\n",event,pid,born,parent,GetCurrentProcessId());DWORD written=0;
    BOOL ok=length>0 && WriteFile(file,line,(DWORD)length,&written,NULL) && written==(DWORD)length;
    CloseHandle(file);return ok;
}
static __inline BOOL MaceAuditedCreate(LPCWSTR app,LPWSTR cmd,LPSECURITY_ATTRIBUTES pa,LPSECURITY_ATTRIBUTES ta,BOOL inherit,DWORD flags,LPVOID env,LPCWSTR cwd,LPSTARTUPINFOW startup,LPPROCESS_INFORMATION pi) {
    BOOL ok=CreateProcessW(app,cmd,pa,ta,inherit,flags,env,cwd,startup,pi);DWORD saved=GetLastError();
    if(ok)MaceAuditRecord("CREATE",pi->hProcess,GetCurrentProcessId());
    SetLastError(saved);return ok;
}
static __inline BOOL MaceAuditedTerminate(HANDLE process,UINT exitCode) {
    wchar_t path[4096];ULONGLONG born=0;DWORD pid=0;
    if(!MaceAuditPath(process,path,&born,&pid) || GetFileAttributesW(path)==INVALID_FILE_ATTRIBUTES){SetLastError(ERROR_ACCESS_DENIED);return FALSE;}
    if(!MaceAuditRecord("KILL_REQUEST",process,0)){SetLastError(ERROR_ACCESS_DENIED);return FALSE;}
    return TerminateProcess(process,exitCode);
}
#ifdef MACE_AUDIT_WRAP_WMAIN
int MaceAuditUserMain(int,wchar_t**);
int wmain(int argc,wchar_t **argv) {
    if(!MaceAuditRecord("SELF",GetCurrentProcess(),MaceAuditParent()))return 90;
    return MaceAuditUserMain(argc,argv);
}
#define wmain MaceAuditUserMain
#endif
#define CreateProcessW MaceAuditedCreate
#define TerminateProcess MaceAuditedTerminate
#endif
'''
header=header.replace('@@ACTIVE@@',str(AUDIT/'active-directory.txt').replace('\\','\\\\'))
(AUDIT/'candidate_process_audit.h').write_text(header,encoding='utf8')
cmake='''
function(mace_candidate_audit)
  get_property(all_targets DIRECTORY PROPERTY BUILDSYSTEM_TARGETS)
  foreach(target IN LISTS all_targets)
    get_target_property(target_type ${target} TYPE)
    if(target MATCHES "^(test_|lhm_|mace_native_lhm_|mace_native_secure_test)" AND NOT target STREQUAL "test_lhm_live" AND NOT target_type STREQUAL "UTILITY")
      target_compile_options(${target} PRIVATE "$<$<COMPILE_LANGUAGE:C>:/FIHEADER>")
    endif()
    if(target_type STREQUAL "EXECUTABLE")
      set_property(TARGET ${target} PROPERTY TEST_LAUNCHER "@@PYTHON@@" "@@LAUNCHER@@")
    endif()
  endforeach()
  get_target_property(security_sources test_lhm_security SOURCES)
  list(REMOVE_ITEM security_sources tests/test_lhm_security.c)
  list(APPEND security_sources "SECURITY_SOURCE")
  set_property(TARGET test_lhm_security PROPERTY SOURCES "${security_sources}")
  set_property(SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/tests/lhm_fake_helper.c" APPEND PROPERTY COMPILE_DEFINITIONS MACE_AUDIT_WRAP_WMAIN=1)
endfunction()
cmake_language(DEFER CALL mace_candidate_audit)
'''.replace('HEADER',(AUDIT/'candidate_process_audit.h').as_posix()).replace('SECURITY_SOURCE',(AUDIT/'test_lhm_security.c').as_posix()).replace('@@PYTHON@@',Path(sys.executable).as_posix()).replace('@@LAUNCHER@@',(REPO/'helper/auditoria-licencias/lanzador-test-candidata.py').as_posix())
(AUDIT/'test_lhm_security.c').write_text((ROOT/'source/native/tests/test_lhm_security.c').read_text(encoding='utf8').replace('item.th32ParentProcessID==GetCurrentProcessId() && !_wcsicmp(item.szExeFile,L"Mace.Lhm.Helper.exe")','item.th32ParentProcessID==GetCurrentProcessId()').replace('PROCESS_TERMINATE|SYNCHRONIZE','PROCESS_TERMINATE|PROCESS_QUERY_LIMITED_INFORMATION|SYNCHRONIZE'),encoding='utf8')
(AUDIT/'audit.cmake').write_text(cmake,encoding='utf8')
print('AUDIT_GENERATED production_dll_injected=0 source_files_modified=0')
