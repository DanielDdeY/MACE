/*
 * Setup simulado SOLO para pruebas del coordinador. No instala nada.
 * Exige exactamente "-install -silent"; si no, devuelve 87.
 * MACE_STUB_MARKER: ruta de un archivo que se crea al ejecutarse.
 * MACE_STUB_SLEEP_MS: espera antes de salir.  MACE_STUB_EXIT: codigo de salida.
 */
#include <windows.h>
#include <stdlib.h>
#include <wchar.h>

static DWORD env_number(const wchar_t *name, DWORD fallback)
{
    wchar_t value[32];
    DWORD n = GetEnvironmentVariableW(name, value, (DWORD)(sizeof value / sizeof value[0]));
    if (n == 0 || n >= sizeof value / sizeof value[0]) {
        return fallback;
    }
    return (DWORD)wcstoul(value, NULL, 10);
}

int wmain(int argc, wchar_t **argv)
{
    wchar_t marker[4096];
    DWORD n;

    if (argc != 3 || wcscmp(argv[1], L"-install") != 0 || wcscmp(argv[2], L"-silent") != 0) {
        return 87;
    }
    n = GetEnvironmentVariableW(L"MACE_STUB_MARKER", marker, (DWORD)(sizeof marker / sizeof marker[0]));
    if (n > 0 && n < sizeof marker / sizeof marker[0]) {
        HANDLE f = CreateFileW(marker, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
        if (f != INVALID_HANDLE_VALUE) {
            CloseHandle(f);
        }
    }
    Sleep(env_number(L"MACE_STUB_SLEEP_MS", 0));
    return (int)env_number(L"MACE_STUB_EXIT", 0);
}
