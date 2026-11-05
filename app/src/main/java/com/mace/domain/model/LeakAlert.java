package com.mace.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Posible fuga de memoria: la serie crece de forma sostenida según la estrategia de regresión.
 * Es solo un aviso; MACE nunca cierra ni suspende el proceso automáticamente.
 *
 * @param identity            proceso (PID + fecha de creación)
 * @param processName         nombre del ejecutable
 * @param series              serie analizada (Working Set o Private Bytes)
 * @param slopeBytesPerSecond pendiente estimada
 * @param rSquared            bondad del ajuste lineal [0, 1]
 * @param sampleCount         muestras válidas usadas
 * @param window              intervalo cubierto por las muestras
 * @param detectedAt          instante de la evaluación
 */
public record LeakAlert(ProcessIdentity identity, String processName, MemorySeries series,
                        double slopeBytesPerSecond, double rSquared, int sampleCount,
                        Duration window, Instant detectedAt) {

    public LeakAlert {
        Objects.requireNonNull(identity, "identity no puede ser null");
        Objects.requireNonNull(series, "series no puede ser null");
        Objects.requireNonNull(window, "window no puede ser null");
        Objects.requireNonNull(detectedAt, "detectedAt no puede ser null");
        processName = processName == null || processName.isBlank() ? "PID " + identity.pid().value() : processName;
    }
}
