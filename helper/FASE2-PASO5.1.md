# Fase 2 — Subpaso 5.1: imagen regenerada y verificada

Fecha: 2026-11-17. Rama: `LHM_implementado`.
Commit de partida: `2ef7dbf63bb3bbc725b3dfc87d32a2646c11dc19`.
Árbol limpio confirmado antes de empezar. Los tres TXT de diagnóstico siguen
intactos y excluidos localmente; no se modifica `.gitignore`.

El usuario aprobó 5.0/endurecimiento y confirmó **13 casos, código 0, solo en
administrador**. Se registra esa batería aislada como VERIFICADO POR EL USUARIO;
no se extrapola a los 347 casos Java ni a la imagen final elevada.

## Resultado y construcción

Se regeneró `packaging/dist/MACE` mediante **package-windows.bat app-image**,
con JDK 22.0.2 y Maven. Java se compiló desde el commit de partida con perfil
production. Se reutilizaron la DLL Production y el helper publicado ya
probados, sin recompilar/cambiar sus contratos. Runtime .NET 10.0.12,
biblioteca LHM 0.9.6 y helper de aplicación **1.1.1**.

El constructor se ejecutó con `Administrator=False`. Se verificaron las rutas
generadas antes de su limpieza y se preservó PawnIO fuera de dist/MACE, en
packaging/prerequisites, comprobando hash/firma. No había procesos MACE/helper
abiertos y no se terminó ninguno para empaquetar. El primer intento dentro del
sandbox no pudo limpiar staging; se repitió fuera del sandbox, con el mismo
token normal, sin RunAs ni cambios de permisos o del empaquetador.

Se utilizó el modo normal sin consola. No se cambió el manifiesto fuente ni
ningún launcher: el empaquetador incrustó el requireAdministrator existente.
Se generó app-image; no MSI/EXE de instalación. No se ejecutó PawnIO.

Maven del empaquetador usa skipTests; la regresión Java completa y de payload
se ejecutó después con el script que proporciona DLL Development a los
fixtures y DLL Production al payload. No se confunden ambas entradas ni se
declaran aprobadas pruebas omitidas por falta del helper simulado.

## Contenido, versiones y tamaño

**370 archivos, 147.787.297 bytes (140,94 MiB), 11 JAR**.
Son 10 JAR en app (aplicación + nueve dependencias de ejecución) y jrt-fs.jar
en el runtime Java. El manifiesto del payload declara **223 archivos**; los
metadatos de jpackage, manifiesto del payload y runtime Java se cuentan por
separado en el total de la imagen.

Frente a la imagen de 1D (369 archivos / 147.719.124 bytes), hay un archivo
adicional: `app/lhm/helper-runtime.manifest`. El incremento total es 68.173
bytes e incluye las versiones nuevas de los componentes. No se añadieron
documentos internos ni scripts para explicar ese incremento.

| Componente | Versión observada | Bytes | SHA-256 de esta construcción |
|---|---|---:|---|
| MACE.exe | 1.0.0, versión de app conservada | 479232 | `38fed9fda64ea2d99f82d2379bb2eb41219a570a82b3b19ef319b0fe86f3405e` |
| MACE-Preview.exe | 1.0.0 | 479232 | `443b495aea4fb9ea73392c4906649af80f32ba3bc3abfd1cb05283e7547df621` |
| app/mace-app-1.0.0-SNAPSHOT.jar | Java del commit 2ef7dbf; perfil production | 343375 | `3536bb00b87fc18f04fc71f7fb766d988292fb84f50605920e470738fdc4931d` |
| app/mace_native.dll | **1.5.0**, PE 1.5.0.0; ABI **1.5** | 223744 | `bb240a670286b6c420047fe1391a3023ee71892b4bb87a62605156fe56e0421b` |
| app/lhm/Mace.Lhm.Helper.exe | **1.1.1**, PE 1.1.1.0 | 162304 | `521438f76f65d7c7ac9abce49defc7a15231b79a4f99e13c919388451312655e` |
| app/lhm/Mace.Lhm.Helper.dll | 1.1.1, PE 1.1.1.0 | 26624 | `a66e003d22edb496474e2dfa83f3cdec1a00b732010a6b6ccef6d10aa13370c9` |
| app/lhm/helper-runtime.manifest | Privado 2, 202 archivos runtime | 19065 | `df7deb0f3334571cd6dcf0718d91be0fd34c7968175ad700499d081372f3de77` |
| app/runtime-manifest.json | Esquema 1 | 47418 | `585ca6f279081feb524433eec9f5b8a62c7fac4752688bcbfeee4f1c0f3fca80` |
| app/prerequisites/PawnIO_setup.exe | 2.2.0 | 3410960 | `1f519a22e47187f70a1379a48ca604981c4fcf694f4e65b734aaa74a9fba3032` |

El inventario completo está en `packaging/dist/MACE-SHA256SUMS.txt`, **fuera de
la imagen**. Contiene los 370 pares hash/ruta, ordenados ordinalmente, con rutas
relativas a dist que comienzan por MACE/. SHA-256 del propio inventario:

`8FCE074CD57C29FAFA19E345C1963574DC6E67C62441CC3A0071A5CB10CA8368`

Los hashes del JAR/helper/DLL identifican sus bytes, no una aprobación legal o
firma del publicador. El campo histórico helperVersion del runtime-manifest
describe LHM **0.9.6**; la versión de aplicación del helper se comprobó en sus
recursos PE como **1.1.1**. No se cambió ese esquema.

## Verificación de la imagen

| Comprobación | Estado / evidencia |
|---|---|
| verify-image.ps1 | AUTOMATIZADO: PASS; 370 archivos, 11 JAR, dos excepciones aceptadas, cero inesperadas |
| packaging/tests/test-image.ps1 | AUTOMATIZADO: PASS; rechaza .lib temporal y copia NDD en ruta ajena. Retira únicamente los archivos propios y vuelve a aprobar la imagen |
| verify-runtime.ps1 -AppImage | AUTOMATIZADO: PASS; 223 archivos declarados, hashes completos y cinco componentes requeridos |
| DLL Production | AUTOMATIZADO: hash idéntico al binario probado en los 77 CTest Production; marcador Production vinculado al manifiesto. Sin selectores MACE_SIM_SCENARIO, permanent-containment, diagnóstico de fixture o marcador Development |
| ABI/FFM de los componentes incluidos | AUTOMATIZADO: Java del runtime + JAR/DLL de la imagen: ABI=1.5, BUILD=1.5.0, CP_PollSystemMemoryV2=MACE_OK; carga de los símbolos obligatorios sin inicializar helper |
| Manifiesto UAC | AUTOMATIZADO: mt.exe extrajo el recurso real de MACE.exe; requestedExecutionLevel=requireAdministrator |
| Configuración del launcher | AUTOMATIZADO: ruta $APPDIR/mace_native.dll y hash coincidente; modo native y native access presentes. CFG comprobado expresamente, además del manifiesto de archivos |
| Java Production | AUTOMATIZADO: metadato exacto profile=production dentro del JAR; sin Test/Probe.class o recursos de test |
| Helper/dependencias | AUTOMATIZADO: publicación de 205 archivos verificada antes de seleccionar 202 runtime; helper-runtime.manifest cotejado contra cada dependencia y contra el hash compilado de la DLL |
| PawnIO intacto | AUTOMATIZADO: hash coincide con política 2.2.0 y firma original Valid; se copió sin ejecutarlo, extraerlo, alterarlo ni volverlo a firmar |
| Avisos y separación repositorio/producto | AUTOMATIZADO: notices/textos de terceros presentes; whitelist del payload y escaneo total sin docs internos, pruebas, scripts, artifacts, logs, PDB, bibliotecas de desarrollo o clases de test |
| Firma de archivos propios | NO VERIFICADO como firma de publicación: MACE/Preview/DLL/helper están NotSigned. Certificado/estrategia pendientes; ganchos conservados |

Los avisos legales de terceros y del JDK son parte del producto; no se
confunden con informes internos del repositorio. El manifiesto generado y sus
hashes no sustituyen firma ni permisos de instalación.

## Excepciones NDD aceptadas, sin ampliación

La política sigue exactamente en [compatibility-references.json](../packaging/compatibility-references.json):
RAMSPDToolkit-NDD **1.4.2**, exclusivamente `app/lhm/RAMSPDToolkit-NDD.dll`, hash
`42C1CCAF90592542EC64E801AE599ED803BB98F4039B2ABB7119CB8EA2087CBA`.

| Offset | Referencia de compatibilidad aceptada |
|---:|---|
| 105819 | Campo de enum reservado de compatibilidad, nombre exacto fijado en la política |
| 127562 | Interfaz vacía de compatibilidad, nombre exacto fijado en la política |

Son las mismas dos referencias previamente revisadas, sin implementación de
ese driver ni proveedor activo de MACE. La prueba copió los mismos bytes a
otra ruta y comprobó el rechazo: el hash por sí solo no amplía la excepción.
No se cambiaron ruta, hash, offsets, cantidad ni política.

## Anulaciones: qué se probó y qué falta

1. **AUTOMATIZADO, componentes de la imagen:** Java incluido + JAR/DLL incluidos,
   con las cuatro variables ajenas y pins de CFG, cargan la DLL esperada y pasan
   FFM/ABI/RAM. No se inicializa el helper en esa comprobación.
2. **AUTOMATIZADO, DLL exacta de la imagen:** test_lhm_production_profile carga
   una copia aislada de ese binario, sin lhm adyacente y con ruta/hash ajenos del
   helper. Resultado HELPER_UNAVAILABLE y uso CPU Windows válido; no usa el
   helper ajeno. El binario copiado conserva el hash de la imagen.
3. **VERIFICADO POR EL USUARIO:** 13 pruebas del endurecimiento Java en consola
   administradora, código 0. No es el launcher final ejecutado.
4. **NO VERIFICADO / pendiente del usuario:** MACE.exe completo de la imagen
   elevada con las cuatro variables ajenas. Este agente no solicita elevación
   ni ejecuta el launcher requireAdministrator.

Para el punto 4 se entrega `packaging/tests/test-image-environment.ps1`, fuera
de la imagen. Se verificó su sintaxis y su negativa en token normal: código 2,
NO_VERIFICADO/ADMIN_REQUIRED y ningún proceso iniciado. El tramo elevado está
**NO VERIFICADO** hasta tu resultado; no se presenta el control normal como
prueba de la UI elevada.

## Comando exacto para confirmar la imagen

Cierra tú las instancias MACE/Preview, incluidas las iniciadas desde fuentes.
Abre una consola de administrador manualmente, sitúate en la raíz del
repositorio y ejecuta:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-image-environment.ps1
$LASTEXITCODE
```

El script verifica pins/perfil/runtime, copia fixtures fuera del producto y
define **solo en el proceso hijo** MACE_NATIVE_LIB, MACE_NATIVE_SHA256,
MACE_LHM_HELPER y MACE_LHM_HELPER_SHA256 con rutas/hashes ajenos válidos. No
modifica variables User/Machine ni tu consola. Abre MACE.exe de la imagen sin
RunAs: hereda la elevación que tú proporcionaste.

Observa el módulo nativo cargado por MACE y su helper hijo mediante consultas
de solo lectura, comprobando sus rutas de imagen en tres observaciones. La
inspección de procesos del arnés no introduce WMI en la telemetría del producto.
Éxito esperado: código **0**, PASS con packaged_native_loaded=1,
packaged_helper_started=1 y ambos foreign_*=0. Confirma selección de componentes;
no afirma temperatura/potencia físicas válidas por el mero hecho de arrancar.

Al finalizar, **cierra tú la ventana visible de MACE**. El script no la cierra,
oculta ni termina procesos; tampoco lo hace si falla. Código 2 sin administrador
significa NO VERIFICADO. Código 1 exige revisar el motivo fijo del resultado;
no se fuerza servicio ni se repara automáticamente.

Los resultados se guardan bajo
`native/build/fase2-paso5.1/environment-<id>/result.txt`. Solo campos fijos,
booleanos y códigos numéricos: sin perfiles, usuarios, máquina, SID o hashes
completos. La salida cruda de la aplicación se drena y descarta. Los fixtures
permanecen ignorados fuera de la imagen. Comunica el
resultado saneado y el código de salida; no hace falta compartir logs brutos.

## Regresión y alcance de evidencias

| Batería | Consola normal | Administrador |
|---|---|---|
| Helper | 105 PASS, evidencia previa del endurecimiento; código/helper sin cambios | NO VERIFICADO, matriz completa |
| CTest Development | 76/76 PASS, evidencia previa; sin cambios C | VERIFICADO POR EL USUARIO: 76/76 y CPU real, DLL Development 1.5.0 |
| CTest Production | 77/77 PASS, evidencia previa; DLL de imagen con el mismo hash | NO VERIFICADO |
| Java completo | **347 PASS + 1 omitida**, 348 total, repetido tras compilar/empaquetar; cero fallos/errores | Solo los 13 casos de política verificados por el usuario; resto NO VERIFICADO |
| Payload | **8 PASS + rechazo DLL Development + rechazo Java Development**, repetido | NO VERIFICADO |
| Protegidos / referencias retiradas | PASS; **181 aceptadas, 0 inesperadas**, auditoría final de este subpaso; TSV sin cambios | NO VERIFICADO |

El fixture de payload declara 214 archivos y usa un único JAR de prueba con
clases principales. La app-image real declara 223: añade nueve dependencias
JAR runtime. No son nueve archivos de desarrollo. Java conserva la omitida
opcional de DLL antigua; las 13 nuevas pruebas explican 347 frente a 334.
Los ocho casos de watchdog/instancia con fixture sí se ejecutaron.

## Pendientes, cambios y parada

**NO VERIFICADO:** la ejecución integral elevada anterior; cierre/kill/reinicio
del producto completo, instancia mixta, coexistencia con LHM y suspensión
física que corresponden a 5.2. Recuperación real de PawnIO sigue NO VERIFICADA:
el intento del usuario devolvió ERROR 1052 y el servicio siguió RUNNING; no se
forzó ni se interpreta como transición observada. Ausente → instalado, 3010
real, Intel/NVIDIA, HVCI y equipo limpio permanecen NO VERIFICADO. Firma,
licencia y revisión legal siguen pendientes, con sus ganchos.

| Archivo versionado | Cambio de 5.1 |
|---|---|
| helper/FASE2-PASO5.1.md | Informe, tamaños/hashes, verificaciones y procedimiento de confirmación |
| helper/FASE2-H-5.0-JAVA.md | Registro posterior de tus 13 casos elevados, sin ampliar cobertura |
| packaging/README.md | Imagen 5.1 y separación del empaquetado Production frente a fixtures Development |
| packaging/tests/test-image-environment.ps1 | Prueba manual exclusiva de selección de componentes, con entorno del hijo y salida saneada |

Outputs ignorados: packaging/dist/MACE, su inventario fuera de imagen, staging,
runtime de construcción y evidencias bajo native/build/fase2-paso5.1. Los logs
brutos del constructor pueden incluir rutas locales; no se versionan ni se
incluyen en el producto. El informe nativo generado queda fuera de MACE.

No se cambia código de producto, ABI, protocolo, helper, política 4A, estados,
manifiestos fuente o launchers. No se instala ni desinstala/ejecuta PawnIO,
cambia HVCI, fuerza servicio, eleva o termina un proceso ajeno al arnés, ni se
fusiona a principal. No se lee/escribe la señal de registro propuesta del 3010.

**Fin de 5.1: detenerse y esperar tu confirmación de la imagen antes de 5.2.**
No se entregan todavía FASE2-PASO5-PRUEBAS.md ni scripts de cierre, kill,
recuperación o suspensión, y no se redacta aún el informe 5.3 ni se implementa
Fase 3. El inventario de escrituras ya preparado se incorporará en 5.3.
