package com.mace.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Fotografía <strong>heredada</strong> del estado térmico y de consumo: una sola GPU y dos
 * booleanos generales. Se conserva solo como ruta de transición mientras la presentación migra
 * a {@link HardwareSnapshot}; los datos nuevos deben consumirse desde allí.
 *
 * <p>Conversión de compatibilidad ({@link #fromHardwareSnapshot(HardwareSnapshot)}), con
 * pérdida de información deliberada:</p>
 * <ul>
 *   <li>{@code cpuSensorAvailable} es {@code true} solo si la <em>temperatura</em> de CPU es
 *       {@code VALID}. La potencia se copia únicamente si también es {@code VALID}; si no, 0.</li>
 *   <li>{@code cpuUsage} vale el uso si es {@code VALID} y 0 si está calentando o ausente.</li>
 *   <li>La GPU elegida es la primera, en orden DXGI, con temperatura {@code VALID} (misma regla
 *       que {@code CP_PollTelemetry}). Potencia y RPM de esa GPU solo se copian si son válidas.
 *       Si ninguna GPU tiene temperatura válida, {@code gpuAvailable} es {@code false}.</li>
 * </ul>
 *
 * @param cpuTemp temperatura de CPU en grados Celsius (ignorar si cpuSensorAvailable es false)
 * @param cpuWatts consumo de CPU en vatios (0 si no hay lectura válida)
 * @param cpuUsage uso de CPU de todo el sistema, en porcentaje [0..100]
 * @param cpuSensorAvailable indica si la temperatura de CPU proviene de un sensor real
 * @param gpuTemp temperatura de GPU en grados Celsius (ignorar si gpuAvailable es false)
 * @param gpuWatts consumo de GPU en vatios (ignorar si gpuAvailable es false)
 * @param gpuFanRpm velocidad del ventilador de GPU en RPM (ignorar si gpuAvailable es false)
 * @param gpuAvailable indica si el sensor de GPU está disponible en este sondeo
 * @param timestamp instante exacto de la captura
 */
public record TelemetrySnapshot(
        float cpuTemp,
        float cpuWatts,
        float cpuUsage,
        boolean cpuSensorAvailable,
        float gpuTemp,
        float gpuWatts,
        int gpuFanRpm,
        boolean gpuAvailable,
        Instant timestamp
) {

    public TelemetrySnapshot {
        Objects.requireNonNull(timestamp, "timestamp no puede ser null");
    }

    /** Lectura con sensores de CPU disponibles (temperatura y consumo válidos). */
    public TelemetrySnapshot(float cpuTemp, float cpuWatts, float cpuUsage,
                             float gpuTemp, float gpuWatts, int gpuFanRpm,
                             boolean gpuAvailable, Instant timestamp) {
        this(cpuTemp, cpuWatts, cpuUsage, true, gpuTemp, gpuWatts, gpuFanRpm, gpuAvailable, timestamp);
    }

    /** Adaptador de compatibilidad V2 → heredado; ver reglas en la documentación de la clase. */
    public static TelemetrySnapshot fromHardwareSnapshot(HardwareSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot no puede ser null");
        CpuReading cpu = snapshot.cpu();
        boolean cpuSensorAvailable = cpu.temperatureCelsius().isValid();
        float cpuTemp = (float) cpu.temperatureCelsius().orElse(0);
        float cpuWatts = (float) cpu.powerWatts().orElse(0);
        float cpuUsage = (float) cpu.usagePercent().orElse(0);

        GpuReading gpu = snapshot.gpus().stream()
                .filter(reading -> reading.temperatureCelsius().isValid())
                .findFirst()
                .orElse(null);
        if (gpu == null) {
            return new TelemetrySnapshot(cpuTemp, cpuWatts, cpuUsage, cpuSensorAvailable,
                    0f, 0f, 0, false, snapshot.timestamp());
        }
        long rpm = gpu.fanRpm().orElse(0);
        return new TelemetrySnapshot(cpuTemp, cpuWatts, cpuUsage, cpuSensorAvailable,
                (float) gpu.temperatureCelsius().value(),
                (float) gpu.powerWatts().orElse(0),
                (int) Math.min(rpm, Integer.MAX_VALUE),
                true,
                snapshot.timestamp());
    }
}
