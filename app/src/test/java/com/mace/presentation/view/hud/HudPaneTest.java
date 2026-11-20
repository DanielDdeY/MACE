package com.mace.presentation.view.hud;

import com.mace.domain.model.MonitoringSnapshot;
import com.mace.presentation.viewmodel.MainDashboardViewModel;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.CheckBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HudPaneTest {
    @Test void hudMuestraNdParaGpuAmbiguaYSoloPotenciaParaUnknownPower() throws Exception {
        MainDashboardViewModel dashboard=mock(MainDashboardViewModel.class);
        var latest=new ReadOnlyObjectWrapper<MonitoringSnapshot>();
        when(dashboard.latestSnapshotProperty()).thenReturn(latest.getReadOnlyProperty());
        onFx(()->{
            HudPane pane=new HudPane(dashboard,new MemoryPreferences());
            try {
                pane.lookupAll(".check-box").forEach(node->((CheckBox)node).setSelected(true));
                var adapter=com.mace.domain.DomainFixtures.gpuAdapter(123,0,"AMD");
                var cpu=com.mace.domain.DomainFixtures.cpu(60);
                var missing=com.mace.domain.model.GpuReading.unavailable(adapter,com.mace.domain.model.MetricState.UNSUPPORTED,0);
                var raw=com.mace.domain.DomainFixtures.snapshot(cpu,missing);
                latest.set(hudSnapshot(1,raw));
                var lines=hudLines(pane);
                assertTrue(lines.contains("GPU temp  N/D"),lines.toString());
                assertTrue(lines.contains("GPU potencia  N/D"),lines.toString());
                assertTrue(lines.contains("GPU uso  N/D"),lines.toString());
                assertTrue(lines.contains("GPU VRAM  N/D"),lines.toString());
                var good=com.mace.domain.DomainFixtures.gpu(adapter,44);
                var mixed=new com.mace.domain.model.GpuReading(adapter,com.mace.domain.model.SensorProvider.LHM,
                    com.mace.domain.model.PowerScope.UNKNOWN,good.temperatureCelsius(),
                    com.mace.domain.model.MetricReading.unavailable(com.mace.domain.model.MetricState.UNSUPPORTED),
                    good.usagePercent(),good.fanRpm(),good.fanPercent(),good.videoMemoryUsedBytes(),
                    good.videoMemoryTotalBytes(),0);
                latest.set(hudSnapshot(2,com.mace.domain.DomainFixtures.snapshot(cpu,mixed)));
                lines=hudLines(pane);
                assertTrue(lines.contains("GPU potencia  N/D"),lines.toString());
                assertTrue(lines.contains("GPU temp  44.0 °C"),lines.toString());
                assertTrue(lines.contains("GPU uso  40.0 %"),lines.toString());
            } finally { pane.close(); }
            return null;
        });
    }
    private static java.util.Set<String> hudLines(HudPane pane) {
        return pane.lookupAll(".hud-line").stream().map(node->((javafx.scene.control.Label)node).getText())
            .collect(java.util.stream.Collectors.toSet());
    }
    private static MonitoringSnapshot hudSnapshot(long sequence,com.mace.domain.model.HardwareSnapshot hardware) {
        return new MonitoringSnapshot(sequence,sequence,hardware,java.util.List.of(),java.util.Optional.empty(),
            java.util.List.of(),java.util.List.of(),Map.of(),com.mace.domain.model.BlackboxStats.disabled());
    }
    @BeforeAll static void startFx() throws Exception {
        try { Platform.startup(() -> {}); }
        catch (IllegalStateException alreadyStarted) { /* El toolkit ya está activo. */ }
        onFx(() -> { Platform.setImplicitExit(false); return null; });
    }

    @Test void abreEnPrimerInicioYPermiteApagarYReabrir() throws Exception {
        Preferences preferences = new MemoryPreferences();
        MainDashboardViewModel dashboard = mock(MainDashboardViewModel.class);
        var latest = new ReadOnlyObjectWrapper<MonitoringSnapshot>();
        when(dashboard.latestSnapshotProperty()).thenReturn(latest.getReadOnlyProperty());
        onFx(() -> {
                HudPane pane = new HudPane(dashboard, preferences);
                try {
                    CheckBox toggle = (CheckBox) pane.lookup(".check-box");
                    assertNotNull(toggle);
                    assertTrue(toggle.isSelected(), "El HUD debe mostrarse en el primer inicio");
                    assertEquals(1, Stage.getWindows().stream().filter(window -> window.isShowing()
                        && window instanceof Stage stage && "MACE Micro-HUD".equals(stage.getTitle())).count());
                    toggle.fire();
                    assertFalse(toggle.isSelected());
                    assertEquals(0, Stage.getWindows().stream().filter(window -> window.isShowing()
                        && window instanceof Stage stage && "MACE Micro-HUD".equals(stage.getTitle())).count());
                    toggle.fire();
                    assertTrue(toggle.isSelected());
                } finally { pane.close(); }
                return null;
        });
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(15, TimeUnit.SECONDS);
    }

    private static final class MemoryPreferences extends AbstractPreferences {
        private final Map<String, String> values = new HashMap<>();
        MemoryPreferences() { super(null, ""); }
        @Override protected void putSpi(String key, String value) { values.put(key, value); }
        @Override protected String getSpi(String key) { return values.get(key); }
        @Override protected void removeSpi(String key) { values.remove(key); }
        @Override protected void removeNodeSpi() { values.clear(); }
        @Override protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
        @Override protected String[] childrenNamesSpi() { return new String[0]; }
        @Override protected AbstractPreferences childSpi(String name) { throw new UnsupportedOperationException(); }
        @Override protected void syncSpi() throws BackingStoreException { }
        @Override protected void flushSpi() throws BackingStoreException { }
    }
}
