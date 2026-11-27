/*
 * mace-pawnio-setup.exe: coordinador del requisito PawnIO para MACE.
 *
 * Contrato (Fase 3, decisiones D2/D3; ver installer/README.md):
 *  - Se ejecuta DESPUES de instalar MACE; su fallo nunca revierte MACE.
 *  - Si PawnIO ya esta registrado (cualquier version) no lo reinstala,
 *    no lo detiene ni lo repara.
 *  - Solo ejecuta el setup oficial intacto: ruta absoluta, archivo ordinario,
 *    SHA-256 fijado y firma Authenticode valida del firmante fijado. Mantiene
 *    el archivo abierto sin compartir escritura/borrado mientras lo ejecuta.
 *  - Argumentos fijos "-install -silent". Espera maxima 180 s; al vencer NO
 *    termina el proceso hijo (resultado TIMEOUT, sin segundo intento).
 *  - 0 o 3010 del hijo solo cuentan si despues el servicio queda registrado.
 *  - Registro de una linea con campos fijos: sin rutas, usuarios ni hashes.
 */
#include <windows.h>
#include <bcrypt.h>
#include <wincrypt.h>
#include <wintrust.h>
#include <softpub.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>

#include "pawnio_pins.h"

/* Codigos de salida del coordinador (contrato con el instalador y con MACE). */
#define RC_INSTALLED            0
#define RC_USAGE                2
#define RC_INSTALLED_REBOOT     3010
#define RC_ALREADY_COMPATIBLE   10
#define RC_OTHER_VERSION_KEPT   11
#define RC_STATE_UNKNOWN        12
#define RC_NOT_ELEVATED         13
#define RC_ABSENT               14
#define RC_SETUP_UNUSABLE       20
#define RC_SIGNATURE_INVALID    21
#define RC_SIGNATURE_UNVERIFIED 22
#define RC_SETUP_FAILED         30
#define RC_NOT_CORROBORATED     31
#define RC_TIMEOUT              40

#define DEFAULT_TIMEOUT_MS (180u * 1000u)
#define SERVICE_NAME L"PawnIO"
#define PATH_CAP 4096

typedef enum { MODE_INSTALL, MODE_VERIFY, MODE_DETECT } run_mode;
typedef enum { DET_ABSENT, DET_COMPATIBLE, DET_OTHER, DET_UNKNOWN } detection;

typedef struct {
    run_mode mode;
    const wchar_t *setup;
    const wchar_t *log;
    const wchar_t *service;
    char expected_sha[65];
    DWORD timeout_ms;
    BOOL skip_signature;
    BOOL skip_elevation;
    BOOL assume_installed;
} options;

typedef struct {
    const char *mode;
    const char *detect;
    char version[48];
    const char *hash;
    const char *signature;
    char child[24];
    unsigned long child_pid;
    unsigned long elapsed_ms;
    const char *result;
    int code;
} report;

static const char *detection_name(detection d)
{
    switch (d) {
    case DET_ABSENT: return "ABSENT";
    case DET_COMPATIBLE: return "COMPATIBLE";
    case DET_OTHER: return "OTHER";
    default: return "UNKNOWN";
    }
}

static void emit(const options *opt, const report *r)
{
    char line[512];
    int n = snprintf(line, sizeof line,
        "mace-pawnio-setup v%s mode=%s detect=%s version=%s hash=%s signature=%s "
        "child=%s child_pid=%lu elapsed_ms=%lu result=%s code=%d\r\n",
        MACE_COORDINATOR_VERSION, r->mode, r->detect, r->version, r->hash, r->signature,
        r->child, r->child_pid, r->elapsed_ms, r->result, r->code);
    if (n <= 0) {
        return;
    }
    if ((size_t)n >= sizeof line) {
        n = (int)sizeof line - 1;
    }
    fputs(line, stdout);
    fflush(stdout);
    if (opt->log != NULL) {
        /* Un solo archivo sobrescrito por ejecucion: retencion acotada. */
        HANDLE f = CreateFileW(opt->log, GENERIC_WRITE, FILE_SHARE_READ, NULL,
                               CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
        if (f != INVALID_HANDLE_VALUE) {
            DWORD written = 0;
            WriteFile(f, line, (DWORD)n, &written, NULL);
            CloseHandle(f);
        }
    }
}

static int finish(const options *opt, report *r, const char *result, int code)
{
    r->result = result;
    r->code = code;
    emit(opt, r);
    return code;
}

static BOOL is_elevated(void)
{
    HANDLE token = NULL;
    TOKEN_ELEVATION elevation;
    DWORD size = 0;
    BOOL elevated = FALSE;
    if (OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token)) {
        if (GetTokenInformation(token, TokenElevation, &elevation, (DWORD)sizeof elevation, &size)) {
            elevated = elevation.TokenIsElevated != 0;
        }
        CloseHandle(token);
    }
    return elevated;
}

static const wchar_t *find_ci(const wchar_t *text, const wchar_t *needle)
{
    size_t n = wcslen(needle);
    for (; *text != L'\0'; ++text) {
        if (_wcsnicmp(text, needle, n) == 0) {
            return text;
        }
    }
    return NULL;
}

/* Convierte la ruta configurada del servicio en una ruta Win32 de archivo. */
static BOOL resolve_image_path(const wchar_t *configured, wchar_t *out, size_t cap)
{
    wchar_t expanded[PATH_CAP];
    wchar_t windir[MAX_PATH];
    const wchar_t *p;
    const wchar_t *end;
    size_t len;
    UINT wl;

    if (ExpandEnvironmentStringsW(configured, expanded, PATH_CAP) == 0) {
        return FALSE;
    }
    p = expanded;
    while (*p == L' ') {
        ++p;
    }
    if (*p == L'"') {
        ++p;
        end = wcschr(p, L'"');
    } else {
        const wchar_t *exe = find_ci(p, L".exe");
        const wchar_t *sys = find_ci(p, L".sys");
        const wchar_t *hit = exe;
        if (hit == NULL || (sys != NULL && sys < hit)) {
            hit = sys;
        }
        end = hit != NULL ? hit + 4 : p + wcslen(p);
    }
    if (end == NULL) {
        return FALSE;
    }
    len = (size_t)(end - p);

    wl = GetWindowsDirectoryW(windir, MAX_PATH);
    if (wl == 0 || wl >= MAX_PATH) {
        return FALSE;
    }
    if (len == 0) {
        return FALSE;
    }
    if (len > 12 && _wcsnicmp(p, L"\\SystemRoot\\", 12) == 0) {
        return swprintf(out, cap, L"%ls\\%.*ls", windir, (int)(len - 12), p + 12) > 0;
    }
    if (len > 4 && _wcsnicmp(p, L"\\??\\", 4) == 0) {
        return swprintf(out, cap, L"%.*ls", (int)(len - 4), p + 4) > 0;
    }
    if (_wcsnicmp(p, L"System32\\", 9) == 0) {
        return swprintf(out, cap, L"%ls\\%.*ls", windir, (int)len, p) > 0;
    }
    return swprintf(out, cap, L"%.*ls", (int)len, p) > 0;
}

static BOOL file_version(const wchar_t *path, WORD v[4])
{
    DWORD ignored = 0;
    DWORD size = GetFileVersionInfoSizeW(path, &ignored);
    void *data;
    BOOL ok = FALSE;
    if (size == 0) {
        return FALSE;
    }
    data = HeapAlloc(GetProcessHeap(), 0, size);
    if (data == NULL) {
        return FALSE;
    }
    if (GetFileVersionInfoW(path, 0, size, data)) {
        VS_FIXEDFILEINFO *info = NULL;
        UINT info_len = 0;
        if (VerQueryValueW(data, L"\\", (LPVOID *)&info, &info_len) && info != NULL &&
            info_len >= sizeof *info) {
            v[0] = HIWORD(info->dwFileVersionMS);
            v[1] = LOWORD(info->dwFileVersionMS);
            v[2] = HIWORD(info->dwFileVersionLS);
            v[3] = LOWORD(info->dwFileVersionLS);
            ok = TRUE;
        }
    }
    HeapFree(GetProcessHeap(), 0, data);
    return ok;
}

/* Consulta solo SCM y el recurso de version del archivo configurado. */
static detection detect(const wchar_t *service, char *version, size_t version_cap)
{
    SC_HANDLE scm;
    SC_HANDLE svc;
    DWORD needed = 0;
    detection result = DET_UNKNOWN;

    snprintf(version, version_cap, "-");
    scm = OpenSCManagerW(NULL, NULL, SC_MANAGER_CONNECT);
    if (scm == NULL) {
        return DET_UNKNOWN;
    }
    svc = OpenServiceW(scm, service, SERVICE_QUERY_CONFIG);
    if (svc == NULL) {
        DWORD error = GetLastError();
        CloseServiceHandle(scm);
        return error == ERROR_SERVICE_DOES_NOT_EXIST ? DET_ABSENT : DET_UNKNOWN;
    }
    if (!QueryServiceConfigW(svc, NULL, 0, &needed) &&
        GetLastError() == ERROR_INSUFFICIENT_BUFFER && needed != 0) {
        QUERY_SERVICE_CONFIGW *config = HeapAlloc(GetProcessHeap(), 0, needed);
        if (config != NULL) {
            if (QueryServiceConfigW(svc, config, needed, &needed) && config->lpBinaryPathName != NULL) {
                wchar_t image[PATH_CAP];
                WORD v[4];
                if (resolve_image_path(config->lpBinaryPathName, image, PATH_CAP) && file_version(image, v)) {
                    snprintf(version, version_cap, "%u.%u.%u.%u", v[0], v[1], v[2], v[3]);
                    result = (v[0] == MACE_PAWNIO_V0 && v[1] == MACE_PAWNIO_V1 &&
                              v[2] == MACE_PAWNIO_V2 && v[3] == MACE_PAWNIO_V3)
                                 ? DET_COMPATIBLE : DET_OTHER;
                }
            }
            HeapFree(GetProcessHeap(), 0, config);
        }
    }
    CloseServiceHandle(svc);
    CloseServiceHandle(scm);
    return result;
}

static int detection_code(detection d)
{
    switch (d) {
    case DET_ABSENT: return RC_ABSENT;
    case DET_COMPATIBLE: return RC_ALREADY_COMPATIBLE;
    case DET_OTHER: return RC_OTHER_VERSION_KEPT;
    default: return RC_STATE_UNKNOWN;
    }
}

static const char *detection_result(detection d)
{
    switch (d) {
    case DET_ABSENT: return "ABSENT";
    case DET_COMPATIBLE: return "ALREADY_COMPATIBLE";
    case DET_OTHER: return "OTHER_VERSION_KEPT";
    default: return "STATE_UNKNOWN";
    }
}

static BOOL sha256_of(HANDLE file, char hex[65])
{
    static BYTE buffer[65536];
    BCRYPT_ALG_HANDLE alg = NULL;
    BCRYPT_HASH_HANDLE hash = NULL;
    UCHAR digest[32];
    LARGE_INTEGER zero;
    BOOL ok = FALSE;
    int i;

    zero.QuadPart = 0;
    if (!SetFilePointerEx(file, zero, NULL, FILE_BEGIN)) {
        return FALSE;
    }
    if (!BCRYPT_SUCCESS(BCryptOpenAlgorithmProvider(&alg, BCRYPT_SHA256_ALGORITHM, NULL, 0))) {
        return FALSE;
    }
    if (BCRYPT_SUCCESS(BCryptCreateHash(alg, &hash, NULL, 0, NULL, 0, 0))) {
        ok = TRUE;
        for (;;) {
            DWORD read = 0;
            if (!ReadFile(file, buffer, (DWORD)sizeof buffer, &read, NULL)) {
                ok = FALSE;
                break;
            }
            if (read == 0) {
                break;
            }
            if (!BCRYPT_SUCCESS(BCryptHashData(hash, buffer, read, 0))) {
                ok = FALSE;
                break;
            }
        }
        if (ok) {
            ok = BCRYPT_SUCCESS(BCryptFinishHash(hash, digest, (ULONG)sizeof digest, 0));
        }
        BCryptDestroyHash(hash);
    }
    BCryptCloseAlgorithmProvider(alg, 0);
    if (!ok) {
        return FALSE;
    }
    for (i = 0; i < 32; ++i) {
        snprintf(hex + i * 2, 3, "%02x", digest[i]);
    }
    hex[64] = '\0';
    return TRUE;
}

typedef enum { SIG_OK, SIG_INVALID, SIG_SIGNER, SIG_UNVERIFIABLE } signature_state;

static signature_state verify_signature(HANDLE file, const wchar_t *path)
{
    GUID action = WINTRUST_ACTION_GENERIC_VERIFY_V2;
    WINTRUST_FILE_INFO file_info;
    WINTRUST_DATA data;
    LONG status;
    signature_state state;

    ZeroMemory(&file_info, sizeof file_info);
    file_info.cbStruct = (DWORD)sizeof file_info;
    file_info.pcwszFilePath = path;
    file_info.hFile = file;

    ZeroMemory(&data, sizeof data);
    data.cbStruct = (DWORD)sizeof data;
    data.dwUIChoice = WTD_UI_NONE;
    data.fdwRevocationChecks = WTD_REVOKE_WHOLECHAIN;
    data.dwUnionChoice = WTD_CHOICE_FILE;
    data.pFile = &file_info;
    data.dwStateAction = WTD_STATEACTION_VERIFY;
    data.dwProvFlags = WTD_REVOCATION_CHECK_CHAIN_EXCLUDE_ROOT;

    status = WinVerifyTrust(NULL, &action, &data);
    if (status == ERROR_SUCCESS) {
        CRYPT_PROVIDER_DATA *provider = WTHelperProvDataFromStateData(data.hWVTStateData);
        CRYPT_PROVIDER_SGNR *signer = provider != NULL
            ? WTHelperGetProvSignerFromChain(provider, 0, FALSE, 0) : NULL;
        CRYPT_PROVIDER_CERT *cert = signer != NULL ? WTHelperGetProvCertFromChain(signer, 0) : NULL;
        state = SIG_SIGNER;
        if (cert != NULL && cert->pCert != NULL) {
            wchar_t name[256];
            DWORD chars = CertGetNameStringW(cert->pCert, CERT_NAME_SIMPLE_DISPLAY_TYPE, 0, NULL,
                                             name, (DWORD)(sizeof name / sizeof name[0]));
            if (chars > 1 && wcscmp(name, MACE_PAWNIO_SIGNER) == 0) {
                state = SIG_OK;
            }
        }
    } else if (status == CERT_E_REVOCATION_FAILURE || status == CRYPT_E_REVOCATION_OFFLINE ||
               status == CRYPT_E_NO_REVOCATION_CHECK) {
        /* Sin poder comprobar revocacion no se ejecuta (D2); MACE puede reintentar luego. */
        state = SIG_UNVERIFIABLE;
    } else {
        state = SIG_INVALID;
    }
    data.dwStateAction = WTD_STATEACTION_CLOSE;
    WinVerifyTrust(NULL, &action, &data);
    return state;
}

typedef enum { RUN_DONE, RUN_START_FAILED, RUN_TIMEOUT } run_state;

static run_state run_setup(const wchar_t *path, DWORD timeout_ms, DWORD *child_code,
                           unsigned long *pid, unsigned long *elapsed)
{
    wchar_t command[PATH_CAP + 32];
    STARTUPINFOW startup;
    PROCESS_INFORMATION process;
    ULONGLONG started;
    DWORD wait;
    run_state state;

    if (swprintf(command, PATH_CAP + 32, L"\"%ls\" -install -silent", path) < 0) {
        *child_code = ERROR_BUFFER_OVERFLOW;
        return RUN_START_FAILED;
    }
    ZeroMemory(&startup, sizeof startup);
    startup.cb = (DWORD)sizeof startup;
    ZeroMemory(&process, sizeof process);

    started = GetTickCount64();
    if (!CreateProcessW(path, command, NULL, NULL, FALSE, 0, NULL, NULL, &startup, &process)) {
        *child_code = GetLastError();
        return RUN_START_FAILED;
    }
    *pid = process.dwProcessId;
    CloseHandle(process.hThread);
    wait = WaitForSingleObject(process.hProcess, timeout_ms);
    *elapsed = (unsigned long)(GetTickCount64() - started);
    if (wait == WAIT_OBJECT_0 && GetExitCodeProcess(process.hProcess, child_code)) {
        state = RUN_DONE;
    } else {
        /* D3: no se termina un instalador de driver a medias. */
        state = RUN_TIMEOUT;
    }
    CloseHandle(process.hProcess);
    return state;
}

static void usage(void)
{
    fputs("uso: mace-pawnio-setup --setup <PawnIO_setup.exe> [--log <archivo>]\n"
          "     mace-pawnio-setup --verify-only --setup <PawnIO_setup.exe>\n"
          "     mace-pawnio-setup --detect-only\n", stderr);
}

#ifdef MACE_COORDINATOR_TEST_HOOKS
static BOOL parse_hex(const wchar_t *text, char out[65])
{
    int i;
    if (wcslen(text) != 64) {
        return FALSE;
    }
    for (i = 0; i < 64; ++i) {
        wchar_t c = text[i];
        if (c >= L'A' && c <= L'F') {
            c = (wchar_t)(c - L'A' + L'a');
        }
        if (!((c >= L'0' && c <= L'9') || (c >= L'a' && c <= L'f'))) {
            return FALSE;
        }
        out[i] = (char)c;
    }
    out[64] = '\0';
    return TRUE;
}
#endif

static BOOL parse_args(int argc, wchar_t **argv, options *opt)
{
    int i;
    ZeroMemory(opt, sizeof *opt);
    opt->mode = MODE_INSTALL;
    opt->service = SERVICE_NAME;
    opt->timeout_ms = DEFAULT_TIMEOUT_MS;
    memcpy(opt->expected_sha, MACE_PAWNIO_SHA256, 65);

    for (i = 1; i < argc; ++i) {
        const wchar_t *a = argv[i];
        BOOL has_value = i + 1 < argc;
        if (wcscmp(a, L"--setup") == 0 && has_value) {
            opt->setup = argv[++i];
        } else if (wcscmp(a, L"--log") == 0 && has_value) {
            opt->log = argv[++i];
        } else if (wcscmp(a, L"--verify-only") == 0) {
            opt->mode = MODE_VERIFY;
        } else if (wcscmp(a, L"--detect-only") == 0) {
            opt->mode = MODE_DETECT;
#ifdef MACE_COORDINATOR_TEST_HOOKS
        } else if (wcscmp(a, L"--test-sha256") == 0 && has_value) {
            if (!parse_hex(argv[++i], opt->expected_sha)) {
                return FALSE;
            }
        } else if (wcscmp(a, L"--test-service") == 0 && has_value) {
            opt->service = argv[++i];
        } else if (wcscmp(a, L"--test-timeout-ms") == 0 && has_value) {
            opt->timeout_ms = (DWORD)wcstoul(argv[++i], NULL, 10);
        } else if (wcscmp(a, L"--test-skip-signature") == 0) {
            opt->skip_signature = TRUE;
        } else if (wcscmp(a, L"--test-skip-elevation") == 0) {
            opt->skip_elevation = TRUE;
        } else if (wcscmp(a, L"--test-assume-installed") == 0) {
            opt->assume_installed = TRUE;
#endif
        } else {
            return FALSE;
        }
    }
    if (opt->mode != MODE_DETECT && opt->setup == NULL) {
        return FALSE;
    }
    return TRUE;
}

int wmain(int argc, wchar_t **argv)
{
    options opt;
    report r;
    detection d;
    wchar_t full[PATH_CAP];
    DWORD full_len;
    HANDLE file;
    BY_HANDLE_FILE_INFORMATION info;
    char actual_sha[65];
    signature_state sig;
    DWORD child_code = 0;
    run_state run;

    ZeroMemory(&r, sizeof r);
    r.mode = "install";
    r.detect = "-";
    snprintf(r.version, sizeof r.version, "-");
    r.hash = "-";
    r.signature = "-";
    snprintf(r.child, sizeof r.child, "-");

    if (!parse_args(argc, argv, &opt)) {
        usage();
        return RC_USAGE;
    }
    r.mode = opt.mode == MODE_VERIFY ? "verify" : opt.mode == MODE_DETECT ? "detect" : "install";

    if (opt.mode != MODE_VERIFY) {
        d = detect(opt.service, r.version, sizeof r.version);
        r.detect = detection_name(d);
        if (opt.mode == MODE_DETECT || d != DET_ABSENT) {
            /* Presente (cualquier version) o indeterminado: no se toca. */
            return finish(&opt, &r, detection_result(d), detection_code(d));
        }
        if (!opt.skip_elevation && !is_elevated()) {
            return finish(&opt, &r, "NOT_ELEVATED", RC_NOT_ELEVATED);
        }
    }

    full_len = GetFullPathNameW(opt.setup, PATH_CAP, full, NULL);
    if (full_len == 0 || full_len >= PATH_CAP) {
        r.hash = "unreadable";
        return finish(&opt, &r, "SETUP_UNUSABLE", RC_SETUP_UNUSABLE);
    }
    /* Sin compartir escritura ni borrado: el archivo verificado es el ejecutado. */
    file = CreateFileW(full, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING,
                       FILE_ATTRIBUTE_NORMAL | FILE_FLAG_OPEN_REPARSE_POINT | FILE_FLAG_SEQUENTIAL_SCAN, NULL);
    if (file == INVALID_HANDLE_VALUE) {
        r.hash = "unreadable";
        return finish(&opt, &r, "SETUP_UNUSABLE", RC_SETUP_UNUSABLE);
    }
    if (!GetFileInformationByHandle(file, &info) ||
        (info.dwFileAttributes & (FILE_ATTRIBUTE_DIRECTORY | FILE_ATTRIBUTE_REPARSE_POINT)) != 0) {
        CloseHandle(file);
        r.hash = "not-regular";
        return finish(&opt, &r, "SETUP_UNUSABLE", RC_SETUP_UNUSABLE);
    }
    if (!sha256_of(file, actual_sha)) {
        CloseHandle(file);
        r.hash = "unreadable";
        return finish(&opt, &r, "SETUP_UNUSABLE", RC_SETUP_UNUSABLE);
    }
    if (strcmp(actual_sha, opt.expected_sha) != 0) {
        CloseHandle(file);
        r.hash = "mismatch";
        return finish(&opt, &r, "SETUP_UNUSABLE", RC_SETUP_UNUSABLE);
    }
    r.hash = "ok";

    if (opt.skip_signature) {
        r.signature = "skipped-test";
    } else {
        sig = verify_signature(file, full);
        if (sig == SIG_UNVERIFIABLE) {
            CloseHandle(file);
            r.signature = "unverifiable";
            return finish(&opt, &r, "SIGNATURE_UNVERIFIED", RC_SIGNATURE_UNVERIFIED);
        }
        if (sig != SIG_OK) {
            CloseHandle(file);
            r.signature = sig == SIG_SIGNER ? "signer-mismatch" : "invalid";
            return finish(&opt, &r, "SIGNATURE_INVALID", RC_SIGNATURE_INVALID);
        }
        r.signature = "ok";
    }

    if (opt.mode == MODE_VERIFY) {
        CloseHandle(file);
        return finish(&opt, &r, "VERIFIED", 0);
    }

    /* Segunda deteccion justo antes de ejecutar. */
    d = detect(opt.service, r.version, sizeof r.version);
    r.detect = detection_name(d);
    if (d != DET_ABSENT) {
        CloseHandle(file);
        return finish(&opt, &r, detection_result(d), detection_code(d));
    }

    run = run_setup(full, opt.timeout_ms, &child_code, &r.child_pid, &r.elapsed_ms);
    CloseHandle(file);
    if (run == RUN_START_FAILED) {
        snprintf(r.child, sizeof r.child, "start-error-%lu", (unsigned long)child_code);
        return finish(&opt, &r, "SETUP_FAILED", RC_SETUP_FAILED);
    }
    if (run == RUN_TIMEOUT) {
        snprintf(r.child, sizeof r.child, "running");
        return finish(&opt, &r, "TIMEOUT", RC_TIMEOUT);
    }
    snprintf(r.child, sizeof r.child, "%lu", (unsigned long)child_code);
    if (child_code != 0 && child_code != 3010) {
        return finish(&opt, &r, "SETUP_FAILED", RC_SETUP_FAILED);
    }

    if (!opt.assume_installed) {
        d = detect(opt.service, r.version, sizeof r.version);
        r.detect = detection_name(d);
        if (d != DET_COMPATIBLE && d != DET_OTHER) {
            return finish(&opt, &r, "NOT_CORROBORATED", RC_NOT_CORROBORATED);
        }
    }
    return child_code == 3010
        ? finish(&opt, &r, "INSTALLED_REBOOT_REQUIRED", RC_INSTALLED_REBOOT)
        : finish(&opt, &r, "INSTALLED", RC_INSTALLED);
}
