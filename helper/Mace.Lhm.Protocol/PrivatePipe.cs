using System.ComponentModel;
using System.IO.Pipes;
using System.Runtime.InteropServices;
using System.Security.AccessControl;
using System.Security.Cryptography;
using System.Security.Principal;
using System.Text.RegularExpressions;
using Microsoft.Win32.SafeHandles;

namespace Mace.Lhm.Protocol;

public static partial class PrivatePipe
{
    public static string NewName() => "mace-lhm-" + Convert.ToHexString(RandomNumberGenerator.GetBytes(32)).ToLowerInvariant();

    public static void ValidateName(string name)
    {
        if (!PipeNamePattern().IsMatch(name)) throw new ArgumentException("Invalid private pipe name.");
    }

    // Reference host for the 1A tests. The native receiver must apply the same Win32 flags in 1B.
    public static NamedPipeServerStream CreateServer(string name)
    {
        ValidateName(name);
        using var identity = WindowsIdentity.GetCurrent();
        string sid = identity.User?.Value ?? throw new InvalidOperationException("Missing user SID.");
        var descriptor = new RawSecurityDescriptor($"O:{sid}D:P(A;;GRGW;;;SY)(A;;GRGW;;;{sid})");
        byte[] bytes = new byte[descriptor.BinaryLength];
        descriptor.GetBinaryForm(bytes, 0);
        IntPtr memory = Marshal.AllocHGlobal(bytes.Length);
        try
        {
            Marshal.Copy(bytes, 0, memory, bytes.Length);
            var attributes = new SecurityAttributes { Length = Marshal.SizeOf<SecurityAttributes>(), Descriptor = memory };
            // DUPLEX | FIRST_PIPE_INSTANCE | OVERLAPPED; BYTE | REJECT_REMOTE_CLIENTS.
            var handle = CreateNamedPipeW(@"\\.\pipe\" + name, 0x40080003, 0x8, 1,
                WireProtocol.MaxFrameSize, WireProtocol.MaxFrameSize, 0, ref attributes);
            if (handle.IsInvalid)
            {
                int error = Marshal.GetLastWin32Error();
                handle.Dispose();
                throw new Win32Exception(error);
            }
            try { return new NamedPipeServerStream(PipeDirection.InOut, true, false, handle); }
            catch { handle.Dispose(); throw; }
        }
        finally { Marshal.FreeHGlobal(memory); }
    }

    public static void VerifyClient(NamedPipeServerStream server, uint expectedPid)
    {
        if (!GetNamedPipeClientProcessId(server.SafePipeHandle, out uint actualPid))
            throw new Win32Exception(Marshal.GetLastWin32Error());
        if (actualPid != expectedPid) throw new UnauthorizedAccessException("Unexpected helper PID.");
    }

    public static void VerifyServer(NamedPipeClientStream client, uint expectedPid)
    {
        if (!GetNamedPipeServerProcessId(client.SafePipeHandle, out uint actualPid))
            throw new Win32Exception(Marshal.GetLastWin32Error());
        if (actualPid != expectedPid) throw new UnauthorizedAccessException("Unexpected parent PID.");
    }

    [GeneratedRegex("^mace-lhm-[a-f0-9]{64}$", RegexOptions.CultureInvariant)]
    private static partial Regex PipeNamePattern();

    [StructLayout(LayoutKind.Sequential)]
    private struct SecurityAttributes
    {
        public int Length;
        public IntPtr Descriptor;
        public int InheritHandle;
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern SafePipeHandle CreateNamedPipeW(string name, uint openMode, uint pipeMode,
        uint instances, int outSize, int inSize, uint timeout, ref SecurityAttributes attributes);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetNamedPipeClientProcessId(SafePipeHandle pipe, out uint pid);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetNamedPipeServerProcessId(SafePipeHandle pipe, out uint pid);
}
