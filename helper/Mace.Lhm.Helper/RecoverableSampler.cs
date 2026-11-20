using System.Runtime.InteropServices;
using Mace.Lhm.Protocol;

namespace Mace.Lhm.Helper;

public interface ISensorSampler : IDisposable
{
    void Open();
    DeviceSample[] Poll(DriverState driver);
}

/// <summary>One sampler at a time; coalesces driver transitions without restarting the helper.</summary>
public sealed class RecoverableSampler : IDisposable
{
    private readonly Func<ISensorSampler> factory;
    private readonly Func<ulong> awakeMs;
    private ISensorSampler? sampler;
    private bool observedDown, pending;
    private ulong nextRecovery;
    public RecoverableSampler(Func<ISensorSampler>? factory = null, Func<ulong>? awakeMs = null)
    {
        this.factory = factory ?? (() => new SensorSampler());
        this.awakeMs = awakeMs ?? AwakeMilliseconds;
    }
    public void Open(DriverState driver)
    {
        observedDown = driver is DriverState.Missing or DriverState.Stopped;
        Reopen();
    }
    public bool NeedsRecovery(DriverState driver)
    {
        if (driver is DriverState.Missing or DriverState.Stopped) observedDown = true;
        if (driver != DriverState.Running) return false;
        if (observedDown) { pending = true; observedDown = false; }
        ulong now = awakeMs();
        if (!pending || now < nextRecovery) return false;
        pending = false;
        nextRecovery = now + 30000;
        return true;
    }
    public void Reopen()
    {
        var previous = sampler; sampler = null;
        previous?.Dispose(); // Never overlap Computer instances/hardware locks.
        var replacement = factory();
        try { replacement.Open(); sampler = replacement; }
        catch { replacement.Dispose(); throw; }
    }
    public DeviceSample[] Poll(DriverState driver)
    {
        var samples = (sampler ?? throw new InvalidOperationException("Sampler not open")).Poll(driver);
        if (pending && driver == DriverState.Running)
            return samples.Select(s => s.Kind == DeviceKind.Cpu
                ? s with { Metrics = Enumerable.Range(0, 7).Select(_ => Reading.Missing(ReadingState.WarmingUp)).ToArray() }
                : s).ToArray();
        return samples;
    }
    public void Dispose() { var previous = sampler; sampler = null; previous?.Dispose(); }

    private static ulong AwakeMilliseconds()
    {
        if (!QueryUnbiasedInterruptTime(out ulong ticks))
            throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
        return ticks / 10000;
    }
    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool QueryUnbiasedInterruptTime(out ulong ticks);
}
