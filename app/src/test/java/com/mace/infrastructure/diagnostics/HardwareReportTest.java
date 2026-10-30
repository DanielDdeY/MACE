package com.mace.infrastructure.diagnostics;

import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.ProcessInventory;
import com.mace.infrastructure.mock.MockProcessAdapter;
import com.mace.infrastructure.mock.MockScenario;
import com.mace.infrastructure.mock.MockSensorsAdapter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareReportTest {

    private static final Instant NOW = Instant.parse("2026-10-21T17:00:00Z");
    private static final HardwareReport.SystemInfo INFO = new HardwareReport.SystemInfo("PC-PRUEBA",
            "Windows 11 Pro 10.0.26100", "Intel(R) Core(TM) Ultra 7 265",
            List.of("Intel(R) Graphics · controlador 32.0.101.8860"), "Datos reales · DLL 1.4.2 (ABI 1.4)", "ABC123");

    private static String render(MockScenario scenario) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        HardwareSnapshot snapshot = new MockSensorsAdapter(scenario, 1, clock, () -> 1L).fetchHardwareSnapshot();
        ProcessInventory inventory = new MockProcessAdapter(1, clock, () -> 1L).enumerateProcesses();
        return HardwareReport.render(INFO, snapshot, inventory, NOW);
    }

    @Test
    void registraEquipoProveedorYEstadoDeCadaMetrica() {
        String report = render(MockScenario.DUAL_GPU);

        assertTrue(report.contains("| Equipo | PC-PRUEBA |"));
        assertTrue(report.contains("| SHA-256 de la DLL | ABC123 |"));
        assertTrue(report.contains("### GPU 0 · Intel(R) Iris(R) Xe Graphics"));
        assertTrue(report.contains("### GPU 1 · NVIDIA GeForce RTX 3060"));
        assertTrue(report.contains("PCI 8086:9A49"));
        assertTrue(report.contains("| Ventilador (RPM) | UNSUPPORTED (No soportado) | — |"), report);
        assertTrue(report.contains("| Procesos omitidos por permisos | VALID | 3 (ejecutar como administrador"));
    }

    @Test
    void unaMetricaSinProveedorMuestraElMotivoYNoUnCero() {
        String report = render(MockScenario.CPU_NO_TEMPERATURE);

        assertTrue(report.contains("| Temperatura | NO_PROVIDER (Sin proveedor) | — |"), report);
    }

    @Test
    void unEquipoSinGpuLoIndica() {
        assertTrue(render(MockScenario.NO_GPU).contains("No se detectaron GPU físicas."));
    }

    @Test
    void unaGpuInactivaSeMarca() {
        assertTrue(render(MockScenario.GPU_INACTIVE).contains("**inactiva**"));
    }
}
