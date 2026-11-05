@echo off
REM ============================================================================
REM  MACE - Empaquetado nativo para Windows x64
REM ----------------------------------------------------------------------------
REM  Uso:
REM      package-windows.bat [app-image | msi | exe] [opciones]
REM
REM  Tipos de salida:
REM      app-image  Carpeta autocontenida packaging\dist\MACE\ (por defecto, no requiere WiX)
REM      msi        Instalador MSI (requiere WiX Toolset 3.x en el PATH: candle.exe / light.exe)
REM      exe        Instalador EXE (requiere WiX Toolset 3.x)
REM
REM  Opciones:
REM      --with-tests    Ejecuta los tests de Maven durante el build (por defecto se omiten)
REM      --console       Launcher con consola visible (util para depurar el puente FFM)
REM      --skip-native   Aceptada por compatibilidad: este script ya no compila la DLL
REM      --help          Muestra esta ayuda
REM
REM  DLL nativa (obligatoria):
REM      Este script NO compila native\. Una recompilacion accidental sin los SDK de ADLX o
REM      Ryzen Master produciria una DLL con menos sensores que la probada. Se empaqueta
REM      exclusivamente bin\mace_native.dll del paquete verificado por Dev 1
REM      (native\scripts\package-native.ps1), comprobando antes su SHA-256 y su ABI.
REM
REM  Variables de entorno:
REM      MACE_NATIVE_LIB     Ruta a bin\mace_native.dll del paquete de Dev 1. Si no se define,
REM                          se usa el paquete mas reciente de native\dist\mace-native-*.
REM      MACE_NATIVE_SHA256  Hash esperado; si no se define se toma de build-info.json o
REM                          SHA256SUMS.txt del mismo paquete.
REM      JAVA_HOME           JDK 22+ a usar (si no, se toma el java del PATH)
REM      JAVAFX_JMODS        Carpeta con los .jmod de JavaFX 22 para que jlink genere un
REM                          runtime minimo con JavaFX modular. Si no se define, JavaFX se
REM                          empaqueta como JARs de classpath (funciona, con una advertencia).
REM
REM  Pasos que ejecuta:
REM      1. Verifica JDK 22+ (FFM API final), jlink, jpackage y Maven.
REM      2. mvn package + copia de dependencias runtime a packaging\output\input.
REM      3. Verifica SHA-256 y ABI de la DLL (verify-native-dll.ps1 + NativeAbiVerifier)
REM         y la copia junto al JAR. El informe queda en packaging\dist\MACE-native-report.txt.
REM      4. jlink: runtime recortado en packaging\runtime.
REM      5. jpackage --type app-image: MACE.exe (modo nativo, DLL fijada por hash) y
REM         MACE-Preview.exe (datos simulados, sin DLL ni administrador).
REM      6. Incrusta packaging\app.manifest (requireAdministrator) SOLO en MACE.exe con mt.exe.
REM      7. Opcional: jpackage --type msi|exe a partir de la app-image ya firmada con el manifiesto.
REM
REM  No incluye ni descarga AMD Ryzen Master SDK, ADLX SDK, WinRing0 ni controladores de terceros.
REM ============================================================================
setlocal EnableExtensions EnableDelayedExpansion

REM ---------------------------------------------------------------------------
REM Rutas y metadatos
REM ---------------------------------------------------------------------------
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "ROOT_DIR=%%~fI"
set "APP_DIR=%ROOT_DIR%\app"
set "NATIVE_DIR=%ROOT_DIR%\native"
set "PACKAGING_DIR=%ROOT_DIR%\packaging"
set "STAGE_DIR=%PACKAGING_DIR%\output\input"
set "RUNTIME_DIR=%PACKAGING_DIR%\runtime"
set "DIST_DIR=%PACKAGING_DIR%\dist"
set "NATIVE_REPORT=%DIST_DIR%\MACE-native-report.txt"

set "APP_NAME=MACE"
set "APP_VERSION=1.0.0"
REM La version puede venir del tag de la Release (GitHub Actions): MACE_APP_VERSION=1.2.0
if defined MACE_APP_VERSION set "APP_VERSION=%MACE_APP_VERSION%"
set "APP_VENDOR=MACE Team"
set "APP_DESCRIPTION=MACE - Telemetria de hardware y gestor de procesos"
set "APP_UPGRADE_UUID=7f3c2a1e-9b4d-4c6e-8a1f-2d5e6b7c8d90"
set "MAIN_CLASS=com.mace.presentation.MaceLauncher"
set "MAIN_JAR=mace-app-1.0.0-SNAPSHOT.jar"

REM Modulos del JDK que necesita la app (JavaFX se agrega si hay JAVAFX_JMODS)
set "JLINK_MODULES=java.base,java.desktop,java.logging,java.xml,java.naming,java.scripting,java.net.http,jdk.unsupported"
set "JAVAFX_MODULES=javafx.base,javafx.graphics,javafx.controls,javafx.fxml"

REM ---------------------------------------------------------------------------
REM Argumentos
REM ---------------------------------------------------------------------------
set "PKG_TYPE=app-image"
set "SKIP_TESTS=1"
set "WIN_CONSOLE=0"

:parse_args
if "%~1"=="" goto :args_done
if /I "%~1"=="app-image"     set "PKG_TYPE=app-image"
if /I "%~1"=="msi"           set "PKG_TYPE=msi"
if /I "%~1"=="exe"           set "PKG_TYPE=exe"
if /I "%~1"=="--skip-native" echo [INFO] --skip-native ya no es necesario: el script no compila la DLL.
if /I "%~1"=="--with-tests"  set "SKIP_TESTS=0"
if /I "%~1"=="--console"     set "WIN_CONSOLE=1"
if /I "%~1"=="--help"        goto :usage
if /I "%~1"=="-h"            goto :usage
if /I "%~1"=="/?"            goto :usage
shift
goto :parse_args
:args_done

echo.
echo ==========================================================
echo   MACE - Empaquetado Windows x64  [tipo: %PKG_TYPE%]
echo ==========================================================
echo   Raiz del proyecto : %ROOT_DIR%
echo.

REM ---------------------------------------------------------------------------
REM 1. Toolchain
REM ---------------------------------------------------------------------------
set "JDK_HOME="
if defined JAVA_HOME (
    set "JDK_HOME=%JAVA_HOME%"
) else (
    for /f "delims=" %%I in ('where java 2^>nul') do (
        if not defined JDK_HOME for %%P in ("%%~dpI..") do set "JDK_HOME=%%~fP"
    )
)
if not defined JDK_HOME (
    echo [ERROR] No se encontro un JDK. Define JAVA_HOME o agrega java al PATH.
    exit /b 1
)
set "JDK_BIN=%JDK_HOME%\bin"

if not exist "%JDK_BIN%\java.exe" (
    echo [ERROR] No existe "%JDK_BIN%\java.exe". Revisa JAVA_HOME.
    exit /b 1
)

REM La salida de "java -version" se vuelca a un archivo temporal y se parsea con
REM findstr. Evita el fallo clasico de 'for /f' cuando JDK_BIN contiene espacios
REM (p. ej. C:\Program Files\...): al ir la ruta entrecomillada como primer token
REM del comando, el parser la partia en el espacio y la version salia vacia.
set "JAVA_VERSION="
set "JAVA_VER_TMP=%TEMP%\mace_javaver_%RANDOM%.txt"
"%JDK_BIN%\java.exe" -version > "%JAVA_VER_TMP%" 2>&1
for /f "tokens=3" %%V in ('findstr /i "version" "%JAVA_VER_TMP%"') do (
    if not defined JAVA_VERSION set "JAVA_VERSION=%%~V"
)
del "%JAVA_VER_TMP%" >nul 2>&1
set "JAVA_MAJOR="
for /f "tokens=1 delims=." %%M in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%M"
if not defined JAVA_MAJOR set "JAVA_MAJOR=0"
if %JAVA_MAJOR% LSS 22 (
    echo [ERROR] Se requiere JDK 22 o superior ^(FFM API final^). Detectado: %JAVA_VERSION%
    echo         JDK en uso: %JDK_HOME%
    exit /b 1
)
echo [OK] JDK %JAVA_VERSION% en %JDK_HOME%

if not exist "%JDK_BIN%\jpackage.exe" (
    echo [ERROR] El JDK no incluye jpackage.exe. Usa una distribucion completa del JDK.
    exit /b 1
)
if not exist "%JDK_BIN%\jlink.exe" (
    echo [ERROR] El JDK no incluye jlink.exe.
    exit /b 1
)

where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven ^(mvn^) no esta en el PATH.
    exit /b 1
)
echo [OK] Maven disponible

if not exist "%APP_DIR%\pom.xml" (
    echo [ERROR] No existe "%APP_DIR%\pom.xml".
    exit /b 1
)

REM ---------------------------------------------------------------------------
REM 2. Build Java + dependencias
REM ---------------------------------------------------------------------------
echo.
echo [2/7] Compilando la aplicacion Java con Maven...
if exist "%STAGE_DIR%" rmdir /s /q "%STAGE_DIR%"
mkdir "%STAGE_DIR%"

set "MVN_TEST_FLAG="
if "%SKIP_TESTS%"=="1" set "MVN_TEST_FLAG=-DskipTests"

set "JAVA_HOME=%JDK_HOME%"
call mvn -B -f "%APP_DIR%\pom.xml" clean package %MVN_TEST_FLAG% dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="%STAGE_DIR%"
if errorlevel 1 (
    echo [ERROR] Fallo la compilacion con Maven.
    exit /b 1
)

if not exist "%APP_DIR%\target\%MAIN_JAR%" (
    set "MAIN_JAR="
    for %%J in ("%APP_DIR%\target\*.jar") do if not defined MAIN_JAR set "MAIN_JAR=%%~nxJ"
)
if not defined MAIN_JAR (
    echo [ERROR] No se genero ningun JAR en "%APP_DIR%\target".
    exit /b 1
)
copy /Y "%APP_DIR%\target\%MAIN_JAR%" "%STAGE_DIR%\" >nul
echo [OK] JAR principal: %MAIN_JAR%

REM ---------------------------------------------------------------------------
REM 3. DLL nativa verificada (hash + ABI) del paquete de Dev 1
REM ---------------------------------------------------------------------------
echo.
echo [3/7] Verificando la biblioteca nativa...
set "NATIVE_DLL_PATH="
if defined MACE_NATIVE_LIB (
    if exist "%MACE_NATIVE_LIB%\" (
        if exist "%MACE_NATIVE_LIB%\mace_native.dll" set "NATIVE_DLL_PATH=%MACE_NATIVE_LIB%\mace_native.dll"
    ) else (
        if exist "%MACE_NATIVE_LIB%" set "NATIVE_DLL_PATH=%MACE_NATIVE_LIB%"
    )
    if not defined NATIVE_DLL_PATH (
        echo [ERROR] MACE_NATIVE_LIB no apunta a una mace_native.dll existente: %MACE_NATIVE_LIB%
        exit /b 1
    )
) else (
    REM Paquete mas reciente de native\dist (los nombres llevan fecha UTC, se ordenan por nombre).
    for /f "delims=" %%P in ('dir /b /ad /o-n "%NATIVE_DIR%\dist\mace-native-*" 2^>nul') do (
        if not defined NATIVE_DLL_PATH if exist "%NATIVE_DIR%\dist\%%P\bin\mace_native.dll" set "NATIVE_DLL_PATH=%NATIVE_DIR%\dist\%%P\bin\mace_native.dll"
    )
)
if not defined NATIVE_DLL_PATH (
    echo [ERROR] No hay DLL verificada. Descomprime el paquete de Dev 1 y define
    echo         MACE_NATIVE_LIB=^<paquete^>\bin\mace_native.dll, o genera native\dist con
    echo         native\scripts\package-native.ps1. Este script no compila la DLL.
    exit /b 1
)

if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
set "NATIVE_SHA256="
set "VERIFY_TMP=%TEMP%\mace_verify_%RANDOM%.txt"
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%PACKAGING_DIR%\verify-native-dll.ps1" ^
    -DllPath "%NATIVE_DLL_PATH%" -AppJar "%APP_DIR%\target\%MAIN_JAR%" -JavaExe "%JDK_BIN%\java.exe" ^
    -ReportPath "%NATIVE_REPORT%" > "%VERIFY_TMP%"
set "VERIFY_EXIT=%ERRORLEVEL%"
if not "%VERIFY_EXIT%"=="0" (
    del "%VERIFY_TMP%" >nul 2>&1
    echo [ERROR] La DLL no supera la verificacion de hash/ABI. No se empaqueta.
    exit /b 1
)
for /f "usebackq delims=" %%H in ("%VERIFY_TMP%") do set "NATIVE_SHA256=%%H"
del "%VERIFY_TMP%" >nul 2>&1
if not defined NATIVE_SHA256 (
    echo [ERROR] No se obtuvo el SHA-256 de la DLL verificada.
    exit /b 1
)
copy /Y "%NATIVE_DLL_PATH%" "%STAGE_DIR%\mace_native.dll" >nul
echo [OK] DLL verificada: %NATIVE_DLL_PATH%
echo [OK] SHA-256: %NATIVE_SHA256%
echo [OK] Informe: %NATIVE_REPORT%

REM ---------------------------------------------------------------------------
REM 4. jlink
REM ---------------------------------------------------------------------------
echo.
echo [4/7] Generando runtime con jlink...
if exist "%RUNTIME_DIR%" rmdir /s /q "%RUNTIME_DIR%"

set "RUNTIME_OPT="
if not exist "%JDK_HOME%\jmods" (
    echo [WARN] El JDK no incluye la carpeta jmods; jpackage usara el runtime por defecto.
    goto :jlink_done
)

set "JLINK_MODULE_PATH=%JDK_HOME%\jmods"
set "JLINK_ADD_MODULES=%JLINK_MODULES%"
if defined JAVAFX_JMODS if exist "%JAVAFX_JMODS%\javafx.base.jmod" (
    set "JLINK_MODULE_PATH=!JLINK_MODULE_PATH!;%JAVAFX_JMODS%"
    set "JLINK_ADD_MODULES=!JLINK_ADD_MODULES!,%JAVAFX_MODULES%"
    REM JavaFX ira dentro del runtime: se quitan los JARs de classpath para evitar duplicados.
    del /q "%STAGE_DIR%\javafx-*.jar" >nul 2>&1
    echo [INFO] JavaFX modular desde %JAVAFX_JMODS%
)

"%JDK_BIN%\jlink.exe" --module-path "!JLINK_MODULE_PATH!" --add-modules !JLINK_ADD_MODULES! --strip-debug --no-header-files --no-man-pages --compress zip-6 --output "%RUNTIME_DIR%"
if errorlevel 1 (
    echo [ERROR] jlink fallo.
    exit /b 1
)
set "RUNTIME_OPT=--runtime-image "%RUNTIME_DIR%""
echo [OK] Runtime en %RUNTIME_DIR%

:jlink_done

REM ---------------------------------------------------------------------------
REM 5. jpackage app-image (launcher real + launcher de vista previa)
REM ---------------------------------------------------------------------------
echo.
echo [5/7] Creando app-image con jpackage...
if exist "%DIST_DIR%\%APP_NAME%" rmdir /s /q "%DIST_DIR%\%APP_NAME%"

set "ICON_OPT="
if exist "%PACKAGING_DIR%\icon.ico" set "ICON_OPT=--icon "%PACKAGING_DIR%\icon.ico""
set "CONSOLE_OPT="
if "%WIN_CONSOLE%"=="1" set "CONSOLE_OPT=--win-console"

REM MACE.exe: modo nativo estricto; la DLL empaquetada queda fijada por su SHA-256.
"%JDK_BIN%\jpackage.exe" --type app-image ^
    --name "%APP_NAME%" ^
    --app-version "%APP_VERSION%" ^
    --vendor "%APP_VENDOR%" ^
    --description "%APP_DESCRIPTION%" ^
    --input "%STAGE_DIR%" ^
    --main-jar "%MAIN_JAR%" ^
    --main-class "%MAIN_CLASS%" ^
    --dest "%DIST_DIR%" ^
    --java-options "--enable-native-access=ALL-UNNAMED" ^
    --java-options "-Dmace.infra.mode=native" ^
    --java-options "-Dmace.native.lib=$APPDIR\mace_native.dll" ^
    --java-options "-Dmace.native.sha256=%NATIVE_SHA256%" ^
    --java-options "-Djava.library.path=$APPDIR" ^
    --add-launcher "%APP_NAME%-Preview=%PACKAGING_DIR%\preview-launcher.properties" ^
    %RUNTIME_OPT% %ICON_OPT% %CONSOLE_OPT%
if errorlevel 1 (
    echo [ERROR] jpackage fallo al crear la app-image.
    exit /b 1
)
set "APP_IMAGE_DIR=%DIST_DIR%\%APP_NAME%"
set "LAUNCHER_EXE=%APP_IMAGE_DIR%\%APP_NAME%.exe"
echo [OK] App-image en %APP_IMAGE_DIR%
echo [OK] Vista previa sin administrador: %APP_IMAGE_DIR%\%APP_NAME%-Preview.exe

REM ---------------------------------------------------------------------------
REM 6. Manifiesto UAC (requireAdministrator) solo en el launcher real
REM ---------------------------------------------------------------------------
echo.
echo [6/7] Incrustando manifiesto UAC en %APP_NAME%.exe...
set "MT_EXE="
for /f "delims=" %%I in ('where mt.exe 2^>nul') do if not defined MT_EXE set "MT_EXE=%%I"
if not defined MT_EXE (
    for /d %%D in ("%ProgramFiles(x86)%\Windows Kits\10\bin\10.*") do (
        if exist "%%~D\x64\mt.exe" set "MT_EXE=%%~D\x64\mt.exe"
    )
)
if not defined MT_EXE (
    for /d %%D in ("%ProgramFiles%\Windows Kits\10\bin\10.*") do (
        if exist "%%~D\x64\mt.exe" set "MT_EXE=%%~D\x64\mt.exe"
    )
)

if defined MT_EXE (
    REM jpackage marca los launchers como solo lectura; mt.exe no puede actualizar
    REM el recurso #1 hasta quitar ese atributo del ejecutable real.
    attrib -R "%LAUNCHER_EXE%"
    if errorlevel 1 (
        echo [ERROR] No se pudo habilitar la escritura de %LAUNCHER_EXE%.
        exit /b 1
    )
    "!MT_EXE!" -nologo -manifest "%PACKAGING_DIR%\app.manifest" -outputresource:"%LAUNCHER_EXE%;#1"
    if errorlevel 1 (
        echo [ERROR] mt.exe no pudo incrustar el manifiesto.
        exit /b 1
    )
    echo [OK] Manifiesto requireAdministrator incrustado con !MT_EXE!
) else (
    echo [ERROR] mt.exe no encontrado. Instala el Windows 10/11 SDK: el launcher
    echo         real requiere el manifiesto UAC para considerarse empaquetado.
    exit /b 1
)

REM ---------------------------------------------------------------------------
REM 7. Instalador opcional (msi / exe) a partir de la app-image
REM ---------------------------------------------------------------------------
echo.
if /I "%PKG_TYPE%"=="app-image" (
    echo [7/7] Instalador omitido ^(tipo app-image^).
    goto :done
)

echo [7/7] Creando instalador %PKG_TYPE% con jpackage...
where candle.exe >nul 2>&1
if errorlevel 1 (
    echo [ERROR] WiX Toolset 3.x ^(candle.exe / light.exe^) no esta en el PATH; es requisito de jpackage para %PKG_TYPE%.
    exit /b 1
)
"%JDK_BIN%\jpackage.exe" --type %PKG_TYPE% ^
    --app-image "%APP_IMAGE_DIR%" ^
    --name "%APP_NAME%" ^
    --app-version "%APP_VERSION%" ^
    --vendor "%APP_VENDOR%" ^
    --description "%APP_DESCRIPTION%" ^
    --dest "%DIST_DIR%" ^
    --win-upgrade-uuid "%APP_UPGRADE_UUID%" ^
    --win-dir-chooser ^
    --win-menu ^
    --win-shortcut ^
    %ICON_OPT%
if errorlevel 1 (
    echo [ERROR] jpackage fallo al crear el instalador %PKG_TYPE%.
    exit /b 1
)
echo [OK] Instalador generado en %DIST_DIR%

:done
echo.
echo ==========================================================
echo   Empaquetado completado.
echo   Launcher : %LAUNCHER_EXE%
echo   DLL      : %NATIVE_DLL_PATH%
echo   SHA-256  : %NATIVE_SHA256%
echo   Informe  : %NATIVE_REPORT%
echo   Salida   : %DIST_DIR%
echo ==========================================================
endlocal
exit /b 0

:usage
echo.
echo Uso: package-windows.bat [app-image ^| msi ^| exe] [--with-tests] [--console] [--help]
echo.
echo   app-image      Carpeta autocontenida ^(por defecto^)
echo   msi / exe      Instalador ^(requiere WiX Toolset 3.x^)
echo   --with-tests   Ejecuta los tests de Maven
echo   --console      Launcher con consola
echo.
echo Variables: MACE_NATIVE_LIB ^(DLL del paquete de Dev 1^), MACE_NATIVE_SHA256, JAVA_HOME ^(JDK 22+^), JAVAFX_JMODS
endlocal
exit /b 0
