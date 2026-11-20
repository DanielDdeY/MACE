> El documento anterior íntegro se conserva en [el histórico](../../docs/historico/native/pre1D-native__docs__HANDOFF.md). Corresponde al respaldo `1b62000`.

# Entrega nativa y Java

Generar la DLL desde la revisión actual y registrar su SHA-256; el paquete nativo
de desarrollo se crea con scripts/package-native.ps1 desde un árbol native limpio.
Java FFM usa ABI 1.4, los registros V2 y LHM=8. No usar una DLL antigua solo porque
su ABI sea compatible. packaging verifica su hash y rechaza cargadores retirados.

La aplicación instalada necesita mace_native.dll y lhm/Mace.Lhm.Helper.exe con
todas sus dependencias. La imagen también incluye prerequisites/PawnIO_setup.exe
intacto y THIRD-PARTY-NOTICES.txt con textos de licencia. La copia del instalador
no instala el controlador; instalación/reinicio/desinstalación compartida quedan
para su fase autorizada posterior. La DLL hereda el token del anfitrión.

Para desarrollo, MACE_NATIVE_LIB/MACE_NATIVE_SHA256 fijan la DLL y
MACE_LHM_HELPER/MACE_LHM_HELPER_SHA256 fijan el helper. Consultar
../../helper/RESULTADOS-1C.md para comandos elevados y limitaciones físicas.
