> El documento anterior íntegro se conserva en [el histórico](../../docs/historico/native/pre1D-native__docs__TEAM_DIAGNOSTIC_GUIDE.md). Corresponde al respaldo `1b62000`.

# Comprobación manual para el grupo

1. Recibir la app-image nueva y verificar sus hashes; no sustituir archivos por
   copias de compilaciones anteriores.
2. Mantener la aplicación, DLL, helper publicado y sus dependencias juntos.
3. El paquete incluye PawnIO oficial intacto en prerequisites; no asumir que ya
   está instalado por ver su EXE. Esta fase no implementa su instalación automática.
4. Ejecutar MACE con el token apropiado. CPU temperatura/potencia requieren
   elevación y PawnIO Running; su ausencia debe mostrar N/D. Uso CPU y RAM continúan.
5. Registrar estados, unidades, ámbito, modelos/PCI, revisión MACE y hashes.
6. NVIDIA e Intel deben conservar su proveedor principal. RPM no se deriva de %.
7. Para diagnóstico del desarrollador, usar Test_MACE_new.exe con la DLL actual y
   el helper fijado mediante MACE_LHM_HELPER; ese EXE no forma parte del producto.

NO VERIFICADO: CPU Intel, NVIDIA principal/respaldo, GPU Intel, HVCI y ausencia
real de PawnIO en un equipo limpio. No cambiar drivers del equipo de trabajo.
