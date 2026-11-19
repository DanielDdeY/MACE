# Diagnóstico 4B — pruebas de job, elevación y entorno

Fecha: 2026-11-17. Partida: e47cb54ae97044111c3a7d084d2140905b5d198d.
Único archivo previo sin seguimiento: prueba-cpu.txt; se conserva intacto y
fuera del commit. Alcance autorizado: A–D, instrumentación B y comando para
contrastarlo. **No se aplica una corrección funcional ni se avanza al Paso 5.**

## Resultado y grado de certeza

Se reprodujo SIN elevación el patrón exacto de siete fallos y un éxito descrito
por el usuario. Variable controlada: MACE_LHM_HELPER_SHA256. Con pin ajeno al
helper simulado, siete pruebas fallan al verificar el archivo antes de crear
el job; restrictive-job pasa porque elimina expresamente esa variable.

Esto demuestra una dependencia del entorno y una falta de aislamiento del
arnés. Se reproduce también con los binarios 4A preexistentes. **No demuestra
todavía que esa variable fuera la causa en la consola ADMINISTRADOR del usuario.**
La comparación elevada con el script entregado queda NO VERIFICADO hasta recibir
su resultado. No se da por probado un fallo de contención de producción ni
se descarta un segundo problema específico de ese entorno.

| Ejecución propia, token no elevado comprobado | Resultado |
|---|---|
| 4B original, sin pin heredado, CTest Release -j 4 | 8/8 PASS, salida 0 |
| 4B original, pin deliberadamente incorrecto | 7/8 FAIL, salida 8, unos 0,15 s |
| 4A preexistente, sin pin heredado | 8/8 PASS, salida 0 |
| 4A preexistente, pin deliberadamente incorrecto | 7/8 FAIL, salida 8, unos 0,14 s |
| Arnés instrumentado, mismo helper simulado ORIGINAL 4B, entorno heredado sin pin | 8/8 PASS |
| Mismo arnés/helper, pin correcto calculado del fixture | 8/8 PASS |
| Mismo arnés/helper, pin incorrecto deliberado | Mismos siete FAIL; restrictive-job PASS; trust/dev-hash-mismatch |

El negativo usa una cadena sintácticamente válida de 64 caracteres, cuyo valor
no se imprime. Es un rechazo esperado, no una regresión aprobada. Tanto su
control positivo como el negativo mantienen las aserciones de job/PID/ACL/cierre.

## A. Comparación 4A (4ffd231) → 4B (e47cb54)

| Archivo pedido | Diferencia relevante |
|---|---|
| native/src/telemetry/lhm_transport.c | Cinco líneas condicionadas por MACE_LHM_TEST_HOOKS: si está definida la macro y MACE_SIM_SCENARIO es permanent-containment, simula fallo de CreateJobObject. Sin la macro conserva la llamada original. No añadió comprobación de elevación. |
| native/src/telemetry/lhm_transport.h | Incluye estados y traduce causas privadas a ABI 1.5. No cambió los flags del job, ACL, herencia, handshake ni token del hijo. |
| native/tests/test_lhm_job.c | Dos expectativas de restrictive-job pasan de READ_FAILED a HELPER_UNAVAILABLE. Los siete casos que fallan no cambiaron. |
| native/tests/lhm_fake_helper.c | Sin diferencias entre esos commits. |
| native/CMakeLists.txt | Versión 1.5.0 y dos casos nuevos: permanent-containment y metric_reasons. Las definiciones de test_lhm_job y sus ocho casos no cambiaron en 4B. |

También son idénticos entre 4A/4B test-lhm1b.ps1 y lhm_trust.c. La lectura del
pin de entorno en Development era anterior a 4B. Un pin puede corresponder al
helper publicado, a otro fixture o a una compilación anterior; no se afirma
cuál había configurado el usuario.

La inyección requiere simultáneamente:

1. MACE_LHM_TEST_HOOKS definida al compilar el objeto que contiene el transporte.
2. Valor exacto permanent-containment en MACE_SIM_SCENARIO del proceso que abre.

En la configuración CMake comprobada, la macro solo es PRIVATE de
mace_native_lhm_retry_test; no está en test_lhm_job, mace_native_lhm_test ni la
DLL de producto. No se transmite por enlazar una DLL ni por elevar el proceso.
test_lhm_job compila sus propias copias de transporte/confianza. La inyección
no tiene relación con nivel de integridad o pertenencia a Administradores.

Límite: el guard original es #ifdef, así que una definición externa, incluso
con valor 0, lo compilaría. CL, _CL_, CFLAGS o flags de CMake inyectados fuera
de la configuración verificada podrían habilitarlo. El diagnóstico informa
si esas variables contienen el nombre de la macro y el ejecutable informa
hooks_compiled=0/1. No se endureció ese guard en este diagnóstico.

Además, contained/forced-close/descendants/control-close/eof-close y homonym
establecen su propio escenario antes de abrir. Por tanto, un escenario heredado
permanent-containment, por sí solo, tampoco explica los siete fallos. La ausencia
de la macro en el ejecutable instrumentado fue comprobada en ejecución.

## B. Instrumentación añadida, sin corrección de comportamiento

Solo test_lhm_job recibe MACE_LHM_DIAGNOSTICS=1. El producto y las otras DLL
conservan diagnóstico desactivado. No se añaden exports, estados, campos V2,
bytes del wire ni API públicas. No se cambia el ABI 1.5 ni la política 4A.

Se captura una etapa fija y el primer error, en almacenamiento por hilo, antes
de liberar recursos. La salida del test solo contiene:

- causa privada: transient / trust / containment / launch-blocked;
- etapa simbólica, por ejemplo dev-hash-mismatch o create-contained-process;
- dominio y código decimal/hexadecimal;
- token conocido/elevado, existencia de job exterior y presencia del override;
- indicador de compilación del hook.

No imprime ruta, nombre de usuario, SID, PID, nombre de tubería, valor de hash
ni valor arbitrario de una variable de entorno. Para abrupt-parent, el hijo
transmite causa/etapa/código por el mapping privado del arnés; el padre imprime
el diagnóstico aunque el stderr del hijo no sea visible. Ese mapping es solo
evidencia de pruebas: no es el wire ni un registro de ABI público.

Dominios: win32=GetLastError capturado al fallar la API; ntstatus=retorno CNG;
hresult=retorno de la consulta de carpeta de perfil; wait-result=resultado de
la espera; validation=fallo lógico al que se asigna un código simbólico de
Windows. **Validation no afirma que una API devolviera ese error.** En concreto:

```text
LHM_OPEN_FAILED cause=trust stage=dev-hash-mismatch domain=validation code=23 hex=0x00000017
```

23 corresponde a ERROR_CRC elegido para expresar discordancia del pin. No hubo
fallo de CreateJobObject del transporte en esa reproducción: no se llegó a él
(los jobs exteriores del arnés son independientes). Para el resumen
del hijo: cause=1 significa trust, domain=2 significa validation.

Se anotan las etapas de archivo/confianza, hash, descriptor privado, bootstrap,
job/JOB_LIST, creación suspendida, verificación/reanudación, conexión y PID del
peer. Se evita confundir ERROR_IO_PENDING con el resultado final de la espera.
Las operaciones de API y comprobaciones siguen en su orden, sin omitir ninguna;
los cambios de código fuera de los macros separan resultados para capturarlos.

## C. Script frente a CTest directo

test-lhm1b.ps1 hace, en orden:

1. Localiza CMake/CTest de la toolchain seleccionada.
2. Configura LAS FUENTES ACTUALES de native con BUILD_TESTING=ON y
   MACE_LHM_DEVELOPMENT=ON en BuildDirectory.
3. Compila Release, --parallel 4.
4. Ejecuta toda la batería con -C Release, -j 4, --output-on-failure y JUnit.
5. Solo si CTest pasa y se pidió RealHelper o RequireCpu, verifica la publicación,
   guarda los overrides previos, fija temporalmente ruta/hash del helper REAL,
   ejecuta test_lhm_live (RequireCpu añade --require-cpu) y restaura overrides.

La fase real no se alcanza si CTest retorna 8: eso concuerda con la evidencia.
El script no elimina el pin heredado antes de CTest, no cambia token/integridad,
no solicita elevación y no exige no elevar. NoProfile evita cargar un perfil
de PowerShell nuevo; no elimina variables heredadas de la consola que lo lanza.
Cambios de entorno de un test hijo no se propagan a PowerShell ni a otros tests.

CTest directo no reconfigura ni compila, ni ejecuta la fase RealHelper. Puede
usar otro CTest del PATH, otra configuración o diferente paralelismo. Para
replicar la parte de job se necesitan el mismo binario, Release, entorno y
CTest; el diagnóstico usa la toolchain y -j 4 como el script.

**El nombre fase2-paso4a de una carpeta NO selecciona el commit 4A.** Ejecutar
el script ACTUAL sobre esa carpeta vuelve a configurar con las fuentes actuales.
Con las fuentes 4B actuales generaría 76 tests, no 74. Ver 74/76 exige contrastar
qué fuentes/binarios/script se ejecutaron y en qué momento; no basta la etiqueta
de carpeta para declarar una comparación controlada. Aquí se ejecutaron los
binarios 4A preexistentes sin reconfigurarlos. Las dos cachés locales apuntaban
al mismo checkout, aunque se habían compilado en momentos distintos.

## D. Clasificación que respalda la evidencia

| Explicación | Qué se puede concluir |
|---|---|
| Regresión de producto introducida por 4B | No demostrada por los siete fallos. Los cambios pertinentes no explican un requisito de token no elevado y 4A reproduce el mismo fallo con el pin equivocado. No se excluye otro problema sin el diagnóstico elevado. |
| Arnés que falla exclusivamente al elevar | No demostrado: el patrón exacto aparece sin elevar. No hay una precondición de token no elevado en estos casos. |
| Dependencia del entorno / aislamiento insuficiente del arnés | Confirmada localmente. Siete casos heredan un pin que restrictive-job elimina; el pin ajeno impide abrir antes de probar contención. Es la hipótesis mejor respaldada para contrastar en la consola del usuario. |

El dashboard real elevado con helper que desaparece al cerrar, informado por
el usuario, es evidencia positiva de esa ejecución; no sustituye la batería ni
prueba por sí solo la causa del fallo de las pruebas. Se registran también sus
escenarios simulados y prueba sin elevar correctos, sin elevarlos a aprobación
global de 4B. La recuperación física del servicio no se fuerza.

## Comando exacto para el usuario y parada

Desde la raíz del proyecto, **en la MISMA consola de ADMINISTRADOR que falló**,
sin limpiar variables ni cerrar esa consola antes:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\diagnose-lhm-job.ps1 *> .\diagnostico-admin.txt
Get-Content .\diagnostico-admin.txt
```

Repetir desde la consola NORMAL para contrastar:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\diagnose-lhm-job.ps1 -SkipBuild *> .\diagnostico-normal.txt
Get-Content .\diagnostico-normal.txt
```

El script compila en native/build/diagnostico-4b/dev, sin recompilar ni alterar
las carpetas originales 4A/4B. Su CTest auxiliar usa el ejecutable de diagnóstico
pero el **mismo helper simulado del build 4B original**: no confunde el hash de
ese archivo con el de otro link nuevo. Si tu carpeta original es otra, añadir
`-FixtureBuildDirectory 'RUTA_DEL_BUILD_ORIGINAL'` (directorio que contiene Release).
No invoca RealHelper, sensores físicos, instalador, RunAs, HVCI o servicios.

Ejecuta tres condiciones con el mismo token/fixture:

1. inherited: pin heredado sin modificar.
2. pinned-fixture-control: pin correcto del MISMO helper simulado, sin omitir
   la verificación de hash ni las aserciones de contención.
3. intentional-mismatch: pin incorrecto deliberado; se esperan siete fallos y
   restrictive-job PASS. Un CTest con código 8 aquí es el resultado esperado.

Restaura el override de proceso en finally; no cambia variables User/Machine.
El comando de diagnóstico puede terminar con 0 aunque su control negativo
retorne 8: imprime el resultado individual de cada condición y no declara que
la regresión haya pasado. Entregar el texto completo de ambos archivos, con
atención a elevated, inherited_hash_present, inherited_matches_fixture,
hooks_compiled y cause/stage/domain/code.

Si en administrador inherited falla por dev-hash-mismatch y el control con
pin del fixture pasa 8/8, quedará confirmada esa dependencia en el caso real.
Si el pin correcto también falla, su error/etapa orientará el siguiente análisis;
no se aplicará una limpieza de entorno como si fuese una solución demostrada.

## Cambios y comprobaciones de este diagnóstico

- native/src/telemetry/lhm_diagnostics.h: macros/campos privados de diagnóstico
  por hilo, desactivados por defecto.
- lhm_transport.c / lhm_trust.c: capturas previas a limpieza; ninguna API nueva.
- native/tests/test_lhm_job.c: informe de causa/contexto y transporte del error
  del subproceso; las aserciones de job, hijos y proceso homónimo se conservan.
- native/CMakeLists.txt: habilita diagnóstico únicamente en test_lhm_job; no
  añade casos ni altera 76/77.
- native/scripts/diagnose-lhm-job.ps1: controles reproducibles, salida saneada
  y conservación del helper original/entorno.
- Este informe: comparación, certeza y comando pendiente.

Comprobado: compilación Release /W4 /WX, ocho casos positivos y el negativo
7/8 esperado, con y sin instrumentación, todos sin elevar. No se ejecuta una
regresión de arreglo: **no hay arreglo aplicado**. Tras aprobarlo corresponderá
la matriz completa solicitada (helper 105, CTest 76/77, Java 334 + 1 omitida,
payload, protegidos y 181 referencias aceptadas), en normal y administrador.
No se afirma que esa matriz elevada haya pasado.
También pasaron check-protected-telemetry y audit-retired-providers (181
aceptadas, cero inesperadas). Se comprobó ausencia de rutas personales, SID y
hashes completos en la salida saneada; los controles negativos mantienen el
rechazo del hash incorrecto. Los archivos de estados/layouts/reintentos y
RecoverableSampler permanecen idénticos a la base 4B.

NO VERIFICADO: contexto/errores concretos de la consola elevada del usuario y
matriz elevada tras una corrección. Prueba física de CPU previa sigue siendo
evidencia del usuario; no se intentó reproducirla elevando este agente.
Los logs brutos de reproducción están ignorados en native/build/diagnostico-4b
y pueden contener rutas de CTest; el script entregado emite solo campos saneados.
No se incluyen logs brutos, binarios o datos personales en el commit.
**Detenerse aquí hasta resultado del usuario y aprobación para corregir.**
