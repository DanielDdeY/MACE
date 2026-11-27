> El documento anterior íntegro se conserva en [el histórico](../../docs/historico/native/pre1D-app__lib__README.txt). Corresponde al respaldo `1b62000`.

Dependencias nativas de MACE

No versionar DLL compiladas. Compilar la revisión actual y configurar
MACE_NATIVE_LIB y MACE_NATIVE_SHA256 durante desarrollo. Los sensores requieren
el helper publicado; en la imagen está en app/lhm junto a mace_native.dll.
No copiar una DLL antigua de esta carpeta. Consultar packaging/README.md.

Informe de compatibilidad de cada equipo (pedido por la guia de integracion):

    cd app
    mvn -q -DskipTests package
    java --enable-native-access=ALL-UNNAMED -cp target\mace-app-1.0.0-SNAPSHOT.jar ^
         com.mace.infrastructure.diagnostics.HardwareReport C:\ruta\al\paquete\bin\mace_native.dll

Genera mace-reporte-<EQUIPO>.md con Windows, modelo de CPU/GPU, version de controlador, SHA-256
de la DLL y, para CPU, cada GPU y RAM, el proveedor, el ambito de potencia y el estado/valor de
cada metrica. Ejecutarlo tambien como administrador si se quiere comparar.
