package com.mace.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Evento de aplicación para la caja negra.
 *
 * @param type      tipo de evento
 * @param timestamp instante de pared
 * @param detail    descripción breve; el sink puede truncarla al tamaño de su registro
 */
public record BlackboxEvent(BlackboxEventType type, Instant timestamp, String detail) {

    public BlackboxEvent {
        Objects.requireNonNull(type, "type no puede ser null");
        Objects.requireNonNull(timestamp, "timestamp no puede ser null");
        detail = detail == null ? "" : detail;
    }

    public static BlackboxEvent thermal(ThermalAlert alert) {
        return new BlackboxEvent(BlackboxEventType.THERMAL_ALERT, alert.timestamp(),
                alert.level() + " " + alert.source() + " " + alert.temperatureCelsius() + "C");
    }

    public static BlackboxEvent leak(LeakAlert alert) {
        return new BlackboxEvent(BlackboxEventType.LEAK_ALERT, alert.detectedAt(),
                alert.processName() + " " + alert.identity() + " " + alert.series()
                        + String.format(Locale.ROOT, " %.0fB/s R2=%.2f", alert.slopeBytesPerSecond(), alert.rSquared()));
    }

    public static BlackboxEvent hang(HangAlert alert) {
        return new BlackboxEvent(BlackboxEventType.HANG_ALERT, alert.detectedAt(),
                alert.processName() + " " + alert.identity() + " hung=" + alert.windowsHung() + "/" + alert.windowsExamined());
    }
}
