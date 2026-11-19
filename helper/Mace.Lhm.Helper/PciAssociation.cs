using System.Text.RegularExpressions;
using Microsoft.Win32;
using Mace.Lhm.Protocol;

namespace Mace.Lhm.Helper;

public static partial class PciAssociation
{
    public static PciIdentity? Parse(string? deviceId)
    {
        if (deviceId is null) return null;
        var match = PciPattern().Match(deviceId);
        if (!match.Success) return null;
        return new PciIdentity(Convert.ToUInt32(match.Groups[1].Value, 16), Convert.ToUInt32(match.Groups[2].Value, 16),
            Convert.ToUInt32(match.Groups[3].Value, 16), Convert.ToUInt32(match.Groups[4].Value, 16));
    }

    public static bool IsUnique(PciIdentity pci, IEnumerable<PciIdentity> installed) => installed.Count(id => id == pci) == 1;

    public static PciIdentity[] ReadInstalled()
    {
        using var key = Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Enum\PCI");
        if (key is null) return [];
        var identities = new List<PciIdentity>();
        foreach (string name in key.GetSubKeyNames())
        {
            var pci = Parse(name);
            if (pci is null) continue;
            using var device = key.OpenSubKey(name);
            if (device is null) continue;
            foreach (string instance in device.GetSubKeyNames()) identities.Add(pci);
        }
        return identities.ToArray();
    }

    [GeneratedRegex(@"VEN_([0-9A-F]{4})&DEV_([0-9A-F]{4})&SUBSYS_([0-9A-F]{8})&REV_([0-9A-F]{2})(?![0-9A-F])", RegexOptions.IgnoreCase | RegexOptions.CultureInvariant)]
    private static partial Regex PciPattern();
}
