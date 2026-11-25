# Paso B parcial: alcance aplicado y diffs pendientes

Fecha: **2026-11-25**, America/Lima. Partida **9a0ee8f**, rama
`LHM_implementado`; árbol seguido limpio al comenzar. Los dos documentos
previos sin seguimiento de `helper/` se conservan fuera de los commits.

## Autorización registrada

- Auditoría A aceptada **solo como base provisional**, sin respaldo legal ni
  evidencia suficiente para SignPath. MIT y derivados cerrados confirmados.
- Inno se mantiene sujeto a consulta; NSIS solo se compara, no se adopta.
- Se prioriza proponer exclusión Mono, sin aplicarla; licencia sigue abierta.
- Propuesta JDK ahora, implementación antes de 3.3 y después de aprobación.
- Revisión cualificada antes de publicar; preparar dossier acotado, no enviarlo.
- Solo B parcial: contribución/plantillas y propuestas de metadatos.
  C, D y E siguen fuera de alcance; no políticas de firma ni workflows nuevos.

LICENSE está en [GitHub/main](https://github.com/DanielDdeY/MACE/blob/main/LICENSE),
leído por conector el 25/11: MIT, copyright 2026 DanielDdeY. **No está en este
checkout**. No se creó, copió, cambió ni se hizo pull/merge para traerlo.
No se describe como inexistente en el repositorio remoto. Antes de una futura
distribución, la fuente seleccionada debe contenerlo; su sincronización queda
fuera de esta entrega. No se vuelve a preguntar por MIT, titular o roles.

## Cambios autorizados de documentación

| Archivo | Cambio |
|---|---|
| FASE3-LICENCIA-RESUMEN.md | Identifica las 16 filas y cuatro cotejos NV, destino/motivo y alcance provisional; no reclasifica todo el inventario como legalmente aprobado |
| CONTRIBUTING.md | Aportaciones propias MIT, atribuciones, evidencia saneada, revisión y contratos congelados; DCO no obligatorio |
| .github/ISSUE_TEMPLATE/bug_report.md | Plantilla mínima de fallo reproducible, sin pedir logs personales ni inventar labels/asignados |
| .github/ISSUE_TEMPLATE/feature_request.md | Necesidad, alternativas y alcance |
| .github/pull_request_template.md | Problema, origen, validación, riesgos y aprobación de cambios congelados |
| Cuatro propuestas solicitadas | JDK, Mono, comparación de instaladores y dossier legal acotado, cada una con decisiones al final |
| Registro de continuidad | Nota fechada del nuevo alcance; no borra resultados o aprobaciones históricas |

En el resumen, los tipos son **previstos**, pues aún no existe instalador final.
Hay 13 filas (a), 3 filas documentales sobre dependencias externas (c*) y 0 (b).
No se inventa un binario de imagen excluido del instalador para encajar las
categorías. musl solo tiene aviso agregado como evidencia disponible.

## Metadatos: diff pendiente, ningún archivo de producto modificado

[METADATOS-LICENCIA.patch](pendientes/METADATOS-LICENCIA.patch) propone:

| Archivo real | Campo propuesto | Efecto y gate |
|---|---|---|
| app/pom.xml | licenses/MIT y URL oficial | POM se incluye en JAR por Maven: puede cambiar JAR, hash del manifiesto global e imagen. **Esperar aprobación** |
| helper/Mace.Lhm.Helper/Mace.Lhm.Helper.csproj | PackageLicenseExpression MIT, Copyright y Authors | Expresión afecta pack; Copyright/Authors pueden afectar atributos/recursos. Tratar DLL/EXE como nuevos bytes, manifiesto helper/pin C y DLL global afectados. **Esperar aprobación** |
| helper/Mace.Lhm.Protocol/Mace.Lhm.Protocol.csproj | Mismos tres campos | Ensamblado propio viaja con helper: recalcular hashes/pins. No altera wire ni su versión. **Esperar aprobación** |
| helper/Mace.Lhm.Tests/Mace.Lhm.Tests.csproj | Mismos campos, coherencia del código propio | Sus binarios no viajan en imagen; propuesto junto al resto, sin aplicar ni empaquetar tests |

Authors fija únicamente el alias público declarado para código propio, evitando
depender de defaults del entorno en un pack futuro. No se afirma que el paquete
actual haya filtrado identidad local; no se modifican autores de dependencias.

No se cambia Directory.Build.props, versión, ProductName/ProductVersion,
UAC, TFM, código de sensores ni política 4A con el parche MIT. El conflicto
de metadatos uniformes para SignPath permanece pendiente de interpretación.
No se promete que PackageLicenseExpression por sí solo cambie un PE: el
efecto definido aquí incluye Copyright, y los bytes finales deberán medirse.
[Propiedades .NET/AssemblyInfo](https://learn.microsoft.com/en-us/dotnet/core/project-sdk/msbuild-props#generating-assembly-info)
y [licencias en pack](https://learn.microsoft.com/en-us/nuget/reference/msbuild-targets#packing-a-license-expression-or-a-license-file),
fuentes públicas abiertas el 25/11. No se deduce eficacia ni identidad de bytes
de un build no realizado.

Los otros dos parches son independientes y **no se aplican en bloque**:
[MONO-EXCLUSION.patch](pendientes/MONO-EXCLUSION.patch) y
[JDK-JAVAFX.patch](pendientes/JDK-JAVAFX.patch).
Mono requiere aún diff real de lock/deps tras restore autorizado; JDK requiere
build/artefactos y versiones de test compatibles verificados. No son cambios
completos listos para publicar. Cada propuesta lista esos límites.

`git apply --check` sirve solo para revisar aplicación textual de cada parche
sobre esta base. No se usó `git apply` para aplicarlos. Nada modifica hashes
fijados de la imagen actual ni el manifiesto. Cualquier reconstrucción futura
deberá medir la cadena helper final → manifiesto → pin/DLL → launcher/imagen.

## DCO: opciones, no requisito impuesto

| Opción | Ventajas | Coste / límite |
|---|---|---|
| MIT entrante + revisión de origen, sin DCO | Menor fricción; opción vigente en CONTRIBUTING | Menos evidencia formal de declaraciones por commit |
| DCO 1.1 para contribuciones futuras | Declaración de derecho a aportar, traza de origen y sign-off | Requiere acordar identidad pública, privacidad y tratamiento de PR; no es Authenticode ni cesión automática |
| CLA específico | Podría regular casos adicionales | Redacción/revisión y gestión mayor; no justificado ni propuesto como requisito ahora |

Recomendación: **no activarlo ahora sin decisión**; estudiar DCO 1.1 para
contribuciones futuras si se desea esa trazabilidad. No añadir bots, checks,
trailers a commits previos o identidad/correos por cuenta del agente.
[DCO oficial](https://developercertificate.org/), leído el 25/11, advierte
que el registro público incluye la información aportada con el sign-off.
No se copia ni modifica el certificado en esta entrega.

## Avisos de terceros y controles

`packaging/THIRD-PARTY-NOTICES.txt` **no cambia**: ya contiene PENDING y
not legal clearance. Se conserva como borrador, no como avisos definitivos.
Su texto heredado sobre licencia pendiente no reabre MIT: la selección/publicación
propia está confirmada; la revisión del conjunto y su nueva materialización
de distribución siguen pendientes. No se cambia la copia fijada en la imagen.

El LICENSE público y un identificador MIT en el POM no sustituyen incorporar
copyright/texto exigible en la distribución. La imagen futura debe resolver
esa materialización con su revisión de avisos/payload, mediante diff aparte;
no se da por cumplida aquí ni se añade una copia a la imagen congelada.

| Comprobación de esta entrega | Resultado |
|---|---|
| Tres git apply --check, individualmente | PASS; no aplicados |
| XML de los diffs reconstruido en memoria | 6 documentos bien formados, 0 archivos de producto escritos |
| Enlaces y saneado de 15 archivos entregados | 0 enlaces locales rotos, 0 datos locales; alias público autorizado preservado |
| Resumen | 65 líneas; 16 filas y cuatro cotejos, destinos y límites explícitos |
| Imagen, Windows PowerShell 5.1 | 370/370 pins iguales, 0 extras; helper 202/202 y publicado 205/205; ninguna regeneración |
| Protección / auditoría de referencias | PASS; 181 aceptadas y 0 inesperadas |
| Diff de producto | 0 cambios; POM/csproj/workflow/avisos y artefactos permanecen intactos |

Los espacios únicos de líneas de contexto vacías de los .patch pertenecen
al formato unified diff y se conservan para que sigan siendo comprobables;
no son espacios añadidos al código del producto. No se ejecuta
helper/CTest/Java/MACE. Una comprobación documental no es regresión ni prueba física.

## Decisiones necesarias y parada

1. Aprobar o ajustar por separado los diffs MIT, Mono y JDK, incluyendo los
   trabajos pendientes declarados; ninguno está aplicado al producto.
2. Elegir si en el futuro se adopta DCO, con alcance/privacidad acordados;
   mientras tanto no es obligatorio.
3. Designar revisión cualificada y entorno futuro de pruebas cuando corresponda.
   **DETENERSE: no C, D, E, publicación, instalación o firma.**
