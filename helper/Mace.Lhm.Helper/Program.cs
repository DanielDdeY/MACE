using System.IO.Pipes;
using System.Security.Principal;
using Mace.Lhm.Helper;
using Mace.Lhm.Protocol;

try
{
    string pipeName;
    uint parentPid;
    if (args is ["--bootstrap-stdin"])
    {
        (pipeName,parentPid) = Bootstrap.Read(Console.OpenStandardInput());
    }
    else if (args.Length == 4 && args[0] == "--pipe" && args[2] == "--parent-pid"
        && uint.TryParse(args[3], out parentPid) && parentPid != 0)
    {
        // Compatibility for the 1A reference harness only.
        pipeName=args[1];
        PrivatePipe.ValidateName(pipeName);
    }
    else throw new ArgumentException("Expected --bootstrap-stdin (production) or 1A test arguments.");
    using var identity = WindowsIdentity.GetCurrent();
    bool elevated = new WindowsPrincipal(identity).IsInRole(WindowsBuiltInRole.Administrator);
    using var shutdown = new CancellationTokenSource();
    Console.CancelKeyPress += (_, e) => { e.Cancel = true; shutdown.Cancel(); };
    using var pipe = new NamedPipeClientStream(".", pipeName, PipeDirection.Out, PipeOptions.Asynchronous,
        TokenImpersonationLevel.Identification);
    await pipe.ConnectAsync(5000, shutdown.Token);
    PrivatePipe.VerifyServer(pipe, parentPid);
    ulong sequence = 0;
    async Task Send(HelperState state, DriverState driver, DeviceSample[] devices)
    {
        var snapshot = new Snapshot(++sequence, (ulong)Environment.TickCount64, (uint)Environment.ProcessId, driver, state, devices, elevated);
        byte[] bytes = WireProtocol.Encode(snapshot);
        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(shutdown.Token);
        deadline.CancelAfter(TimeSpan.FromSeconds(2));
        await pipe.WriteAsync(bytes, deadline.Token);
        await pipe.FlushAsync(deadline.Token);
    }
    await Send(HelperState.WarmingUp, PawnIoStatus.Read(), []);
    using var sampler = new SensorSampler();
    sampler.Open();
    using var interval = new PeriodicTimer(TimeSpan.FromSeconds(1));
    do
    {
        DriverState driver = PawnIoStatus.Read();
        DeviceSample[] devices;
        try { devices = sampler.Poll(driver); }
        catch (Exception error) when (error is not OutOfMemoryException)
        {
            await Send(HelperState.ReadFailed, driver, []);
            continue;
        }
        try { await Send(HelperState.Ready, driver, devices); }
        catch (InvalidDataException) { await Send(HelperState.ReadFailed, driver, []); }
    } while (await interval.WaitForNextTickAsync(shutdown.Token));
    return 0;
}
catch (OperationCanceledException) { return 0; }
catch (IOException error) when ((error.HResult & 0xFFFF) is 109 or 232 or 233) { return 0; }
catch (Exception error)
{
    Console.Error.WriteLine(error.GetType().Name + ": " + error.Message);
    return 1;
}
