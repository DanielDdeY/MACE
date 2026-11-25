# Comprobaciones de solo lectura — auditoría del Paso A

No compila ni ejecuta MACE/helper/PawnIO; no toca servicios, registro, entorno,
imagen ni manifiestos. Lee archivos como datos y hace GET públicos solo con
`-ConsultarFuentes`. Únicas escrituras: CSV/JSON de resultados bajo una
subcarpeta de `native/build/`. No elimina archivos. No requiere administrador.

Requisitos: Windows PowerShell 5.1, Python >= 3.10 sin paquetes externos,
checkout y la imagen congelada existente, publicado helper 205 y cachés
NuGet/Maven de las versiones inspeccionadas. Si falta algo no se restaura,
instala ni construye; se informa NO VERIFICADO/error. Un clon sin los artefactos
locales no puede acreditar la imagen: debe recibir los mismos bytes de prueba.

Desde la raíz del checkout:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\helper\auditoria-licencias\comprobar.ps1
$LASTEXITCODE
```

El modo anterior usa `fuentes-oficiales.json`, una instantánea compacta de las
lecturas públicas con su fecha. **No afirma una consulta nueva ni actualidad
de la licencia**. Para volver a consultar exclusivamente fuentes oficiales:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\helper\auditoria-licencias\comprobar.ps1 -ConsultarFuentes
$LASTEXITCODE
```

No hay credenciales ni envío de archivos. HTTP 200 solo acredita acceso; el
alcance semántico de cada fuente se revisa en el informe. Un redirect fuera
de la lista de hosts se rechaza. Los errores se guardan por clase, sin rutas
o mensajes del sistema. La instantánea versionada no se sobrescribe.

Resultados en `native/build/auditoria-licencias/`:

| Archivo | Comprobación |
|---|---|
| cobertura-imagen.csv | Cada archivo real de imagen a fila/componente; no basta contar directorios |
| cobertura-helper-202.csv | Cada entrada y comparación de bytes contra manifiesto |
| cobertura-publicado-205.csv | Cada entrada del publicado, con tres exclusiones explicadas |
| modulos-jlink.csv / fuentes-locales.json | Once módulos, todos los JAR, versiones PE/paquete; captura antes de leer el informe |
| evidencia-por-fila.csv | Fuente predominante, alcance y ausencia de acreditación; fuente no equivale a legal clearance |
| doble-licencia-comercial.csv | Revisión explícita por fila; ausencia de una mención no prueba ausencia de acuerdos |
| versiones.csv / versiones-no-acreditadas.csv | Comparaciones que sí se pueden hacer y versiones sin prueba |
| spdx-osi.csv / identificador-ausente.csv | SPDX, excepción o LicenseRef; lista OSI independiente y aliases explícitos |
| urls.csv | URL, destino, titular/proyecto o autoridad de licencias, fecha y acceso |
| lenguaje.csv / datos-locales.csv | Solo coincidencias/ubicación, no reproduce datos sensibles |
| estructura-conflictos.csv / privacidad-estatica.csv | Campos C1–C9 y usos estáticos de red/IPC/escrituras/registro |
| muestreo-13.csv | Diez obligatorios y tres al azar con semilla 20261123 |
| resultado.json | Cifras para el resumen |

El muestreo adicional usa todas las otras filas de componente, ordenadas y
sin ponderar por número de archivos. Es reproducible, no aleatoriedad secreta.
Los dos pasos del programa separan captura primaria y cotejo documental;
el auditor humano sigue siendo el autor, no un tercero independiente.

Código 0 = terminó la comprobación sin fallos mecánicos de integridad/cobertura/
versiones comprobables/saneado. **No significa que todas las licencias estén
acreditadas ni que SignPath acepte el proyecto.** Código 1 = error del arnés;
2 = fallo mecánico. Revisar además los campos NV, las URLs y las columnas:
un dato sin metadatos puede seguir NV aunque el proceso termine con 0.

La pertenencia a OSI se comprueba por ID exacto, con aliases de familia
GPL/LGPL expresamente indicados. «NO_LISTADO» no significa revocación ni
incompatibilidad legal. Una divergencia con el booleano SPDX exige revisión.
No hay un comodín para cualquier archivo del helper: se usa deps y casos
de configuración/host explícitos. El runtime jlink se inventaría como
distribución junto con sus legales, sin fingir analizar código comprimido
de cada módulo como si fuese un componente independiente.


## Entrega A–D, 2026-11-25

- `auditar-repositorio.py --repo <clon-o-mirror> --out <JSON>` lee Git y escribe
  solo un informe saneado; no red, fetch, checkout, configuración ni refs.
- `probar-mono-manual.ps1` **no es un comprobador de solo lectura**: es un
  ensayo físico que debe ejecutar el usuario. Carga la DLL de desarrollo y
  lanza el helper aislado; Reconnect pide confirmación y termina exclusivamente
  su propio hijo verificado. El agente no ejecutó este script. No instala,
  eleva ni altera servicios. Comandos/precondiciones en FASE3-EXPERIMENTO-MONO.md.
- Manifiestos operativos y salidas privadas quedan ignorados en native/build;
  no adjuntar copias con hashes completos, rutas o logs brutos de compilador.
