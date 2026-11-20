package com.mace.infrastructure.nativebridge;

import com.mace.domain.model.MetricState;
import java.time.Duration;
import java.util.function.LongSupplier;

/** Retry driven by the existing polling worker: no sleep, task or JavaFX wait. */
final class TelemetryInitialization {
    static final int MAX_ATTEMPTS = 5;
    static final long RETRY_NANOS = Duration.ofMillis(250).toNanos();
    static final long WINDOW_NANOS = Duration.ofSeconds(5).toNanos();
    static final long COOLDOWN_NANOS = Duration.ofSeconds(5).toNanos();
    private final NativeApi api;
    private final LongSupplier nanoTime;
    private boolean ready, cooling;
    private int attempts;
    private long burstStarted, nextAttempt;
    private MetricState state = MetricState.WARMING_UP;

    TelemetryInitialization(NativeApi api, LongSupplier nanoTime) {
        this.api=api; this.nanoTime=nanoTime;
    }
    boolean ready() {
        if (ready) return true;
        long now=nanoTime.getAsLong();
        if (attempts>0 && now-nextAttempt<0) return false;
        if (cooling) { attempts=0; cooling=false; }
        if (attempts>0 && now-burstStarted>=WINDOW_NANOS) { cooldown(now); return false; }
        if (attempts==0) burstStarted=now;
        attempts++;
        int result=api.ensureTelemetryInitialized();
        if (result==NativeStatus.OK) { ready=true; return true; }
        if (result!=NativeStatus.BUSY)
            throw new NativeLibraryException(NativeFunctions.SYMBOL_INITIALIZE_TELEMETRY+" devolvió el código "+result);
        state=MetricState.WARMING_UP;
        if (attempts>=MAX_ATTEMPTS) cooldown(now);
        else nextAttempt=now+RETRY_NANOS;
        return false;
    }
    private void cooldown(long now) {
        cooling=true; state=MetricState.NOT_INITIALIZED; nextAttempt=now+COOLDOWN_NANOS;
    }
    MetricState state() { return state; }
}
