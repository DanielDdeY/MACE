package com.mace.presentation.viewmodel;

import com.mace.domain.model.AlertLevel;
import com.mace.domain.model.GpuAdapterId;
import com.mace.domain.model.HangAlert;
import com.mace.domain.model.LeakAlert;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ThermalAlert;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Alerta del dominio adaptada a texto, sin decidir nuevas acciones de proceso. */
public record AlertItemViewModel(Kind kind, String source, String message, Instant timestamp,
                                 Optional<AlertLevel> thermalLevel, Optional<GpuAdapterId> gpuId,
                                 Optional<ProcessIdentity> processIdentity) {

    public enum Kind { THERMAL, MEMORY_LEAK, HUNG_PROCESS }

    public AlertItemViewModel {
        Objects.requireNonNull(kind, "kind no puede ser null");
        Objects.requireNonNull(source, "source no puede ser null");
        Objects.requireNonNull(message, "message no puede ser null");
        Objects.requireNonNull(timestamp, "timestamp no puede ser null");
        Objects.requireNonNull(thermalLevel, "thermalLevel no puede ser null");
        Objects.requireNonNull(gpuId, "gpuId no puede ser null");
        Objects.requireNonNull(processIdentity, "processIdentity no puede ser null");
    }

    public static AlertItemViewModel from(ThermalAlert alert) {
        Objects.requireNonNull(alert, "alert no puede ser null");
        return new AlertItemViewModel(Kind.THERMAL, alert.source(),
                String.format(Locale.ROOT, "%.1f °C", alert.temperatureCelsius()), alert.timestamp(),
                Optional.of(alert.level()), alert.gpu(), Optional.empty());
    }

    public static AlertItemViewModel from(LeakAlert alert) {
        Objects.requireNonNull(alert, "alert no puede ser null");
        double mibPerMinute = alert.slopeBytesPerSecond() * 60.0 / (1024.0 * 1024.0);
        return new AlertItemViewModel(Kind.MEMORY_LEAK, alert.processName(),
                alert.series().label() + " · " + String.format(Locale.ROOT, "%.1f MiB/min", mibPerMinute),
                alert.detectedAt(), Optional.empty(), Optional.empty(), Optional.of(alert.identity()));
    }

    public static AlertItemViewModel from(HangAlert alert) {
        Objects.requireNonNull(alert, "alert no puede ser null");
        return new AlertItemViewModel(Kind.HUNG_PROCESS, alert.processName(),
                alert.windowsHung() + " de " + alert.windowsExamined() + " ventanas sin respuesta",
                alert.detectedAt(), Optional.empty(), Optional.empty(), Optional.of(alert.identity()));
    }
}
