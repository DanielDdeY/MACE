package com.mace.domain.service;

import com.mace.domain.model.ProcessId;
import com.mace.domain.model.ProcessIdentity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemProcessGuardTest {

    private final SystemProcessGuard guard = new SystemProcessGuard(4242);

    @Test
    void protegeLosPidReservadosDelKernel() {
        assertTrue(guard.isSystemProcess(new ProcessId(0)));
        assertTrue(guard.isSystemProcess(new ProcessId(4)));
        assertFalse(guard.isSystemProcess(new ProcessId(8)));
    }

    @Test
    void protegeElPropioProcesoDeMace() {
        assertTrue(guard.isProtected(ProcessIdentity.of(4242, 99)));
        assertTrue(guard.isProtected(ProcessIdentity.of(4, 99)));
        assertFalse(guard.isProtected(ProcessIdentity.of(5000, 99)));
    }

    @Test
    void porDefectoUsaElPidDeLaJvm() {
        SystemProcessGuard real = new SystemProcessGuard();
        assertTrue(real.isOwnProcess(new ProcessId(ProcessHandle.current().pid())));
    }
}
