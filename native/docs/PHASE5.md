> El documento anterior íntegro se conserva en [el histórico](../../docs/historico/native/pre1D-native__docs__PHASE5.md). Corresponde al respaldo `1b62000`.

# Ciclo de vida y concurrencia vigentes

MaceRuntime coordina inicialización y cierre. La inicialización inicia trabajadores
sin esperar sensores; BUSY es transitorio si un cierre retiene el bloqueo de ciclo
de vida. El cierre impide nuevos sondeos antes de unir trabajadores y reiniciar
contadores. Los sondeos consultan cachés y mantienen el uso Windows diferencial.

Después de Shutdown, los sondeos V2 devuelven NOT_INITIALIZED. Un nuevo ciclo
comienza con WARMING_UP. La DLL se mantiene cargada hasta terminar la JVM;
NativeFunctions cierra su runtime una sola vez cuando adquirió la inicialización.
Los tokens de caja negra se cierran antes de los adaptadores nativos.

lifecycle, concurrency y buffer_concurrency siguen vigentes. La política de un
límite total de reinicios del helper sigue pendiente de la Fase 2; no se afirma
haber completado esa fase en esta limpieza.
