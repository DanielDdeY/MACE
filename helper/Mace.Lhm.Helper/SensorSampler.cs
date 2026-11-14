using LibreHardwareMonitor.Hardware;
using LibreHardwareMonitor.Hardware.Gpu;
using Mace.Lhm.Protocol;

namespace Mace.Lhm.Helper;

public sealed class SensorSampler : IDisposable
{
    private readonly Computer computer = new() { IsCpuEnabled = true, IsGpuEnabled = true };
    public void Open() => computer.Open();

    public DeviceSample[] Poll(DriverState driver)
    {
        var installed = PciAssociation.ReadInstalled();
        var devices = computer.Hardware.Where(h => h.HardwareType is HardwareType.Cpu or HardwareType.GpuNvidia
            or HardwareType.GpuAmd or HardwareType.GpuIntel).ToArray();
        if (devices.Length > WireProtocol.MaxDevices) throw new InvalidDataException("Too many hardware devices.");
        return devices.Select(h => Read(h, driver, installed)).ToArray();
    }

    private static DeviceSample Read(IHardware hardware, DriverState driver, PciIdentity[] installed)
    {
        DeviceKind kind = hardware.HardwareType switch
        {
            HardwareType.Cpu => DeviceKind.Cpu, HardwareType.GpuAmd => DeviceKind.Amd,
            HardwareType.GpuNvidia => DeviceKind.Nvidia, _ => DeviceKind.Intel
        };
        var pci = hardware is GenericGpu gpu ? PciAssociation.Parse(gpu.DeviceId) : null;
        var identity = kind == DeviceKind.Cpu ? IdentityKind.Cpu :
            pci is not null && PciAssociation.IsUnique(pci, installed) ? IdentityKind.UniquePci : IdentityKind.Unknown;
        Reading[] missing = Enumerable.Range(0, 7).Select(_ => Reading.Missing()).ToArray();
        var sample = new DeviceSample(kind, identity, pci ?? new PciIdentity(0, 0, 0, 0), missing);
        if (identity == IdentityKind.Unknown) return sample;
        if (kind == DeviceKind.Cpu && driver != DriverState.Running)
            return sample with { Metrics = Enumerable.Range(0, 7).Select(_ => Reading.Missing(ReadingState.NoProvider)).ToArray() };
        try
        {
            hardware.Update();
            bool cpu = kind == DeviceKind.Cpu;
            var sensors = hardware.Sensors;
            missing[0] = cpu
                ? Select(sensors, SensorType.Temperature, Meaning.CpuPackage, "CPU Package")
                : Select(sensors, SensorType.Temperature, Meaning.GpuCore, "GPU Core");
            if (cpu && missing[0].State == ReadingState.Unsupported)
                missing[0] = Select(sensors, SensorType.Temperature, Meaning.CpuControlTemperature, "Core (Tctl/Tdie)");
            missing[1] = cpu
                ? Select(sensors, SensorType.Power, Meaning.CpuPackage, "CPU Package", "Package")
                : Select(sensors, SensorType.Power, Meaning.UnknownPower, "GPU Package", "GPU Power");
            if (!cpu && pci is not null) missing[1]=AmdPowerPolicy.Apply(hardware,pci,missing[1]);
            if (!cpu)
            {
                missing[2] = Select(sensors, SensorType.Load, Meaning.None, "GPU Core");
                missing[3] = Select(sensors, SensorType.Fan, Meaning.None, "GPU Fan");
                missing[4] = Select(sensors, SensorType.Control, Meaning.None, "GPU Fan");
                missing[5] = Select(sensors, SensorType.SmallData, Meaning.None, "GPU Memory Used");
                missing[6] = Select(sensors, SensorType.SmallData, Meaning.None, "GPU Memory Total");
            }
            return sample;
        }
        catch (Exception error) when (error is not OutOfMemoryException)
        {
            return sample with { Metrics = Enumerable.Range(0, 7).Select(_ => Reading.Missing(ReadingState.ReadFailed)).ToArray() };
        }
    }

    private static Reading Select(ISensor[] sensors, SensorType type, Meaning meaning, params string[] names)
    {
        foreach (string name in names)
        {
            var matches = sensors.Where(s => s.SensorType == type && s.Name == name).ToArray();
            if (matches.Length > 1) return Reading.Missing();
            if (matches.Length == 0) continue;
            float? raw = matches[0].Value;
            if (!raw.HasValue) return Reading.Missing(ReadingState.WarmingUp);
            if (!float.IsFinite(raw.Value)) return Reading.Missing(ReadingState.ReadFailed);
            double value = type == SensorType.SmallData ? raw.Value * 1048576.0 : raw.Value;
            if (type is SensorType.Fan or SensorType.Control or SensorType.SmallData) value = Math.Round(value);
            return new Reading(value, ReadingState.NumericReported, meaning);
        }
        return Reading.Missing();
    }

    public void Dispose() => computer.Close();
}
