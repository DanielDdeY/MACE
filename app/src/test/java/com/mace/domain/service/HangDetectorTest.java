package com.mace.domain.service;

import com.mace.domain.model.HangAlert;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ResponsivenessOutcome;
import com.mace.domain.model.ResponsivenessResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.mace.domain.DomainFixtures.MIB;
import static com.mace.domain.DomainFixtures.NOW;
import static com.mace.domain.DomainFixtures.SECOND;
import static com.mace.domain.DomainFixtures.entry;
import static com.mace.domain.DomainFixtures.identity;
import static com.mace.domain.DomainFixtures.inventory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HangDetectorTest {

    private static final ProcessIdentity HUNG = identity(7777, 1);
    private static final ProcessIdentity SERVICE = identity(3100, 1);
    private static final ProcessIdentity EDITOR = identity(9021, 1);

    private final HangDetector detector = new HangDetector(
            new HangDetectionPolicy(Duration.ofSeconds(5), Duration.ofSeconds(30), 64));

    @Test
    void sinVentanaEsDistintoDeColgado() {
        detector.dueForCheck(inventory(0, entry(HUNG, "frozen.exe", MIB, MIB), entry(SERVICE, "svc.exe", MIB, MIB)), 0);
        detector.record(new ResponsivenessResult(HUNG, ResponsivenessOutcome.HUNG, 2, 1, 0), 0);
        detector.record(new ResponsivenessResult(SERVICE, ResponsivenessOutcome.NO_WINDOW, 0, 0, 0), 0);

        List<HangAlert> alerts = detector.activeAlerts(NOW);

        assertEquals(1, alerts.size());
        assertEquals(HUNG, alerts.get(0).identity());
        assertEquals("frozen.exe", alerts.get(0).processName());
        assertEquals(1, alerts.get(0).windowsHung());
        assertEquals(ResponsivenessOutcome.NO_WINDOW, detector.latest().get(SERVICE).outcome());
    }

    @Test
    void losProcesosSinVentanaSeRevisanConMenosFrecuencia() {
        var inv = inventory(0, entry(EDITOR, "Code.exe", MIB, MIB), entry(SERVICE, "svc.exe", MIB, MIB));
        assertEquals(2, detector.dueForCheck(inv, 0).size(), "la primera vez se revisan todos");
        detector.record(ResponsivenessResult.of(EDITOR, ResponsivenessOutcome.RESPONDING), 0);
        detector.record(ResponsivenessResult.of(SERVICE, ResponsivenessOutcome.NO_WINDOW), 0);

        assertTrue(detector.dueForCheck(inv, 4 * SECOND).isEmpty());
        assertEquals(List.of(EDITOR), detector.dueForCheck(inv, 5 * SECOND));
        assertEquals(2, detector.dueForCheck(inv, 30 * SECOND).size());
    }

    @Test
    void unProcesoFinalizadoNoSeConfundeConColgadoYSeOlvida() {
        detector.dueForCheck(inventory(0, entry(HUNG, "frozen.exe", MIB, MIB)), 0);
        detector.record(ResponsivenessResult.of(HUNG, ResponsivenessOutcome.HUNG), 0);
        assertFalse(detector.activeAlerts(NOW).isEmpty());

        detector.record(ResponsivenessResult.of(HUNG, ResponsivenessOutcome.PROCESS_EXITED), SECOND);

        assertTrue(detector.activeAlerts(NOW).isEmpty());
        assertFalse(detector.latest().containsKey(HUNG));
    }

    @Test
    void errorYAccesoDenegadoNoGeneranAlerta() {
        detector.dueForCheck(inventory(0, entry(EDITOR, "a.exe", MIB, MIB), entry(SERVICE, "b.exe", MIB, MIB)), 0);
        detector.record(ResponsivenessResult.of(EDITOR, ResponsivenessOutcome.ERROR), 0);
        detector.record(ResponsivenessResult.of(SERVICE, ResponsivenessOutcome.ACCESS_DENIED), 0);

        assertTrue(detector.activeAlerts(NOW).isEmpty());
    }

    @Test
    void respetaElMaximoDeConsultasPorCiclo() {
        HangDetector limited = new HangDetector(new HangDetectionPolicy(Duration.ZERO, Duration.ZERO, 2));
        var inv = inventory(0, entry(identity(1, 1), "a.exe", MIB, MIB), entry(identity(2, 1), "b.exe", MIB, MIB),
                entry(identity(3, 1), "c.exe", MIB, MIB));

        assertEquals(2, limited.dueForCheck(inv, 0).size());
    }

    @Test
    void olvidaLosProcesosQueSalenDelInventario() {
        detector.dueForCheck(inventory(0, entry(HUNG, "frozen.exe", MIB, MIB)), 0);
        detector.record(ResponsivenessResult.of(HUNG, ResponsivenessOutcome.HUNG), 0);

        detector.dueForCheck(inventory(SECOND, entry(EDITOR, "Code.exe", MIB, MIB)), SECOND);

        assertTrue(detector.activeAlerts(NOW).isEmpty());
    }
}
