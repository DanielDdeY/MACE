using Mace.Lhm.Helper;
using Mace.Lhm.Protocol;

internal static class RecoveryTests
{
    public static void Run(Action<bool, string> check)
    {
        ulong now = 0;
        int opens = 0, closes = 0;
        var recovery = new RecoverableSampler(() => new FakeSampler(() => opens++, () => closes++), () => now);
        recovery.Open(DriverState.Missing);
        check(opens == 1, "missing driver: initial open once");
        check(!recovery.NeedsRecovery(DriverState.Missing), "missing is not a retry loop");
        check(!recovery.NeedsRecovery(DriverState.Unknown), "unknown preserves down observation without reopen");
        check(recovery.NeedsRecovery(DriverState.Running), "missing to running requests recovery");
        recovery.Reopen();
        check(opens == 2 && closes == 1, "old sampler disposed before replacement opens");
        check(!recovery.NeedsRecovery(DriverState.Running), "steady running does not reopen");
        check(recovery.Poll(DriverState.Running)[0].Metrics[0].State == ReadingState.NumericReported,
            "simulated CPU numeric after recovery");
        check(!recovery.NeedsRecovery(DriverState.Stopped), "stopped never means reboot pending or reopen now");
        check(recovery.Poll(DriverState.Stopped)[0].Metrics[0].State == ReadingState.NoProvider,
            "simulated stopped CPU remains unavailable");
        now = 1000;
        check(!recovery.NeedsRecovery(DriverState.Running), "second edge coalesced during cooldown");
        check(recovery.Poll(DriverState.Running)[0].Metrics[0].State == ReadingState.WarmingUp,
            "pending recovery cannot expose CPU values from an old sampler");
        now = 29999;
        check(!recovery.NeedsRecovery(DriverState.Running), "cooldown not elapsed");
        // A simulated wall-clock jump/sleep does not advance the injected active clock.
        check(!recovery.NeedsRecovery(DriverState.Running), "suspension does not consume cooldown");
        now = 30000;
        check(recovery.NeedsRecovery(DriverState.Running), "pending edge recovers at 30 s awake");
        recovery.Reopen();
        check(opens == 3 && closes == 2, "one recovery for coalesced transitions");
        for (int i = 0; i < 20; i++) recovery.NeedsRecovery(DriverState.Running);
        check(opens == 3, "no reopen loop from repeated running observations");
        recovery.Dispose(); recovery.Dispose();
        check(closes == 3, "final sampler disposed exactly once");
        using var initiallyRunning = new RecoverableSampler(() => new FakeSampler(() => { }, () => { }), () => now);
        initiallyRunning.Open(DriverState.Running);
        check(!initiallyRunning.NeedsRecovery(DriverState.Running), "initial running does not recreate sampler");
        check(!initiallyRunning.NeedsRecovery(DriverState.Unknown) && !initiallyRunning.NeedsRecovery(DriverState.Running),
            "unknown alone does not invent a stopped transition");
    }

    private sealed class FakeSampler(Action opened, Action closed) : ISensorSampler
    {
        public void Open() => opened();
        public void Dispose() => closed();
        public DeviceSample[] Poll(DriverState driver) => [new(DeviceKind.Cpu, IdentityKind.Cpu, new(0,0,0,0),
            [driver == DriverState.Running ? new(55, ReadingState.NumericReported, Meaning.CpuPackage)
                : Reading.Missing(ReadingState.NoProvider)])];
    }
}
