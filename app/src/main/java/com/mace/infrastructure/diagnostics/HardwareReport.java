package com.mace.infrastructure.diagnostics;

import com.mace.domain.model.CpuReading;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.LongMetricReading;
import com.mace.domain.model.MetricReading;
import com.mace.domain.model.ProcessInventory;
import com.mace.infrastructure.InfrastructureAdapters;
import com.mace.infrastructure.nativebridge.NativeLibraryException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Informe de compatibilidad física para registrar, en cada equipo del grupo, qué detecta MACE y el
 * estado de cada métrica (proveedor, ámbito, valor o motivo de ausencia). Solo lee: no inicializa
 * nada más que la telemetría y no toca procesos.
 *
 * <pre>
 * java --enable-native-access=ALL-UNNAMED -cp app\target\mace-app-1.0.0-SNAPSHOT.jar ^
 *      com.mace.infrastructure.diagnostics.HardwareReport [ruta\mace_native.dll] [salida.md]
 * </pre>
 *
 * <p>Sin argumento de DLL se usa {@code MACE_NATIVE_LIB} / {@code -Dmace.native.lib}. Se toman
 * tres muestras separadas un segundo para que las métricas diferenciales dejen de calentar.</p>
 */
public final class HardwareReport {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);
    private static final double GIB = 1024.0 * 1024.0 * 1024.0;

    private HardwareReport() {
    }

    /**
     * Datos del equipo que no vienen de la DLL.
     *
     * @param computer       nombre del equipo
     * @param windows        nombre y versión de Windows
     * @param cpuModel       modelo de CPU según Windows
     * @param gpuDrivers     una línea por GPU con nombre y versión de controlador según Windows
     * @param source         descripción del origen de datos (DLL y ABI)
     * @param dllSha256      SHA-256 de la DLL, o "" si no aplica
     */
    public record SystemInfo(String computer, String windows, String cpuModel, List<String> gpuDrivers,
                             String source, String dllSha256) {
        public SystemInfo {
            gpuDrivers = List.copyOf(Objects.requireNonNull(gpuDrivers, "gpuDrivers no puede ser null"));
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 2) {
            System.err.println("Uso: HardwareReport [mace_native.dll] [salida.md]");
            System.exit(64);
        }
        InfrastructureAdapters adapters;
        try {
            adapters = args.length >= 1 ? InfrastructureAdapters.nativeFrom(Path.of(args[0]))
                    : InfrastructureAdapters.nativeOrThrow();
        } catch (NativeLibraryException e) {
            System.err.println("No se pudo cargar la DLL: " + e.getMessage());
            System.exit(2);
            return;
        }
        try (adapters) {
            HardwareSnapshot snapshot = null;
            for (int i = 0; i < 3; i++) {
                snapshot = adapters.sensors().fetchHardwareSnapshot();
                if (i < 2) {
                    Thread.sleep(1000);
                }
            }
            ProcessInventory inventory = adapters.processes().enumerateProcesses();
            SystemInfo info = new SystemInfo(
                    System.getenv().getOrDefault("COMPUTERNAME", "N/D"),
                    windowsQuery("(Get-CimInstance Win32_OperatingSystem | ForEach-Object { $_.Caption + ' ' + $_.Version })"),
                    windowsQuery("(Get-CimInstance Win32_Processor | Select-Object -First 1).Name"),
                    windowsLines("Get-CimInstance Win32_VideoController | ForEach-Object { $_.Name + ' · controlador ' + $_.DriverVersion }"),
                    adapters.dataSourceDescription(),
                    adapters.nativeLibrarySha256().orElse(""));
            String report = render(info, snapshot, inventory, Instant.now());
            Path output = args.length == 2 ? Path.of(args[1])
                    : Path.of("mace-reporte-" + info.computer().replaceAll("[^A-Za-z0-9_-]", "_") + ".md");
            // Solo la ruta: la consola de Windows puede no mostrar bien el UTF-8 del informe.
            Files.writeString(output, report, StandardCharsets.UTF_8);
            System.out.println("Informe guardado en " + output.toAbsolutePath());
        }
    }

    /** Genera el informe en Markdown (público para pruebas sin DLL). */
    public static String render(SystemInfo info, HardwareSnapshot snapshot, ProcessInventory inventory, Instant now) {
        Objects.requireNonNull(info, "info no puede ser null");
        Objects.requireNonNull(snapshot, "snapshot no puede ser null");
        Objects.requireNonNull(inventory, "inventory no puede ser null");
        StringBuilder out = new StringBuilder(4096);
        out.append("# MACE · Informe de compatibilidad de hardware\n\n");
        out.append("| Dato | Valor |\n|---|---|\n");
        row(out, "Fecha", STAMP.format(now));
        row(out, "Equipo", info.computer());
        row(out, "Windows", info.windows());
        row(out, "CPU (Windows)", info.cpuModel());
        for (String gpu : info.gpuDrivers()) {
            row(out, "GPU (Windows)", gpu);
        }
        row(out, "Origen de datos", info.source());
        row(out, "SHA-256 de la DLL", info.dllSha256().isBlank() ? "N/D" : info.dllSha256());
        row(out, "Java", System.getProperty("java.version", "N/D"));

        CpuReading cpu = snapshot.cpu();
        out.append("\n## CPU\n\n");
        out.append("Fabricante: ").append(cpu.vendor().label()).append(" · Proveedor: ").append(cpu.provider().label())
                .append(" · Ámbito de potencia: ").append(cpu.powerScope().label()).append("\n\n");
        out.append("| Métrica | Estado | Valor |\n|---|---|---|\n");
        metricRow(out, "Temperatura", cpu.temperatureCelsius(), "°C");
        metricRow(out, "Potencia", cpu.powerWatts(), "W");
        metricRow(out, "Uso", cpu.usagePercent(), "%");

        out.append("\n## GPU\n\n");
        if (snapshot.gpus().isEmpty()) {
            out.append("No se detectaron GPU físicas.\n");
        }
        for (GpuReading gpu : snapshot.gpus()) {
            out.append("### ").append(gpu.adapter().displayName()).append("\n\n");
            out.append(String.format(Locale.ROOT, "PCI %04X:%04X · %s · Proveedor: %s · Ámbito: %s%s\n\n",
                    gpu.adapter().vendorId(), gpu.adapter().deviceId(), gpu.adapterId(), gpu.provider().label(),
                    gpu.powerScope().label(), gpu.isInactive() ? " · **inactiva**" : ""));
            out.append("| Métrica | Estado | Valor |\n|---|---|---|\n");
            metricRow(out, "Temperatura", gpu.temperatureCelsius(), "°C");
            metricRow(out, "Potencia", gpu.powerWatts(), "W");
            metricRow(out, "Uso", gpu.usagePercent(), "%");
            longRow(out, "Ventilador (RPM)", gpu.fanRpm(), 1, "RPM");
            longRow(out, "Ventilador (%)", gpu.fanPercent(), 1, "%");
            longRow(out, "VRAM usada", gpu.videoMemoryUsedBytes(), GIB, "GiB");
            longRow(out, "VRAM total", gpu.videoMemoryTotalBytes(), GIB, "GiB");
            out.append('\n');
        }

        out.append("\n## Memoria y procesos\n\n| Dato | Estado | Valor |\n|---|---|---|\n");
        longRow(out, "RAM total", snapshot.memory().totalBytes(), GIB, "GiB");
        longRow(out, "RAM disponible", snapshot.memory().availableBytes(), GIB, "GiB");
        out.append("| Procesos listados | VALID | ").append(inventory.processes().size()).append(" |\n");
        out.append("| Procesos omitidos por permisos | VALID | ").append(inventory.skippedCount())
                .append(inventory.skippedCount() > 0 ? " (ejecutar como administrador para ver más)" : "")
                .append(" |\n");
        out.append("\nUn estado distinto de VALID no es un fallo del puente Java: indica que el sensor o su "
                + "proveedor no está disponible en este equipo.\n");
        return out.toString();
    }

    private static void row(StringBuilder out, String name, String value) {
        out.append("| ").append(name).append(" | ").append(value == null || value.isBlank() ? "N/D" : value).append(" |\n");
    }

    private static void metricRow(StringBuilder out, String name, MetricReading reading, String unit) {
        out.append("| ").append(name).append(" | ").append(reading.state()).append(" (")
                .append(reading.state().label()).append(") | ")
                .append(reading.isValid() ? String.format(Locale.ROOT, "%.1f %s", reading.value(), unit) : "—")
                .append(" |\n");
    }

    private static void longRow(StringBuilder out, String name, LongMetricReading reading, double divisor, String unit) {
        String value = !reading.isValid() ? "—" : divisor == 1
                ? reading.value() + " " + unit
                : String.format(Locale.ROOT, "%.2f %s", reading.value() / divisor, unit);
        out.append("| ").append(name).append(" | ").append(reading.state()).append(" (")
                .append(reading.state().label()).append(") | ").append(value).append(" |\n");
    }

    private static String windowsQuery(String command) {
        List<String> lines = windowsLines(command);
        return lines.isEmpty() ? "N/D" : lines.get(0);
    }

    /** Consulta CIM de Windows (best effort); devuelve vacío si falla o no es Windows. */
    private static List<String> windowsLines(String command) {
        try {
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "[Console]::OutputEncoding = [Text.Encoding]::UTF8; " + command)
                    .redirectErrorStream(true).start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return List.of();
            }
            byte[] output = process.getInputStream().readAllBytes();
            if (process.exitValue() != 0) {
                return List.of();
            }
            return new String(output, StandardCharsets.UTF_8).lines().map(String::trim)
                    .filter(line -> !line.isEmpty()).toList();
        } catch (IOException e) {
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }
}
