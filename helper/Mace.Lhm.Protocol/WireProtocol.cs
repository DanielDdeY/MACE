using System.Buffers.Binary;

namespace Mace.Lhm.Protocol;

public static class WireProtocol
{
    public const uint Magic = 0x314D484C; // LHM1
    public const ushort Version = 2;
    public const int HeaderSize = 48;
    public const int DeviceSize = 144;
    public const int MaxDevices = 17;
    public const int MaxFrameSize = HeaderSize + MaxDevices * DeviceSize;

    public static byte[] Encode(Snapshot sample)
    {
        Validate(sample);
        using var stream = new MemoryStream();
        using var writer = new BinaryWriter(stream);
        writer.Write(Magic);
        writer.Write(Version);
        writer.Write((ushort)HeaderSize);
        writer.Write((uint)(HeaderSize + sample.Devices.Length * DeviceSize));
        writer.Write(sample.Elevated ? 1u : 0u);
        writer.Write(sample.Sequence);
        writer.Write(sample.UptimeMs);
        writer.Write(sample.SenderPid);
        writer.Write((uint)sample.Driver);
        writer.Write((uint)sample.State);
        writer.Write((uint)sample.Devices.Length);
        foreach (var device in sample.Devices)
        {
            writer.Write((uint)device.Kind);
            writer.Write((uint)device.Identity);
            writer.Write(0u); // reserved for LUID low/high in a future protocol version
            writer.Write(0u);
            writer.Write(device.Pci.Vendor);
            writer.Write(device.Pci.Device);
            writer.Write(device.Pci.Subsystem);
            writer.Write(device.Pci.Revision);
            foreach (var metric in device.Metrics)
            {
                writer.Write(metric.Value);
                writer.Write((uint)metric.State);
                writer.Write((uint)metric.Meaning);
            }
        }
        return stream.ToArray();
    }

    public static Snapshot Decode(byte[] frame, uint expectedPid, ulong previousSequence, ulong nowMs)
    {
        CheckLength(frame);
        using var reader = new BinaryReader(new MemoryStream(frame, false));
        if (reader.ReadUInt32() != Magic || reader.ReadUInt16() != Version || reader.ReadUInt16() != HeaderSize)
            throw new InvalidDataException("Unsupported protocol header.");
        if (reader.ReadUInt32() != frame.Length) throw new InvalidDataException("Invalid length.");
        uint flags = reader.ReadUInt32();
        if ((flags & ~1u) != 0) throw new InvalidDataException("Reserved flags are nonzero.");
        ulong sequence = reader.ReadUInt64();
        ulong timestamp = reader.ReadUInt64();
        uint pid = reader.ReadUInt32();
        var driver = (DriverState)reader.ReadUInt32();
        var state = (HelperState)reader.ReadUInt32();
        uint count = reader.ReadUInt32();
        if (count > MaxDevices || HeaderSize + count * DeviceSize != frame.Length)
            throw new InvalidDataException("Invalid device count.");
        if (pid != expectedPid || sequence <= previousSequence || timestamp > nowMs || nowMs - timestamp > 3000)
            throw new InvalidDataException("Wrong process, sequence or stale timestamp.");
        var devices = new List<DeviceSample>();
        for (int i = 0; i < count; i++)
        {
            var kind = (DeviceKind)reader.ReadUInt32();
            var identity = (IdentityKind)reader.ReadUInt32();
            if (reader.ReadUInt64() != 0) throw new InvalidDataException("Reserved LUID fields must be zero.");
            var pci = new PciIdentity(reader.ReadUInt32(), reader.ReadUInt32(), reader.ReadUInt32(), reader.ReadUInt32());
            var metrics = new Reading[7];
            for (int m = 0; m < metrics.Length; m++)
                metrics[m] = new Reading(reader.ReadDouble(), (ReadingState)reader.ReadUInt32(), (Meaning)reader.ReadUInt32());
            devices.Add(new DeviceSample(kind, identity, pci, metrics));
        }
        var snapshot = new Snapshot(sequence, timestamp, pid, driver, state, devices.ToArray(), (flags & 1) != 0);
        Validate(snapshot);
        return snapshot;
    }

    public static async Task<byte[]> ReadFrameAsync(Stream stream, CancellationToken cancellation)
    {
        byte[] prefix = new byte[12];
        await stream.ReadExactlyAsync(prefix, cancellation);
        uint size = BinaryPrimitives.ReadUInt32LittleEndian(prefix.AsSpan(8));
        if (size < HeaderSize || size > MaxFrameSize || (size - HeaderSize) % DeviceSize != 0)
            throw new InvalidDataException("Frame length rejected before allocation.");
        byte[] frame = new byte[size];
        prefix.CopyTo(frame, 0);
        await stream.ReadExactlyAsync(frame.AsMemory(prefix.Length), cancellation);
        return frame;
    }

    private static void CheckLength(byte[] frame)
    {
        if (frame.Length < HeaderSize || frame.Length > MaxFrameSize || (frame.Length - HeaderSize) % DeviceSize != 0)
            throw new InvalidDataException("Invalid frame size.");
    }

    private static void Validate(Snapshot sample)
    {
        if (sample.Sequence == 0 || sample.SenderPid == 0 || !Enum.IsDefined(sample.Driver) || !Enum.IsDefined(sample.State)
            || sample.Devices.Length > MaxDevices)
            throw new InvalidDataException("Invalid snapshot metadata.");
        if (sample.Devices.Count(d => d.Kind == DeviceKind.Cpu) > 1 || sample.Devices.Count(d => d.Kind != DeviceKind.Cpu) > 16)
            throw new InvalidDataException("Device limit exceeded.");
        var identities = new HashSet<PciIdentity>();
        foreach (var device in sample.Devices)
        {
            if (!Enum.IsDefined(device.Kind) || !Enum.IsDefined(device.Identity) || device.Metrics.Length != 7)
                throw new InvalidDataException("Invalid device metadata.");
            if (device.Kind == DeviceKind.Cpu)
            {
                if (device.Identity != IdentityKind.Cpu || device.Pci != new PciIdentity(0, 0, 0, 0))
                    throw new InvalidDataException("Invalid CPU identity.");
            }
            else
            {
                if (device.Identity == IdentityKind.Cpu) throw new InvalidDataException("Invalid GPU identity.");
                uint vendor = device.Kind switch { DeviceKind.Nvidia => 0x10DEu, DeviceKind.Amd => 0x1002u, _ => 0x8086u };
                if (device.Identity == IdentityKind.UniquePci && (device.Pci.Vendor != vendor || device.Pci.Device > 0xFFFF
                    || device.Pci.Revision > 0xFF || !identities.Add(device.Pci)))
                    throw new InvalidDataException("Invalid or repeated PCI identity.");
            }
            for (int m = 0; m < 7; m++)
            {
                var metric = device.Metrics[m];
                if (!Enum.IsDefined(metric.State) || !Enum.IsDefined(metric.Meaning) || !double.IsFinite(metric.Value))
                    throw new InvalidDataException("Invalid metric metadata.");
                if (metric.State != ReadingState.NumericReported && (metric.Value != 0 || metric.Meaning != Meaning.None))
                    throw new InvalidDataException("Unavailable metric carries a value.");
                double maximum = m switch { 0 => 150, 1 => 2000, 2 => 100, 3 => 100000, 4 => 200, _ => 1125899906842624 };
                if (metric.Value < (m == 0 ? -50 : 0) || metric.Value > maximum || (m >= 3 && metric.Value != Math.Truncate(metric.Value)))
                    throw new InvalidDataException("Metric out of range.");
                if (metric.State == ReadingState.NumericReported)
                {
                    bool meaningOk = m switch
                    {
                        0 => device.Kind == DeviceKind.Cpu ? metric.Meaning is Meaning.CpuPackage or Meaning.CpuControlTemperature : metric.Meaning == Meaning.GpuCore,
                        1 => device.Kind == DeviceKind.Cpu ? metric.Meaning == Meaning.CpuPackage : metric.Meaning is Meaning.GpuBoard or Meaning.GpuChip or Meaning.UnknownPower,
                        _ => metric.Meaning == Meaning.None
                    };
                    if (!meaningOk) throw new InvalidDataException("Metric scope mismatch.");
                }
                if (device.Identity == IdentityKind.Unknown && metric.State == ReadingState.NumericReported)
                    throw new InvalidDataException("An ambiguous GPU cannot supply numeric metrics.");
            }
            if (device.Metrics[5].State == ReadingState.NumericReported && device.Metrics[6].State == ReadingState.NumericReported
                && device.Metrics[5].Value > device.Metrics[6].Value)
                throw new InvalidDataException("Video memory used exceeds total.");
        }
    }
}
