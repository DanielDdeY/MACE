package com.mace.presentation.viewmodel;

/**
 * Fila de datos para la tabla de procesos.
 *
 * NOTA DE DESACOPLAMIENTO (Dev 4):
 * Este record es TEMPORAL. Representa localmente lo mismo que, mas adelante,
 * expondra el puerto de dominio (ProcessEntry) de Dev 2. Cuando esa clase
 * este disponible en 'domain.model', solo hay que:
 *   1) Reemplazar el origen de datos mock en MainDashboardViewModel por el
 *      caso de uso real (a traves del puerto correspondiente).
 *   2) Mapear ProcessEntry -> ProcessRow (o usar ProcessEntry directamente
 *      si sus campos ya calzan con lo que necesita la tabla).
 * Ningun archivo de 'view' ni de 'viewmodel' deberia cambiar por ese motivo,
 * solo el punto de carga de datos.
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