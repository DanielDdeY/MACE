# Guía para probar MACE native en los equipos del grupo

Esta guía usa `test_mace.exe`, compilado a partir de `native/test_main.c`. Es una prueba **de lectura**: detecta CPU y todas las GPU físicas, muestra cada métrica y su estado, consulta RAM y procesos de usuario, y comprueba una muestra y un buffer temporal con el propio proceso de prueba. No finaliza procesos. El informe separa watts de CPU/GPU del consumo total del equipo, que esta DLL no mide.

## Opción A: ejecutar el paquete ya compilado (recomendada)

1. Usar un equipo con **Windows 10 u 11 de 64 bits** y controladores de CPU/GPU instalados. Recibir el archivo `mace-native-1.4.2-win-x64-....zip` generado para el grupo. Para esta opción **no** se necesitan Visual Studio, CMake, Java, el SDK ADLX ni el código de `app/`.
2. En el Explorador de archivos, hacer clic derecho en el ZIP y elegir **Extraer todo**. Elegir una carpeta propia, por ejemplo `Escritorio\MACE-native-prueba`. El ZIP contiene una carpeta `mace-native-1.4.2-win-x64-...`; entrar en ella y luego en `bin`.
3. Comprobar que **`bin\test_mace.exe` y `bin\mace_native.dll` están juntos**. Ejecutar esa pareja; no mezclar la DLL del ZIP con una antigua de `app/lib/` o de otra compilación. Conservar `build-info.json` y `SHA256SUMS.txt` en el paquete para identificar la compilación. Las carpetas `include/`, `lib/`, `docs/` y `examples/` sirven para desarrollo o consulta; no se copian a `bin` para ejecutar la prueba.
4. Abrir PowerShell dentro de `bin`: en la barra de dirección del Explorador escribir `powershell` y pulsar Intro. Ejecutar:

   ```powershell
   .\test_mace.exe
   ```

   La ejecución tarda aproximadamente un segundo más la enumeración de procesos. De forma predeterminada muestra los primeros diez procesos y la cantidad total. Para incluir **todos** los procesos elegibles y guardar un informe:

   ```powershell
   .\test_mace.exe --all-processes | Tee-Object -FilePath .\reporte-mace.txt
   ```

   El informe queda en `bin\reporte-mace.txt`. Antes de publicarlo fuera del grupo, revisarlo: contiene nombres y rutas de aplicaciones del equipo. `--help` muestra las opciones sin sondear hardware.
5. Si se prueba **temperatura de CPU AMD** y sale `acceso denegado`, cerrar esa consola, abrir **PowerShell como administrador** desde Inicio, ir a la carpeta `bin` extraída y repetir la ejecución. Ejemplo, cambiando la ruta por la carpeta real:

   ```powershell
   Set-Location -LiteralPath 'C:\Users\TU_USUARIO\Desktop\MACE-native-prueba\mace-native-1.4.2-win-x64-...\bin'
   .\test_mace.exe
   ```

   La elevación por sí sola no instala sensores. Para esa temperatura AMD hace falta además el **[Ryzen Master Monitoring SDK](https://www.amd.com/en/developer/ryzen-master-monitoring-sdk.html) 3.1.1.5478** instalado con su controlador en el equipo que realiza la prueba. El SDK se obtiene e instala por separado desde AMD; no se debe copiar la carpeta del SDK de otro equipo ni añadirla al ZIP de MACE. En un procesador AMD no compatible puede seguir sin haber lectura.
6. Al compartir resultados, indicar el modelo de CPU, modelos de cada GPU, versión de Windows y controlador gráfico, si se ejecutó como administrador y si el Ryzen Master Monitoring SDK estaba instalado. Adjuntar el informe revisado. Si hay una GPU integrada y otra dedicada, anotar si aparecen como entradas distintas; una GPU apagada por ahorro energético puede indicar `GPU inactiva`.

**Lectura del resultado.** `valida` significa que el proveedor entregó esa medida. `sin proveedor`, `no soportada`, `acceso denegado`, `inicializando` o `GPU inactiva` son resultados informativos, no valores físicos de cero. La primera muestra prepara contadores y la segunda se toma tras un segundo. La GPU puede tener temperatura y uso válidos pero carecer de ventilador propio, especialmente si es integrada. Intel CPU temperatura/potencia dependen del acceso opcional a MSR mediante WinRing0, que **no se distribuye** en este paquete; sin él el uso global de CPU de Windows continúa disponible. La potencia de CPU AMD aún no tiene proveedor. Los sensores Radeon usan ADLX del controlador si éste es compatible; NVIDIA usa NVML e Intel GPU usa IGCL. El inventario DXGI puede identificar una GPU aunque sus sensores no estén disponibles.

**Si no abre o faltan datos.** Si Windows dice que falta `mace_native.dll`, verificar el paso 3. Si la CPU/GPU figura pero sus métricas indican `sin proveedor`, registrar el estado y revisar controlador, SDK AMD requerido o soporte del modelo; no sustituirlo por una DLL antigua. `CON ERRORES` al final indica que falló alguna consulta básica o la prueba de caja negra, y se debe compartir el texto completo. Un proceso puede terminar entre el inventario y la lectura de su ruta: aparecerá `proceso finalizado` o similar en esa fila sin implicar que toda la prueba haya fallado.

## Opción B: compilar desde el código fuente

1. Extraer `mace-native-source-1.4.2-....zip` en una **carpeta independiente**, por ejemplo `Escritorio\MACE-native-fuente`. No copiar `build/`, `dist/`, `.vs/` ni `.vscode/` entre equipos. La estructura extraída debe contener `native\CMakeLists.txt`, `native\test_main.c`, `native\include\` y `native\scripts\`.
2. Instalar **Visual Studio 2022 o 2026** con la carga de trabajo **Desarrollo de escritorio con C++**, herramientas MSVC x64 y Windows SDK. Usar **CMake 3.28 o posterior** (la versión incluida en Visual Studio sirve si cumple ese mínimo) y PowerShell. El script detecta la versión compatible instalada y ejecuta la compilación y las pruebas CTest. No hace falta instalar Java para compilar y ejecutar `test_mace.exe`. **JDK 22+** solo hace falta para el empaquetado verificado desde un clon Git con `package-native.ps1`; ese script exige además que la fuente esté registrada en Git, por lo que no se ejecuta directamente desde el ZIP de fuente.
3. Abrir PowerShell en la carpeta que contiene `native/` y ejecutar:

   ```powershell
   .\native\scripts\build-native.ps1 -Configuration Release
   ```

   El script indica la carpeta de compilación, normalmente `native\build\vs2022-x64\Release` o `native\build\vs2026-x64\Release`. Abrir esa carpeta y ejecutar `test_mace.exe`; `mace_native.dll` ya queda a su lado. No usar un directorio CMake generado para Visual Studio 2022 con el generador 2026, ni al revés. Si aparece el error de herramientas `v143`, elegir el preset `vs2026-x64` con VS 2026 o instalar el conjunto v143 de VS 2022 y configurar en un directorio de compilación nuevo.
4. Si alguien necesita **compilar su propia DLL con sensores Radeon y temperatura Ryzen**, debe instalar por separado los SDK oficiales y pasar sus rutas a la compilación. Estas rutas son ejemplos de la máquina de desarrollo y pueden ser distintas en otro equipo:

   ```powershell
   .\native\scripts\build-native.ps1 -Configuration Release `
     -AdlxSdkDirectory 'C:\Program Files\AMD\ADLX-2.0' `
     -RyzenSdkDirectory 'C:\Program Files\AMD\RyzenMasterMonitoringSDK'
   ```

   Compilar sin esos parámetros es válido, pero no incluirá los proveedores AMD opcionales correspondientes. Un compañero Intel o NVIDIA que solo quiere **probar** debe usar la opción A; no necesita instalar los SDK AMD.
5. En Visual Studio también se puede abrir directamente la carpeta `native/`, seleccionar el preset `vs2022-x64` o `vs2026-x64` según la instalación, compilar en **Release**, escoger **`test_mace.exe`** como elemento de inicio y pulsar **Ctrl+F5**. El archivo fuente se llama `test_main.c`, pero el objetivo ejecutable de CMake es `test_mace`.

La aplicación Java de `app/` todavía usa principalmente la interfaz heredada. Esta prueba usa V2 directamente para verificar el trabajo nativo en hardware distinto antes de la integración Java.
