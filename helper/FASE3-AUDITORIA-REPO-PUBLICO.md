# C — Auditoría del repositorio ya publicado

Fecha: **2026-11-25**. Solo lectura de GitHub y del clon local; sin push,
merge, borrado, cambios de historia o contacto. Base local **d563cbc**.
Regla 5 **confirmada por el usuario**: no publicar releases con binarios de
terceros hasta cerrar revisión legal; no afecta al código fuente público.
No implica retirar automáticamente publicaciones anteriores.

## Alcance reproducible

Conector GitHub GET: 21 ramas, main **d2698a0**, LHM_implementado **9de86ea**.
Para cubrir más que el checkout se creó un mirror nuevo e ignorado en
native/build/fase3-tareas-abcd/public-repo.git. Operación remota de lectura;
ninguna referencia del checkout original se actualizó. El primer intento de
red aislado falló; el segundo, autorizado fuera del aislamiento, terminó bien.
No fue elevación Windows ni ejecución de producto.

| Cobertura medida | Público, mirror | Checkout local, todas las refs |
|---|---:|---:|
| Commits alcanzables | 132 | 80 |
| Referencias | 61 | 5 |
| Blobs alcanzables únicos examinados | 1043 | 957 |
| Versiones binarias por extensión o tamaño >= 1 MiB | **0** | **0** |
| Versiones de texto con candidatos de los patrones | 61 | 49 |
| Pares nombre/correo de autor | 11 | 8 |
| Tres TXT prohibidos, en todo el historial alcanzable | **0** | **0** |

61 refs públicas incluyen ramas, etiquetas y refs adicionales que el servidor
anuncia al mirror; no son 61 ramas. Se lee cada blob alcanzable, árboles de
todas las refs, autoría, committers y mensajes. No cubre objetos inaccesibles,
reflogs remotos, forks, cachés de terceros ni secretos fuera del repositorio.
Patrones no sustituyen revisión humana ni un análisis completo de secretos.
No se compila ni ejecuta MACE para esta auditoría.

Script de solo lectura: helper/auditoria-licencias/auditar-repositorio.py.
Escribe JSON saneado: rutas relativas, categorías y contadores, sin valores
personales, cuerpos de blobs, nombres/correos ni hashes completos.

```powershell
python helper/auditoria-licencias/auditar-repositorio.py --repo . --out native/build/fase3-tareas-abcd/auditoria-local.json
python helper/auditoria-licencias/auditar-repositorio.py --repo native/build/fase3-tareas-abcd/public-repo.git --out native/build/fase3-tareas-abcd/auditoria-publica.json
```

Para actualizar evidencia pública en otra fecha, crear **otra carpeta nueva**
con git clone --mirror de la URL pública; no usar fetch ni checkout sobre la
copia original. La instantánea de hoy conserva la fecha de este informe.

## Hallazgo C-1: Git limpio de binarios no equivale a releases sin binarios

No se encontraron DLL/EXE/JAR/ZIP/PDB/SYS/MSI/MSIX/7z/NuGet/JMOD/LIB/SO/A
versionados, ni blobs >=1 MiB en el historial alcanzable inspeccionado.
El instalador PawnIO y SDK binarios no aparecen como blobs Git alcanzables.
Sí hay **encabezados/fuentes de IGCL**: no confundir ausencia de binarios con
ausencia de material de terceros ni con aprobación de su licencia.

La API pública de [releases](https://github.com/DanielDdeY/MACE/releases)
devuelve **3 releases, 13 adjuntos**; **7 adjuntos binarios**:

| Release | Binarios listados | Tamaños en bytes |
|---|---|---|
| v1.1.0 | MACE-portable.zip; MACE-Setup.exe | 86162676; 85507072 |
| v1.1.0-rc3 | MACE-portable.zip; MACE-Setup.exe | 86162673; 85507072 |
| v1.0.0 | ZIP nativo; MACE-portable.zip; MACE-Setup.exe | 247427; 44105324; 45551104 |

No se descargaron ni abrieron esos binarios. **NO VERIFICADO**: si contienen
PawnIO, qué versiones/terceros incluyen, sus avisos, firmas y legalidad de
redistribución. Los otros 6 adjuntos son informes y sumas; tampoco se ha
auditado aquí su cuerpo para datos locales. Los nombres originales que
incluyen sellos/hashes se abrevian deliberadamente.

Recomendación: usuario decide revisión/restricción temporal de **los adjuntos
ya públicos**; inventariar su contenido y avisos antes de mantenerlos como
release recomendada. No se retiran, renombran ni sustituyen por cuenta del
agente. La regla confirmada se aplica a nuevas publicaciones; este hallazgo
requiere decisión explícita sobre las anteriores.

## Hallazgo C-2: identidad de commits ya pública

Hay **11 pares de autor nombre/correo** y **9 pares de committer** públicos.
De los autores, 10 pares usan correo distinto del formato GitHub noreply
(127 commits); 1 par usa ese formato (5 commits). No se reproducen identidades
ni se infiere que todas pertenezcan al usuario; alias distintos no prueban
personas distintas. La autoría es pública en Git, aunque se oculte en README.

Propuesta: revisar voluntariamente identidad pública para commits futuros,
correo noreply de GitHub y permisos de colaboradores. Cambiar configuración
futura **no borra el historial**. Una reescritura exigiría decisión y coordinación;
no se recomienda ejecutarla automáticamente ni se promete eliminar copias.
No se modificó git config ni identidades existentes.

## Hallazgo C-3: datos locales, plantillas y hardware

61 versiones de blobs disparan al menos un patrón: **no son 61 filtraciones**.
Se revisaron los candidatos de perfiles: corresponden a rutas de ejemplo de
mocks y guía histórica, no se acreditó una ruta de perfil personal real.
SID de dominio/equipo literal: **0**. Campos máquina explícitos: 0.
Mensajes de commits: 0 coincidencias de los patrones. Correos encontrados en
textos corresponden a avisos de terceros; no suprimir atribuciones exigibles.
Quedan NO VERIFICADOS datos locales ofuscados, formatos no cubiertos y los
adjuntos de releases, issues, comentarios y logs de Actions fuera de Git.

Sí se publican **modelos/características de hardware y cifras de ensayos**:
RESULTADOS-1A, RESULTADOS-1B, RESULTADOS-1C y COBERTURA-AMD-POTENCIA describen
mediciones físicas AMD; native/docs/HARDWARE_COMPATIBILITY conserva matriz
y límites. Otros modelos en mocks/tests son simulados. No se atribuye ese
hardware a las dos computadoras Intel actuales del usuario ni a otras personas.
También se publican límites, estados, PIDs puntuales y resultados de fases;
que no haya SID no vuelve anónima toda esa combinación de información.
Recomendación: decidir qué detalle de ensayos conservar públicamente y
sanear nuevas evidencias antes de subirlas. No se alteran informes históricos.

## Hallazgo C-4: documentación pública y alineación de ramas

main y LHM_implementado públicos contienen los **25 informes helper** listados
abajo; el contenido de cada rama puede divergir. La auditoría nueva y B parcial
local no se consideran publicados por el mero hecho de estar en el checkout.

| Referencia examinada | Archivos | LICENSE raíz | CONTRIBUTING.md raíz |
|---|---:|---|---|
| main público d2698a0 | 420 | **Sí, MIT** | No |
| LHM_implementado público 9de86ea | 397 | No | No |
| LHM_implementado local d563cbc | 418 | No | **Sí** |

MIT está confirmado, titular verificado **por el usuario**. La ausencia en una
rama no reabre esa decisión. Propuesta sin ejecutar: revisar diff entre main
y rama de trabajo, integrar solo LICENSE aprobado en la rama de trabajo y
proponer CONTRIBUTING/plantillas revisados a main mediante PR independiente,
resolviendo cambios concurrentes. No fusionar toda la rama para copiar dos
archivos; no crear otra licencia o atribuir titularidad ajena. No se hizo pull,
cherry-pick, merge, push ni PR en esta entrega.

Los tres TXT locales no tienen seguimiento en ninguna ref/historial revisado.
La exclusión local no es garantía para otros clones: comprobar git status y
archivos preparados antes de cada commit; no publicarlos ni sus contenidos.

### Informes helper publicados (main, también nombres presentes en LHM)

- `helper/FASE2-CIERRE.md`
- `helper/FASE2-DIAGNOSTICO-5.1.md`
- `helper/FASE2-H-5.0-JAVA.md`
- `helper/FASE2-INVENTARIO-ESCRITURAS.md`
- `helper/FASE2-PASO0.md`
- `helper/FASE2-PASO1A.md`
- `helper/FASE2-PASO1B.md`
- `helper/FASE2-PASO2.md`
- `helper/FASE2-PASO3.md`
- `helper/FASE2-PASO4A.md`
- `helper/FASE2-PASO4B.md`
- `helper/FASE2-PASO5-PRUEBAS.md`
- `helper/FASE2-PASO5.0-AUDITORIA.md`
- `helper/FASE2-PASO5.0.md`
- `helper/FASE2-PASO5.1.md`
- `helper/FASE2-PENDIENTES.md`
- `helper/FASE3-DISENO-ADENDA.md`
- `helper/FASE3-DISENO.md`
- `helper/FASE3-PASO0.md`
- `helper/RESULTADOS-1A.md`
- `helper/RESULTADOS-1B.md`
- `helper/RESULTADOS-1C.md`
- `helper/RESULTADOS-1D-FINAL.md`
- `helper/RESULTADOS-1D-PASO1.md`
- `helper/RESULTADOS-1D-PASO2.md`

## Fuentes públicas y límites

Consultadas por GET el 25/11/2026: [repositorio](https://github.com/DanielDdeY/MACE),
[ramas](https://api.github.com/repos/DanielDdeY/MACE/branches?per_page=100),
[releases/metadatos](https://api.github.com/repos/DanielDdeY/MACE/releases?per_page=100),
[LICENSE main](https://github.com/DanielDdeY/MACE/blob/main/LICENSE).
Mirror conserva exactamente los objetos alcanzables anunciados en la lectura;
no implica una auditoría permanente ni la descarga de assets de releases.

**Decisiones:** tratamiento de releases existentes; conservación de detalle
hardware público; alineación documental por PR futuro. No se modifica nada
remoto ni se ejecuta ninguna de estas propuestas. No es dictamen legal.
