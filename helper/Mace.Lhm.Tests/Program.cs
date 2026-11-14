using System.Buffers.Binary;
using System.Diagnostics;
using System.IO.Pipes;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Text.Json;
using Mace.Lhm.Helper;
using Mace.Lhm.Protocol;

if (args is ["--amd-power-report"])
{
    var computer=new LibreHardwareMonitor.Hardware.Computer { IsGpuEnabled=true };
    computer.Open();
    try
    {
    foreach (var hardware in computer.Hardware.Where(h => h.HardwareType==LibreHardwareMonitor.Hardware.HardwareType.GpuAmd))
    {
        for (int i=0;i<3;i++) { hardware.Update(); await Task.Delay(1000); }
        Console.WriteLine("LHM "+typeof(LibreHardwareMonitor.Hardware.Computer).Assembly.GetName().Version);
        Console.WriteLine(hardware.Name+" "+((LibreHardwareMonitor.Hardware.Gpu.GenericGpu)hardware).DeviceId);
        foreach (var sensor in hardware.Sensors.Where(s => s.SensorType==LibreHardwareMonitor.Hardware.SensorType.Power))
            Console.WriteLine($"POWER name={sensor.Name} id={sensor.Identifier} value={sensor.Value}");
        Console.WriteLine(hardware.GetReport());
    }
    }
    finally { computer.Close(); }
    return;
}

int passed = 0;
void Check(bool condition, string label)
{
    if (!condition) throw new Exception("FAILED: " + label);
    passed++;
}
void Reject(Action action, string label)
{
    try { action(); }
    catch (Exception e) when (e is InvalidDataException or ArgumentException or UnauthorizedAccessException or System.ComponentModel.Win32Exception)
    { passed++; return; }
    throw new Exception("FAILED: accepted " + label);
}
DeviceSample Cpu() => new(DeviceKind.Cpu, IdentityKind.Cpu, new(0,0,0,0),
    Enumerable.Range(0,7).Select(_ => Reading.Missing()).ToArray());
Snapshot Sample() => new(1, 10000, 123, DriverState.Running, HelperState.Ready, [Cpu()]);
Snapshot Decode(byte[] data) => WireProtocol.Decode(data,123,0,10000);

string asicReport="Overdrive Caps\n Status: ADL_OK\n Version: 8\nOverdrive5 Parameters\n"
    +" Sensor[ADL_PMLOG_ASIC_POWER].Supported: True\n Sensor[ADL_PMLOG_ASIC_POWER].Value: 5\n Sensor[ADL_PMLOG_BOARD_POWER].Supported: False\n";
PciIdentity rx5500=new(0x1002,0x7340,0x38221462,0xC5);
Meaning Scope(PciIdentity pci,string report)=>AmdPowerPolicy.Classify(pci,new Version(0,9,6,0),"GPU Package",3,report);
Check(Scope(rx5500,asicReport)==Meaning.GpuChip,"RX5500 identified ASIC power is chip");
Check(Scope(rx5500,asicReport.Replace("BOARD_POWER].Supported: False","BOARD_POWER].Supported: True"))==Meaning.UnknownPower,"board fallback remains unknown");
Check(Scope(rx5500,asicReport.Replace("ASIC_POWER].Supported: True","ASIC_POWER].Supported: False"))==Meaning.UnknownPower,"missing ASIC evidence");
Check(Scope(rx5500,asicReport.Replace("Version: 8","Version: 7"))==Meaning.UnknownPower,"other backend remains unknown");
Check(Scope(rx5500 with { Device=0x744C },asicReport)==Meaning.UnknownPower,"unvalidated AMD model remains unknown");
Check(AmdPowerPolicy.Classify(rx5500,new Version(0,9,7,0),"GPU Package",3,asicReport)==Meaning.UnknownPower,"other LHM version remains unknown");
Check(AmdPowerPolicy.Classify(rx5500,new Version(0,9,6,0),"GPU Power",3,asicReport)==Meaning.UnknownPower,"label alone insufficient");
Check(AmdPowerPolicy.Classify(rx5500,new Version(0,9,6,0),"GPU Package",0,asicReport)==Meaning.UnknownPower,"sensor index required");
Reading unknownPower=new(9,ReadingState.NumericReported,Meaning.UnknownPower);
Reading Resolve(string report)=>AmdPowerPolicy.Resolve(rx5500,new Version(0,9,6,0),"GPU Package",3,unknownPower,report);
Check(Resolve(asicReport)==new Reading(5,ReadingState.NumericReported,Meaning.GpuChip),"use identified ASIC value, not ambiguous package fallback");
Check(Resolve(asicReport.Replace(".Value: 5",".Value: NaN"))==unknownPower,"invalid identified value remains unknown");
Check(Resolve(asicReport.Replace(".Value: 5",".Value: 2001"))==unknownPower,"out of range identified value remains unknown");
Check(Resolve(asicReport.Replace(".Value: 5",".Value: 0"))==new Reading(0,ReadingState.NumericReported,Meaning.GpuChip),"zero identified ASIC power remains numeric");
Check(Resolve(asicReport+"Sensor[ADL_PMLOG_ASIC_POWER].Value: 7\n")==unknownPower,"ambiguous report value remains unknown");

byte[] startup=new byte[128];
BinaryPrimitives.WriteUInt32LittleEndian(startup,0x3142434D);
BinaryPrimitives.WriteUInt32LittleEndian(startup.AsSpan(4),1);
BinaryPrimitives.WriteUInt32LittleEndian(startup.AsSpan(8),123);
BinaryPrimitives.WriteUInt32LittleEndian(startup.AsSpan(12),73);
System.Text.Encoding.ASCII.GetBytes("mace-lhm-"+new string('a',64)).CopyTo(startup,16);
Check(Bootstrap.Read(new MemoryStream(startup))==("mace-lhm-"+new string('a',64),123u),"private bootstrap roundtrip");
startup[127]=1;
Reject(()=>Bootstrap.Read(new MemoryStream(startup)),"bootstrap trailing data");
startup[127]=0; startup[8]=0;
Reject(()=>Bootstrap.Read(new MemoryStream(startup)),"bootstrap parent PID");

byte[] frame = WireProtocol.Encode(Sample());
Check(frame.Length == 192, "fixed layout");
Check(Decode(frame).Devices.Length == 1, "roundtrip");
foreach (int length in new[] {0,1,47,49,WireProtocol.MaxFrameSize+1})
    Reject(() => Decode(new byte[length]), "frame size");
foreach ((int offset, uint value) in new[] {(0,0u),(8,4096u),(12,2u),(32,124u),(36,0u),(40,99u),(44,18u),(48,99u),(52,99u),(56,1u),(88,99u),(92,99u)})
{
    byte[] bad = frame.ToArray();
    BinaryPrimitives.WriteUInt32LittleEndian(bad.AsSpan(offset),value);
    Reject(() => Decode(bad), "invalid wire field " + offset);
}
byte[] oldVersion = frame.ToArray();
oldVersion[4] = 2;
Reject(() => Decode(oldVersion), "version");
Reject(() => WireProtocol.Decode(frame,123,1,10000), "replay");
Reject(() => WireProtocol.Decode(frame,123,0,14000), "stale sample");
Reject(() => WireProtocol.Decode(frame,123,0,9999), "future sample");
foreach (double invalid in new[] {double.NaN,double.PositiveInfinity,-51,151})
{
    var cpu = Cpu(); cpu.Metrics[0] = new(invalid,ReadingState.NumericReported,Meaning.CpuPackage);
    Reject(() => WireProtocol.Encode(Sample() with {Devices=[cpu]}), "invalid temperature");
}
var zero = Cpu(); zero.Metrics[1] = new(0,ReadingState.NumericReported,Meaning.CpuPackage);
Check(Decode(WireProtocol.Encode(Sample() with {Devices=[zero]})).Devices[0].Metrics[1].State == ReadingState.NumericReported,
    "zero remains raw, never automatically valid");
Reject(() => WireProtocol.Encode(Sample() with {Devices=[Cpu(),Cpu()]}), "duplicate CPU");
var gpu = new DeviceSample(DeviceKind.Amd,IdentityKind.UniquePci,new(0x1002,0x7340,0x38221462,0xC5),
    Enumerable.Range(0,7).Select(_ => Reading.Missing()).ToArray());
Reject(() => WireProtocol.Encode(Sample() with {Devices=[gpu,gpu]}), "duplicate PCI");
gpu.Metrics[0] = new(45,ReadingState.NumericReported,Meaning.GpuCore);
Reject(() => WireProtocol.Encode(Sample() with {Devices=[gpu with {Identity=IdentityKind.Unknown}]}), "ambiguous GPU data");
gpu.Metrics[5] = new(2,ReadingState.NumericReported);
gpu.Metrics[6] = new(1,ReadingState.NumericReported);
Reject(() => WireProtocol.Encode(Sample() with {Devices=[gpu]}), "memory coherence");
var pci = PciAssociation.Parse(@"PCI\VEN_1002&DEV_7340&SUBSYS_38221462&REV_C5\INSTANCE");
Check(pci == gpu.Pci, "PCI parsing");
Check(PciAssociation.Parse("NVIDIA RTX 3060") == null, "name is not identity");
Check(!PciAssociation.IsUnique(gpu.Pci,[gpu.Pci,gpu.Pci]), "identical GPUs ambiguous");
Check(PciAssociation.IsUnique(gpu.Pci,[gpu.Pci]), "unique PCI");
byte[] oversizedPrefix = new byte[12];
BinaryPrimitives.WriteUInt32LittleEndian(oversizedPrefix.AsSpan(8),uint.MaxValue);
using (var oversized = new MemoryStream(oversizedPrefix))
{
    try { await WireProtocol.ReadFrameAsync(oversized,CancellationToken.None); throw new Exception("allocation accepted"); }
    catch (InvalidDataException) { passed++; }
}
using (var truncated = new MemoryStream(frame[..^1]))
{
    try { await WireProtocol.ReadFrameAsync(truncated,CancellationToken.None); throw new Exception("truncation accepted"); }
    catch (EndOfStreamException) { passed++; }
}
string name = PrivatePipe.NewName();
Check(name != PrivatePipe.NewName(), "random pipe names");
Reject(() => PrivatePipe.ValidateName(@"\\remote\pipe\x"), "remote pipe name");
using (var server = PrivatePipe.CreateServer(name))
{
    Reject(() => { using var duplicate = PrivatePipe.CreateServer(name); }, "first instance exclusivity");
    var security = server.GetAccessControl();
    using var identity = WindowsIdentity.GetCurrent();
    var allowed = security.GetAccessRules(true,true,typeof(SecurityIdentifier)).Cast<PipeAccessRule>().ToArray();
    Check(allowed.Length == 2 && allowed.All(a => a.AccessControlType == AccessControlType.Allow &&
        (a.IdentityReference.Equals(identity.User) || a.IdentityReference.Value == "S-1-5-18")), "actual ACL only user and SYSTEM");
    using var client = new NamedPipeClientStream(".",name,PipeDirection.Out,PipeOptions.Asynchronous);
    using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(5));
    Task connection = server.WaitForConnectionAsync(deadline.Token);
    await client.ConnectAsync(deadline.Token);
    await connection;
    PrivatePipe.VerifyClient(server,(uint)Environment.ProcessId);
    PrivatePipe.VerifyServer(client,(uint)Environment.ProcessId);
    passed += 2;
    Reject(() => PrivatePipe.VerifyClient(server,uint.MaxValue), "wrong client PID");
    Reject(() => PrivatePipe.VerifyServer(client,uint.MaxValue), "wrong server PID");
    var read = WireProtocol.ReadFrameAsync(server,deadline.Token);
    foreach (byte value in frame) await client.WriteAsync(new byte[] {value},deadline.Token);
    Check((await read).SequenceEqual(frame), "fragmented pipe frame");
}
if (args.Length > 0)
{
    string executable = Path.GetFullPath(args[0]);
    string liveName = PrivatePipe.NewName();
    using var live = PrivatePipe.CreateServer(liveName);
    var start = new ProcessStartInfo(executable) { UseShellExecute=false,CreateNoWindow=true,RedirectStandardError=true };
    start.ArgumentList.Add("--pipe"); start.ArgumentList.Add(liveName);
    start.ArgumentList.Add("--parent-pid"); start.ArgumentList.Add(Environment.ProcessId.ToString());
    var clock = Stopwatch.StartNew();
    using var child = Process.Start(start) ?? throw new Exception("Helper failed to start");
    var stderr = child.StandardError.ReadToEndAsync();
    try
    {
        using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(20));
        await live.WaitForConnectionAsync(deadline.Token);
        PrivatePipe.VerifyClient(live,(uint)child.Id);
        ulong previous = 0;
        double? firstReadyMs = null;
        Snapshot? latest = null;
        for (int i = 0; i < 5; i++)
        {
            byte[] data = await WireProtocol.ReadFrameAsync(live,deadline.Token);
            latest = WireProtocol.Decode(data,(uint)child.Id,previous,(ulong)Environment.TickCount64);
            if (i == 0) Check(latest.State == HelperState.WarmingUp && latest.Devices.Length == 0,"warming up before sensor initialization");
            previous = latest.Sequence;
            if (latest.State == HelperState.Ready) firstReadyMs ??= clock.Elapsed.TotalMilliseconds;
        }
        Check(firstReadyMs.HasValue,"live hardware snapshot");
        Console.WriteLine(JsonSerializer.Serialize(new {firstReadyMs,latest}));
        live.Dispose();
        using var exitDeadline = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        await child.WaitForExitAsync(exitDeadline.Token);
        Check(child.HasExited && child.ExitCode == 0,"helper exits cleanly after disconnection");
        Console.WriteLine("Helper stderr: " + await stderr);
    }
    finally { if (!child.HasExited) { child.Kill(); await child.WaitForExitAsync(); } }
}
Console.WriteLine($"PASS: {passed} checks");
