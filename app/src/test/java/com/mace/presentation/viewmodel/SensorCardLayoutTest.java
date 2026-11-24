package com.mace.presentation.viewmodel;

import atlantafx.base.theme.PrimerDark;
import com.mace.domain.model.*;
import com.mace.presentation.view.component.MetricCard;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SensorCardLayoutTest {
    @Test void stateChangesKeepCardHeightAndRebootExplanationFits() throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException active) { }
        var complete=new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try {
                Platform.setImplicitExit(false);
                javafx.application.Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
                var reading=new ReadOnlyObjectWrapper<>(MetricDisplay.decimal(MetricReading.valid(55),"°C",1));
                try (var card=new MetricCard("Temperatura CPU",reading)) {
                    var root=new StackPane(card); var scene=new Scene(root,250,400);
                    scene.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
                    root.applyCss(); card.resize(210,card.prefHeight(210)); card.layout();
                    double height=card.prefHeight(210);
                    for (MetricState state:MetricState.values()) {
                        if (state.isValid()) continue;
                        reading.set(MetricDisplay.decimal(MetricReading.unavailable(state),"°C",1));
                        root.applyCss(); card.layout();
                        assertEquals(height,card.prefHeight(210),0.1,"State must not reflow the card: "+state);
                        var explanation=(Label)card.getChildren().getLast();
                        assertEquals(MetricMessages.message(state),explanation.getText());
                        var measured=new javafx.scene.text.Text(explanation.getText());
                        measured.setFont(explanation.getFont()); measured.setWrappingWidth(176);
                        assertTrue(measured.getLayoutBounds().getHeight()<=92,"Full explanation fits reserved region: "+state);
                    }
                }
                complete.complete(null);
            } catch (Throwable error) { complete.completeExceptionally(error); }
        });
        complete.get(15,TimeUnit.SECONDS);
    }
}
