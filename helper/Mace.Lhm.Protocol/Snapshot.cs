namespace Mace.Lhm.Protocol;

public enum DeviceKind : uint { Cpu = 1, Nvidia = 2, Amd = 3, Intel = 4 }
public enum IdentityKind : uint { Unknown = 0, Cpu = 1, UniquePci = 2 }
public enum ReadingState : uint { NumericReported = 1, WarmingUp = 2, NoProvider = 3, Unsupported = 4, ReadFailed = 5 }
public enum HelperState : uint { WarmingUp = 1, Ready = 2, ReadFailed = 3 }
public enum DriverState : uint { Missing = 1, Stopped = 2, Running = 3, Unknown = 4 }
public enum Meaning : uint { None = 0, CpuPackage = 1, CpuControlTemperature = 2, GpuCore = 3, GpuBoard = 4, GpuChip = 5, UnknownPower = 6 }

public sealed record PciIdentity(uint Vendor, uint Device, uint Subsystem, uint Revision);
public sealed record Reading(double Value, ReadingState State, Meaning Meaning = Meaning.None)
{
    public static Reading Missing(ReadingState state = ReadingState.Unsupported) => new(0, state);
}

// Fixed order is part of the wire contract; CPU usage continues to come from GetSystemTimes.
public sealed record DeviceSample(DeviceKind Kind, IdentityKind Identity, PciIdentity Pci, Reading[] Metrics);
public sealed record Snapshot(ulong Sequence, ulong UptimeMs, uint SenderPid,
    DriverState Driver, HelperState State, DeviceSample[] Devices, bool Elevated = false);
