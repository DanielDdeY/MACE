package com.mace.presentation.viewmodel;

import com.mace.domain.model.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Presentation-only grace for one isolated WARMING_UP poll at 1 Hz.
 * Raw snapshots used by alerts, persistence and the native bridge stay untouched.
 */
final class TelemetryPresentation {
    static final long MAX_HOLD_NANOS=Duration.ofMillis(1500).toNanos();
    private record Source(Object device, SensorProvider provider) {}
    private record Key(Source source,int metric) {}
    private final Map<Key,Hold<MetricReading>> decimals=new HashMap<>();
    private final Map<Key,Hold<LongMetricReading>> integers=new HashMap<>();
    private final Map<Source,PowerScope> scopes=new HashMap<>();
    private final LongSupplier nanoTime;

    TelemetryPresentation(LongSupplier nanoTime) { this.nanoTime=nanoTime; }

    HardwareSnapshot apply(HardwareSnapshot raw) {
        long now=nanoTime.getAsLong();
        Set<Source> active=new HashSet<>();
        CpuReading cpu=raw.cpu();
        Source c=new Source(cpu.vendor(),cpu.provider()); active.add(c);
        var temp=decimal(c,0,cpu.temperatureCelsius(),now);
        var power=decimal(c,1,cpu.powerWatts(),now);
        var usage=decimal(c,2,cpu.usagePercent(),now);
        CpuReading shown=new CpuReading(cpu.vendor(),cpu.provider(),
            scope(c,cpu.powerScope(),cpu.powerWatts(),power),temp,power,usage,cpu.monotonicNanos());
        List<GpuReading> gpus=new ArrayList<>();
        for (GpuReading gpu:raw.gpus()) {
            Source g=new Source(gpu.adapter(),gpu.provider()); active.add(g);
            var gpuTemp=decimal(g,0,gpu.temperatureCelsius(),now);
            var gpuPower=decimal(g,1,gpu.powerWatts(),now);
            gpus.add(new GpuReading(gpu.adapter(),gpu.provider(),
                scope(g,gpu.powerScope(),gpu.powerWatts(),gpuPower),gpuTemp,gpuPower,
                decimal(g,2,gpu.usagePercent(),now),integer(g,3,gpu.fanRpm(),now),
                integer(g,4,gpu.fanPercent(),now),integer(g,5,gpu.videoMemoryUsedBytes(),now),
                integer(g,6,gpu.videoMemoryTotalBytes(),now),gpu.monotonicNanos()));
        }
        decimals.keySet().removeIf(key->!active.contains(key.source()));
        integers.keySet().removeIf(key->!active.contains(key.source()));
        scopes.keySet().retainAll(active);
        return new HardwareSnapshot(raw.timestamp(),shown,gpus,raw.memory());
    }
    private MetricReading decimal(Source source,int index,MetricReading value,long now) {
        return decimals.computeIfAbsent(new Key(source,index),key->new Hold<>())
            .apply(value,MetricReading::state,now);
    }
    private LongMetricReading integer(Source source,int index,LongMetricReading value,long now) {
        return integers.computeIfAbsent(new Key(source,index),key->new Hold<>())
            .apply(value,LongMetricReading::state,now);
    }
    private PowerScope scope(Source source,PowerScope current,MetricReading raw,MetricReading shown) {
        if (raw.isValid()) scopes.put(source,current);
        return shown.isValid() && !raw.isValid() ? scopes.getOrDefault(source,PowerScope.UNKNOWN) : current;
    }
    private static final class Hold<T> {
        private T valid;
        private long acquired;
        private boolean consumed;
        T apply(T current,Function<T,MetricState> state,long now) {
            if (state.apply(current)==MetricState.VALID) {
                valid=current; acquired=now; consumed=false; return current;
            }
            if (state.apply(current)==MetricState.WARMING_UP && valid!=null && !consumed &&
                now-acquired>=0 && now-acquired<=MAX_HOLD_NANOS) {
                consumed=true; return valid;
            }
            valid=null; return current;
        }
    }
}
