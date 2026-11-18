# Arnés nativo de MACE

Estado vigente: ABI 1.5, DLL 1.5.0, protocolo privado 2 y helper 1.1.1.
Este directorio contiene pruebas de desarrollo; no se distribuye en la imagen.

## Entorno de la consola

**No ejecutar las baterías desde una consola que arrastre variables MACE_* de
otra prueba, compilación o ejecución de MACE.** Usar un proceso de prueba
aislado. No basta con -NoProfile: evita cargar otro perfil de PowerShell,
pero conserva el entorno heredado del proceso que lo lanza.

En particular, MACE_LHM_HELPER_SHA256 puede contener el hash del helper real
o de otro link del fixture. Siete casos de lhm_job heredan ese pin y lo aplican
al ejecutable simulado recibido como argumento; si no corresponde, falla la
confianza antes de crear el job del transporte. restrictive-job elimina ese
pin en su subproceso y puede pasar mientras los otros siete fallan. No es una
precondición de token no elevado.

Resultados del usuario y diagnóstico 5f818cb: el control con pin correcto del
fixture pasa en consola normal y administradora. En el TXT administrador,
inherited todavía retorna 8 y el control positivo retorna 0. El negativo
intencional retorna 8 en ambas consolas, como debe. No se interpreta el éxito
del control como aislamiento automático de toda la batería.

## Qué hace actualmente cada herramienta

- test-lhm1b.ps1 configura las fuentes actuales con Development explícito,
  compila Release y ejecuta toda la batería CTest con -C Release y -j 4.
  **No limpia todas las variables MACE_* antes de CTest.**
- Los tests fijan sus escenarios/overrides cuando están programados para ello;
  no se debe asumir que cada ejecutable restablece todo el entorno.
- Solo tras pasar CTest, RealHelper/RequireCpu verifican la publicación, guardan
  ruta/hash previos del helper, fijan temporalmente los reales y los restauran
  en finally. RequireCpu exige temperatura y potencia CPU válidas; no instala
  drivers. La elevación de esa prueba física la realiza el usuario.
- diagnose-lhm-job.ps1 compila en una carpeta de diagnóstico independiente,
  utiliza el helper simulado del build original sin modificarlo y compara:
  inherited, pinned-fixture-control e intentional-mismatch. Restaura la variable
  de proceso en finally. No modifica variables de usuario o máquina ni elimina
  globalmente MACE_*. El negativo debe fallar; no cuenta como regresión fallida.
- test_lhm_job informa causa, etapa y dominio/código de error sin rutas, hashes,
  identidad o PID. La instrumentación MACE_LHM_DIAGNOSTICS se compila solo en
  ese ejecutable. El hook de fallo de contención pertenece a otra DLL de pruebas.

No se cambió código del arnés al escribir este README. La dependencia del
entorno se documenta; no se afirma que 5f818cb la haya corregido automáticamente.

## Invocación aislada de CTest

Desde la raíz del repositorio, esta orden crea una PowerShell hija, elimina
MACE_* solo de su entorno de proceso y ejecuta la batería. No borra archivos
ni cambia las variables de usuario/máquina de Windows; la consola padre conserva
sus valores. La elevación se conserva: abrir primero la consola que corresponda.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -Command 'Get-ChildItem Env:MACE_* | Remove-Item; & .\native\scripts\test-lhm1b.ps1 -BuildDirectory .\native\build\fase2-paso4b\dev'
```

Para la CPU física, el usuario utiliza una consola administradora y añade
RealHelper/RequireCpu dentro de la misma invocación aislada:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -Command 'Get-ChildItem Env:MACE_* | Remove-Item; & .\native\scripts\test-lhm1b.ps1 -BuildDirectory .\native\build\fase2-paso4b\dev -RealHelper -RequireCpu'
```

Estas órdenes se entregan para uso del usuario; no se volvieron a ejecutar en
la consolidación 5.0. Las pruebas Java de integración necesitan sus rutas y
hashes explícitos verificados, en otro proceso preparado para esa batería.
No confundir esa configuración deliberada con heredar valores de una consola
anterior. Los negativos de hash también configuran sus valores deliberadamente.

CTest directo no recompila ni selecciona un commit según el nombre del build.
Consultar [el diagnóstico](../../helper/DIAGNOSTICO-4B-ELEVACION.md) para el
contraste controlado y [la consolidación](../../helper/FASE2-PASO5.0.md) para
la separación entre evidencia automatizada y física.

## Logs locales: propuesta de exclusión

No versionar diagnostico-admin.txt, diagnostico-normal.txt ni prueba-cpu.txt.
Propuesta de entradas exactas, ancladas a la raíz, para .gitignore:

```gitignore
/diagnostico-admin.txt
/diagnostico-normal.txt
/prueba-cpu.txt
```

Alternativa local sin cambiar la política compartida: las mismas entradas en
.git/info/exclude. No usar *.txt, que ocultaría avisos de terceros/documentación.
En 5.0 solo se propone la exclusión: no se modifican esos archivos de
configuración, y los tres TXT originales permanecen intactos sin seguimiento.

Evitar git add . / git add -A para esta entrega; añadir solo los documentos
aprobados y revisar git diff --cached --name-only antes de hacer commit. Los
logs ignorados de native/build y app/target siguen siendo evidencia local,
pueden contener datos de herramientas y no forman parte del producto.
