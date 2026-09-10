@echo off
REM ============================================================================
REM  MACE (CorePulse) - Empaquetado nativo para Windows x64
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
REM      --skip-native   No compila la DLL con CMake (usa la que ya exista o arranca en modo mock)
REM      --with-tests    Ejecuta los tests de Maven durante el build (por defecto se omiten)
REM      --console       Launcher con consola visible (util para depurar el puente FFM)
REM      --help          Muestra esta ayuda
REM
REM  Variables de entorno opcionales:
REM      JAVA_HOME       JDK 22+ a usar (si no, se toma el java del PATH)
REM      JAVAFX_JMODS    Carpeta con los .jmod de JavaFX 22 para que jlink genere un
REM                      runtime minimo con JavaFX modular. Si no se define, JavaFX se
REM                      empaqueta como JARs de classpath (funciona, con una advertencia).
REM      MACE_NATIVE_LIB Ruta a mace_native.dll ya compilada
REM
REM  Pasos que ejecuta:
REM      1. Verifica JDK 22+ (FFM API final), jlink, jpackage y Maven.
REM      2. mvn package + copia de dependencias runtime a packaging\output\input.
REM      3. Compila native\ con CMake (MSVC x64) y copia mace_native.dll junto al JAR.
REM      4. jlink: runtime recortado en packaging\runtime.
REM      5. jpackage --type app-image con las opciones de JVM para FFM.
REM      6. Incrusta packaging\app.manifest (requireAdministrator) en MACE.exe con mt.exe.
REM      7. Opcional: jpackage --type msi|exe a partir de la app-image ya firmada con el manifiesto.
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

set "APP_NAME=MACE"
set "APP_VERSION=1.0.0"
set "APP_VENDOR=MACE Team"
set "APP_DESCRIPTION=MACE (CorePulse) - Telemetria de hardware y gestor de procesos"
set "APP_UPGRADE_UUID=7f3c2a1e-9b4d-4c6e-8a1f-2d5e6b7c8d90"
set "MAIN_CLASS=com.mace.presentation.MaceApplication"
set "MAIN_JAR=mace-app-1.0.0-SNAPSHOT.jar"

REM Modulos del JDK que necesita la app (JavaFX se agrega si hay JAVAFX_JMODS)
set "JLINK_MODULES=java.base,java.desktop,java.logging,java.xml,java.naming,java.scripting,java.net.http,jdk.unsupported"
set "JAVAFX_MODULES=javafx.base,javafx.graphics,javafx.controls,javafx.fxml"

REM ---------------------------------------------------------------------------
REM Argumentos
REM ---------------------------------------------------------------------------
set "PKG_TYPE=app-image"
set "SKIP_NATIVE=0"
set "SKIP_TESTS=1"
set "WIN_CONSOLE=0"

:parse_args
if "%~1"=="" goto :args_done
if /I "%~1"=="app-image"     set "PKG_TYPE=app-image"
if /I "%~1"=="msi"           set "PKG_TYPE=msi"
if /I "%~1"=="exe"           set "PKG_TYPE=exe"
if /I "%~1"=="--skip-native" set "SKIP_NATIVE=1"
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

set "JAVA_VERSION="
for /f "tokens=3" %%V in ('"%JDK_BIN%\java.exe" -version 2^>^&1 ^| findstr /i "version"') do (
    if not defined JAVA_VERSION set "JAVA_VERSION=%%~V"
)
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
REM 3. DLL nativa (CMake + MSVC)
REM ---------------------------------------------------------------------------
echo.
echo [3/7] Biblioteca nativa...
if "%SKIP_NATIVE%"=="1" (
    echo [INFO] --skip-native: se omite la compilacion con CMake.
    goto :native_locate
)
where cmake >nul 2>&1
if errorlevel 1 (
    echo [WARN] cmake no esta en el PATH; se omite la compilacion de la DLL.
    goto :native_locate
)
cmake -S "%NATIVE_DIR%" -B "%NATIVE_DIR%\build" -A x64
if errorlevel 1 (
    echo [ERROR] Fallo la configuracion de CMake.
    exit /b 1
)
cmake --build "%NATIVE_DIR%\build" --config Release
if errorlevel 1 (
    echo [ERROR] Fallo la compilacion de la DLL nativa.
    exit /b 1
)

:native_locate
set "NATIVE_DLL_PATH="
if defined MACE_NATIVE_LIB if exist "%MACE_NATIVE_LIB%" set "NATIVE_DLL_PATH=%MACE_NATIVE_LIB%"
for %%D in ("%NATIVE_DIR%\build\Release" "%NATIVE_DIR%\build" "%NATIVE_DIR%\out\build\x64-Release" "%NATIVE_DIR%") do (
    for %%N in (mace_native.dll corepulse_native.dll) do (
        if not defined NATIVE_DLL_PATH if exist "%%~D\%%N" set "NATIVE_DLL_PATH=%%~D\%%N"
    )
)
if defined NATIVE_DLL_PATH (
    copy /Y "%NATIVE_DLL_PATH%" "%STAGE_DIR%\" >nul
    echo [OK] DLL nativa: %NATIVE_DLL_PATH%
) else (
    echo [WARN] No se encontro mace_native.dll. La app arrancara en modo simulado ^(mock^).
)

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
REM 5. jpackage app-image
REM ---------------------------------------------------------------------------
echo.
echo [5/7] Creando app-image con jpackage...
if exist "%DIST_DIR%\%APP_NAME%" rmdir /s /q "%DIST_DIR%\%APP_NAME%"
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"

set "ICON_OPT="
if exist "%PACKAGING_DIR%\icon.ico" set "ICON_OPT=--icon "%PACKAGING_DIR%\icon.ico""
set "CONSOLE_OPT="
if "%WIN_CONSOLE%"=="1" set "CONSOLE_OPT=--win-console"

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
    --java-options "-Dmace.infra.mode=auto" ^
    --java-options "-Dmace.native.lib=$APPDIR" ^
    --java-options "-Djava.library.path=$APPDIR" ^
    %RUNTIME_OPT% %ICON_OPT% %CONSOLE_OPT%
if errorlevel 1 (
    echo [ERROR] jpackage fallo al crear la app-image.
    exit /b 1
)
set "APP_IMAGE_DIR=%DIST_DIR%\%APP_NAME%"
set "LAUNCHER_EXE=%APP_IMAGE_DIR%\%APP_NAME%.exe"
echo [OK] App-image en %APP_IMAGE_DIR%

REM ---------------------------------------------------------------------------
REM 6. Manifiesto UAC (requireAdministrator)
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
    "!MT_EXE!" -nologo -manifest "%PACKAGING_DIR%\app.manifest" -outputresource:"%LAUNCHER_EXE%;#1"
    if errorlevel 1 (
        echo [ERROR] mt.exe no pudo incrustar el manifiesto.
        exit /b 1
    )
    echo [OK] Manifiesto requireAdministrator incrustado con !MT_EXE!
) else (
    echo [WARN] mt.exe no encontrado. Instala el Windows 10/11 SDK para incrustar el manifiesto UAC.
    echo        Mientras tanto, ejecuta %APP_NAME%.exe con "Ejecutar como administrador".
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
echo   Salida   : %DIST_DIR%
echo ==========================================================
endlocal
exit /b 0

:usage
echo.
echo Uso: package-windows.bat [app-image ^| msi ^| exe] [--skip-native] [--with-tests] [--console] [--help]
echo.
echo   app-image      Carpeta autocontenida ^(por defecto^)
echo   msi / exe      Instalador ^(requiere WiX Toolset 3.x^)
echo   --skip-native  No compila native\ con CMake
echo   --with-tests   Ejecuta los tests de Maven
echo   --console      Launcher con consola
echo.
echo Variables: JAVA_HOME ^(JDK 22+^), JAVAFX_JMODS, MACE_NATIVE_LIB
endlocal
exit /b 0
