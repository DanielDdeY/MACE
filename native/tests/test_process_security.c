#include "process_internal.h"
#include <stdio.h>

#define CHECK(x) do { if (!(x)) { \
    fprintf(stderr, "check failed at line %d: %s\n", __LINE__, #x); return 1; \
} } while (0)

int main(void) {
    CHECK(ProcessSecurity_IsBlacklistedName(L"LSASS.EXE"));
    CHECK(ProcessSecurity_IsBlacklistedName(L"csrss.exe"));
    CHECK(!ProcessSecurity_IsBlacklistedName(L"my-lsass.exe"));
    CHECK(!ProcessSecurity_IsBlacklistedName(L"editor.exe"));
    CHECK(ProcessSecurity_IsSystemPathWithRoots(
        L"c:\\WINDOWS\\System32\\services.exe", L"C:\\Windows", L"C:\\Windows\\System32"));
    CHECK(ProcessSecurity_IsSystemPathWithRoots(
        L"C:\\Windows\\SysWOW64\\tool.exe", L"C:\\Windows", L"C:\\Windows\\System32"));
    CHECK(ProcessSecurity_IsSystemPathWithRoots(
        L"\\\\?\\c:/WINDOWS/System32/tool.exe", L"C:\\Windows", L"C:\\Windows\\System32"));
    CHECK(!ProcessSecurity_IsSystemPathWithRoots(
        L"C:\\WindowsBackup\\tool.exe", L"C:\\Windows", L"C:\\Windows\\System32"));
    CHECK(!ProcessSecurity_IsSystemPathWithRoots(
        L"C:\\Other\\System32Backup\\tool.exe", L"C:\\Windows", L"C:\\Windows\\System32"));
    CHECK(!ProcessSecurity_IsSystemPathWithRoots(
        L"D:\\Apps\\editor.exe", L"C:\\Windows", L"C:\\Windows\\System32"));
    CHECK(ProcessSecurity_Check(GetCurrentProcess(), GetCurrentProcessId(),
          L"D:\\Apps\\editor.exe", NULL) == MACE_E_PROTECTED_PROCESS);
    CHECK(ProcessSecurity_Check(GetCurrentProcess(), 4,
          L"D:\\Apps\\editor.exe", NULL) == MACE_E_PROTECTED_PROCESS);
    puts("process policy, case-insensitive directory boundaries and self-protection: OK");
    return 0;
}
