package com.mace.presentation.viewmodel;

/**
 * Modelo de presentación para una fila de la tabla de procesos. Se mantiene separado del
 * record de dominio para que la vista pueda exponer propiedades/formateos propios sin
 * contaminar el núcleo de negocio.
 */
public record ProcessRow(
        long pid,
        String name,
        String fullPath,
        long workingSetBytes,
        long privateBytes
) {

    public double workingSetMb() {
        return workingSetBytes / (1024.0 * 1024.0);
    }

    public double privateMb() {
        return privateBytes / (1024.0 * 1024.0);
    }
}