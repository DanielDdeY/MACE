#include <windows.h>
#include <stdint.h>

typedef struct {
    uint32_t target_pid;
    HWND found_hwnd;
} WindowSearchContext;

static BOOL CALLBACK EnumWindowsCallback(HWND hwnd, LPARAM lParam) {
    WindowSearchContext* ctx = (WindowSearchContext*)lParam;
    DWORD pid = 0;
    GetWindowThreadProcessId(hwnd, &pid);

    if (pid == ctx->target_pid && IsWindowVisible(hwnd)) {
        ctx->found_hwnd = hwnd;
        return FALSE; // Detener búsqueda
    }
    return TRUE;
}

int CheckWindowHung(uint32_t pid) {
    WindowSearchContext ctx = { pid, NULL };
    EnumWindows(EnumWindowsCallback, (LPARAM)&ctx);

    if (ctx.found_hwnd != NULL) {
        return IsHungAppWindow(ctx.found_hwnd) ? 1 : 0;
    }

    return 0; // Proceso sin ventana interactiva o responsivo
}