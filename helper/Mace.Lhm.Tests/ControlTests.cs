using System.Buffers.Binary;
using System.Diagnostics;
using System.IO.Pipes;
using Mace.Lhm.Protocol;

internal static class ControlTests
{
    public static async Task<int> RunAsync()
    {
        int passed = 0;
        void Check(bool value, string label)
        {
            if (!value) throw new Exception("Control test failed: " + label);
            passed++;
        }
        void Reject(byte[] frame, ulong sequence = 0)
        {
            try { ControlProtocol.Accept(frame, 123, sequence); }
            catch (InvalidDataException) { passed++; return; }
            throw new Exception("Invalid control accepted");
        }
        byte[] golden = ControlProtocol.Shutdown(123);
        Check(Convert.ToHexString(golden) == "4D43433102002000200000000100000001000000000000007B00000000000000", "C-compatible golden frame");
        Check(ControlProtocol.Accept(golden, 123, 0) == 1, "first shutdown");
        Reject(golden, 1); // A duplicate is never idempotently accepted as another command.
        foreach ((int offset, uint value) in new[] { (0, 0u), (4, 0x00200001u), (6, 31u), (8, 4096u),
            (12, 2u), (16, 0u), (16, 2u), (24, 124u), (28, 1u) })
        {
            byte[] bad = golden.ToArray(); BinaryPrimitives.WriteUInt32LittleEndian(bad.AsSpan(offset), value); Reject(bad);
        }
        Reject(golden[..^1]); Reject([..golden, 0]);

        async Task MemoryCase(byte[] bytes, bool invalid)
        {
            bool close = false;
            try
            {
                await ControlProtocol.ListenAsync(new MemoryStream(bytes), 123, () => close = true,
                    CancellationToken.None).WaitAsync(TimeSpan.FromSeconds(2));
                if (invalid) throw new Exception("Invalid control stream accepted");
            }
            catch (Exception error) when (invalid && error is InvalidDataException or EndOfStreamException) { }
            Check(close, invalid ? "invalid/duplicate stream shuts down without hanging" : "EOF independent of control");
        }
        await MemoryCase([], false);
        await MemoryCase(golden[..8], true);
        await MemoryCase([..golden, ..golden], true);

        foreach (string scenario in new[] { "shutdown", "eof", "duplicate", "partial", "wrong-pid" })
        {
            string name = PrivatePipe.NewName();
            using var server = PrivatePipe.CreateServer(name);
            using var client = new NamedPipeClientStream(".", name, PipeDirection.InOut, PipeOptions.Asynchronous);
            using var lifetime = new CancellationTokenSource(TimeSpan.FromSeconds(5));
            Task connection = server.WaitForConnectionAsync(lifetime.Token);
            await client.ConnectAsync(lifetime.Token); await connection;
            uint pid = (uint)Environment.ProcessId;
            PrivatePipe.VerifyClient(server, pid); PrivatePipe.VerifyServer(client, pid);
            bool close = false;
            Task listener = ControlProtocol.ListenAsync(client, pid, () => close = true, lifetime.Token);
            byte[] command = ControlProtocol.Shutdown(scenario == "wrong-pid" ? pid + 1 : pid);
            var clock = Stopwatch.StartNew();
            if (scenario == "eof") server.Dispose();
            else await server.WriteAsync(scenario == "duplicate" ? [..command, ..command]
                : scenario == "partial" ? command[..5] : command, lifetime.Token);
            bool invalid = scenario is "duplicate" or "partial" or "wrong-pid";
            try
            {
                await listener.WaitAsync(TimeSpan.FromSeconds(2));
                if (invalid) throw new Exception("Invalid duplex command accepted");
            }
            catch (InvalidDataException) when (invalid) { }
            Check(close && clock.ElapsedMilliseconds < 2000, scenario + " bounded duplex result");
            if (scenario == "shutdown") Check(server.IsConnected, "control exits without requiring EOF");
        }
        return passed;
    }
}
