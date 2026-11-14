using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;
using Mace.Lhm.Protocol;

namespace Mace.Lhm.Helper;

public static class PawnIoStatus
{
    public static DriverState Read()
    {
        using var manager = OpenSCManagerW(null, null, 1); // SC_MANAGER_CONNECT
        if (manager.IsInvalid) return DriverState.Unknown;
        using var service = OpenServiceW(manager, "PawnIO", 4); // SERVICE_QUERY_STATUS
        if (service.IsInvalid) return Marshal.GetLastWin32Error() == 1060 ? DriverState.Missing : DriverState.Unknown;
        if (!QueryServiceStatus(service, out var status)) return DriverState.Unknown;
        return status.CurrentState == 4 ? DriverState.Running : DriverState.Stopped;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct ServiceStatus
    {
        public uint ServiceType, CurrentState, ControlsAccepted, Win32ExitCode, ServiceExitCode, Checkpoint, WaitHint;
    }

    private sealed class ServiceHandle : SafeHandleZeroOrMinusOneIsInvalid
    {
        private ServiceHandle() : base(true) { }
        protected override bool ReleaseHandle() => CloseServiceHandle(handle);
    }

    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern ServiceHandle OpenSCManagerW(string? machine, string? database, uint access);
    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern ServiceHandle OpenServiceW(ServiceHandle manager, string name, uint access);
    [DllImport("advapi32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool QueryServiceStatus(ServiceHandle service, out ServiceStatus status);
    [DllImport("advapi32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CloseServiceHandle(IntPtr handle);
}
