using LibreHardwareMonitor.Hardware;
using Mace.Lhm.Protocol;
using System.Globalization;

namespace Mace.Lhm.Helper;

// Evidence and limits: helper/RESULTADOS-1C.md. Never classify by label alone.
public static class AmdPowerPolicy
{
    public static Meaning Classify(PciIdentity pci, Version? version, string name, int index, string report)
    {
        if (pci.Vendor!=0x1002 || pci.Device!=0x7340 || version!=new Version(0,9,6,0)
            || name!="GPU Package" || index!=3) return Meaning.UnknownPower;
        string[] lines=report.Split('\n').Select(s=>s.Trim()).ToArray();
        int start=Array.IndexOf(lines,"Overdrive Caps");
        int end=Array.IndexOf(lines,"Overdrive5 Parameters");
        if (start<0 || end<=start) return Meaning.UnknownPower;
        var caps=lines[start..end];
        return caps.Contains("Status: ADL_OK") && caps.Contains("Version: 8")
            && lines.Contains("Sensor[ADL_PMLOG_ASIC_POWER].Supported: True")
            && lines.Contains("Sensor[ADL_PMLOG_BOARD_POWER].Supported: False")
            ? Meaning.GpuChip : Meaning.UnknownPower;
    }

    public static Reading Resolve(PciIdentity pci,Version? version,string name,int index,Reading original,string report)
    {
        if (original.State!=ReadingState.NumericReported || Classify(pci,version,name,index,report)!=Meaning.GpuChip)
            return original;
        // Read the explicitly identified ASIC value from LHM's public report.
        // GPU Package can also contain an OD6/board fallback; do not relabel that value.
        const string prefix="Sensor[ADL_PMLOG_ASIC_POWER].Value:";
        var matches=report.Split('\n').Select(s=>s.Trim()).Where(s=>s.StartsWith(prefix,StringComparison.Ordinal)).ToArray();
        if (matches.Length!=1 || !double.TryParse(matches[0][prefix.Length..],NumberStyles.Float,CultureInfo.InvariantCulture,out double value)
            || !double.IsFinite(value) || value<0 || value>2000) return original;
        return new Reading(value,ReadingState.NumericReported,Meaning.GpuChip);
    }

    public static Reading Apply(IHardware hardware, PciIdentity pci, Reading reading)
    {
        if (reading.State!=ReadingState.NumericReported || hardware.HardwareType!=HardwareType.GpuAmd
            || pci.Vendor!=0x1002 || pci.Device!=0x7340) return reading;
        var candidates=hardware.Sensors.Where(s=>s.SensorType==SensorType.Power && s.Name=="GPU Package").ToArray();
        if (candidates.Length!=1 || candidates[0].Index!=3) return reading;
        try
        {
            return Resolve(pci,typeof(Computer).Assembly.GetName().Version,
                candidates[0].Name,candidates[0].Index,reading,hardware.GetReport());
        }
        catch (Exception e) when (e is not OutOfMemoryException) { return reading; }
    }
}
