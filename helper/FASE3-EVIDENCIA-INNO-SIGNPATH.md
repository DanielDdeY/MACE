# Evidencia pública: Inno Setup y SignPath Foundation

Fecha: **2026-11-25**. Lectura de fuentes oficiales; no contacto, solicitud,
cuenta ni descarga de instaladores. Inno sigue elegido **sujeto a consulta**.
No se implementa pipeline o configuración de artefactos de MACE.

## Dos precedentes acreditados documentalmente

| Proyecto | Evidencia de Foundation | Evidencia Inno y cadena de empaquetado/firma |
|---|---|---|
| Stellarium | [Lista oficial Foundation](https://signpath.org/projects.html) incluye Stellarium; [README oficial](https://github.com/Stellarium/stellarium/blob/master/README.md) declara certificado Foundation para paquetes Windows | [CMakeLists](https://github.com/Stellarium/stellarium/blob/master/CMakeLists.txt), líneas 959 y 1086–1103, configura .iss y target stellarium-installer con compilador Inno; [plantilla](https://github.com/Stellarium/stellarium/blob/master/cmake/stellarium.iss6.cmake); [.appveyor.yml](https://github.com/Stellarium/stellarium/blob/master/.appveyor.yml) construye ese target, publica EXE y usa webhook SignPath con release-signing bajo condición SIGNING |
| VSCodium | [Ficha oficial Foundation](https://signpath.org/projects/vscodium/) y [README](https://github.com/VSCodium/vscodium/blob/master/README.md) declaran Foundation | [prepare_assets Windows](https://github.com/VSCodium/vscodium/blob/master/build/windows/prepare_assets.sh) invoca tareas system/user-setup y mueve EXE a assets; [package.sh](https://github.com/VSCodium/vscodium/blob/master/build/windows/package.sh) mantiene componente Inno updater; [publish-stable-windows](https://github.com/VSCodium/vscodium/blob/master/.github/workflows/publish-stable-windows.yml), líneas 172–204, prepara esos assets, sube EXE/MSI y espera devolución de firma SignPath |

Stellarium: árbol master leído **e233fa9**; VSCodium: **ca7f7b4**.
Las URLs de rama pueden cambiar; los prefijos identifican la instantánea leída,
no son hashes de instaladores. La lista Foundation y el código de CI son
fuentes separadas del propio proyecto, no solo un README que promete firmar.
No se copian tokens, endpoints de integración o correos de esos workflows.

En VSCodium la identificación Inno del instalador EXE se complementa con la
cadena upstream de VS Code: el [gulpfile oficial](https://github.com/microsoft/vscode/blob/main/build/gulpfile.vscode.win32.ts),
leído el 25/11, resuelve innosetup/bin/ISCC.exe y build/win32/code.iss en su
función packageInnoSetup. Sus tareas system/user-setup generan
los EXE; el MSI del mismo workflow es un canal distinto. No llamar al MSI un
“MSI Inno”. La presencia de inno_updater sola no demostraría qué empaqueta un
EXE; ver también fuentes upstream antes de replicar esa integración.

**Alcance:** evidencia documental pública de uso conjunto, no inspección
Authenticode ni reconstrucción de una release exacta. Firmas vigentes de cada
asset, configuración privada, aceptación de versión Inno concreta y excepciones
negociadas: **NO VERIFICADO**. No se descargaron binarios para comprobarlo.
Stellarium tiene una cadena Inno explícita y una declaración/listado Foundation;
VSCodium añade la acción de firma para sus EXE. Ninguno acredita la elegibilidad
del payload distinto de MACE, PawnIO o una licencia faltante.

## Candidatos descartados como prueba suficiente

Flow Launcher usa Squirrel según su appveyor actual: no se cuenta como ejemplo
Inno. QuickLook actual usa otra cadena de setup y no se cuenta por memoria.
rEFInd_GUI tiene .iss y workflow multietapa, pero su propio texto dice que
la firma es opcional y puede no estar configurada: no acredita por sí solo
que exista una release de producción firmada. No se usa ese caso como prueba
concluyente. Se priorizan fuentes de proyectos y del programa sobre buscadores.

El intento /projects/ devolvió 404; la lista oficial correcta consultada fue
/projects.html. La ficha individual de Stellarium no abrió con la herramienta;
se acredita por lista y README, sin fingir haber abierto esa ficha.

## Decisión y límite

Mantener Inno sujeto a la consulta que presentará el usuario. Estos precedentes
permiten formular una pregunta concreta sobre formato y artefactos; no permiten
afirmar compatibilidad jurídica o aceptación de MACE. No se envía consulta ni
se cambia de herramienta. Ver bloqueos de licencia en el resumen provisional.
