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
    @BeforeAll static void startFx() throws Exception {
        try { Platform.startup(() -> {}); }
        catch (IllegalStateException alreadyStarted) { /* El toolkit ya está activo. */ }
        onFx(() -> null);
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
