package com.mace.presentation.viewmodel;

import com.mace.domain.model.ImagePath;
import com.mace.domain.model.MemoryMetrics;
import com.mace.domain.model.ProcessIdentity;

import java.util.Objects;

/**
 * Modelo de presentación para una fila de la tabla de procesos. Se mantiene separado del
 * record de dominio para que la vista pueda exponer propiedades/formateos propios sin
 * contaminar el núcleo de negocio.
 */
public record ProcessRow(ProcessIdentity identity, String name, ImagePath imagePath, MemoryMetrics memory) {

    public ProcessRow {
        Objects.requireNonNull(identity, "identity no puede ser null");
        Objects.requireNonNull(name, "name no puede ser null");
        Objects.requireNonNull(imagePath, "imagePath no puede ser null");
        Objects.requireNonNull(memory, "memory no puede ser null");
    }

    public long pid() {
        return identity.pid().value();
    }

    public String fullPath() {
        return imagePath.asOptional().orElse("N/D: " + imagePath.status().label());
    }

    public boolean hasWorkingSet() {
        return memory.workingSetReading().isValid();
    }

    public double workingSetMb() {
        return hasWorkingSet() ? memory.workingSetReading().value() / (1024.0 * 1024.0) : Double.NaN;
    }

    public double privateMb() {
        return memory.privateBytesReading().isValid()
                ? memory.privateBytesReading().value() / (1024.0 * 1024.0) : Double.NaN;
    }
}
