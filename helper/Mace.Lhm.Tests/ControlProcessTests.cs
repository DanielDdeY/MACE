using System.Buffers.Binary;
using System.Diagnostics;
using System.Text;
using Mace.Lhm.Protocol;

internal static class ControlProcessTests
{
    public static async Task<int> RunAsync(string executable)
    {
        int passed = 0;
        foreach (string mode in new[] { "control", "eof", "invalid", "duplicate", "partial" })
        {
            string name = PrivatePipe.NewName();
            using var server = PrivatePipe.CreateServer(name);
            using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(8));
            Task connect = server.WaitForConnectionAsync(deadline.Token);
            var start = new ProcessStartInfo(Path.GetFullPath(executable)) {
                UseShellExecute = false, CreateNoWindow = true, RedirectStandardInput = true,
                RedirectStandardOutput = true, RedirectStandardError = true
            };
            start.ArgumentList.Add("--bootstrap-stdin");
            using var child = Process.Start(start) ?? throw new Exception("Helper did not start");
            Task<string> errors = child.StandardError.ReadToEndAsync();
            Task<string> output = child.StandardOutput.ReadToEndAsync();
            try
            {
                byte[] bootstrap = new byte[128];
                BinaryPrimitives.WriteUInt32LittleEndian(bootstrap, 0x3142434D);
                BinaryPrimitives.WriteUInt32LittleEndian(bootstrap.AsSpan(4), WireProtocol.Version);
                BinaryPrimitives.WriteUInt32LittleEndian(bootstrap.AsSpan(8), (uint)Environment.ProcessId);
                BinaryPrimitives.WriteUInt32LittleEndian(bootstrap.AsSpan(12), 73);
                Encoding.ASCII.GetBytes(name).CopyTo(bootstrap, 16);
                await child.StandardInput.BaseStream.WriteAsync(bootstrap, deadline.Token);
                child.StandardInput.Close();
                await connect;
                PrivatePipe.VerifyClient(server, (uint)child.Id);
                byte[] warm = await WireProtocol.ReadFrameAsync(server, deadline.Token);
                if (WireProtocol.Decode(warm, (uint)child.Id, 0, (ulong)Environment.TickCount64).State != HelperState.WarmingUp)
                    throw new Exception("Missing warmup frame");
                var clock = Stopwatch.StartNew();
                byte[] control = ControlProtocol.Shutdown((uint)Environment.ProcessId);
                if (mode == "eof") server.Dispose();
                else
                {
                    if (mode == "invalid") control[12] = 99;
                    byte[] message = mode == "duplicate" ? [..control, ..control] : mode == "partial" ? control[..8] : control;
                    await server.WriteAsync(message, deadline.Token);
                }
                await child.WaitForExitAsync(deadline.Token);
                int expected = mode is "control" or "eof" ? 0 : 1;
                if (child.ExitCode != expected || clock.ElapsedMilliseconds >= 3000)
                    throw new Exception($"Control case {mode} failed: code={child.ExitCode}, elapsed={clock.ElapsedMilliseconds}");
                if (mode == "control" && !server.IsConnected) throw new Exception("Control depended on EOF");
                await errors; await output;
                Console.WriteLine($"helper control {mode}: code={child.ExitCode}, elapsed={clock.ElapsedMilliseconds} ms");
                passed++;
            }
            finally
            {
                if (!child.HasExited) { child.Kill(entireProcessTree: true); await child.WaitForExitAsync(); }
            }
        }
        return passed;
    }
}
