# Diffs de propuesta — no aplicados

**Estado actualizado 2026-11-25:** JDK-JAVAFX.patch es histórico/superado;
**no aplicar**, JavaFX no elegida. Temurin 25 aprobado solo como línea.
MONO-EXCLUSION.patch se ensayó únicamente en copia autorizada; el nuevo
[MONO-EXPERIMENTO-COMPLETO.patch](MONO-EXPERIMENTO-COMPLETO.patch) incluye los
dos locks y se comprueba con --check --unidiff-zero. Integración pendiente.
Los comandos y preguntas anteriores se conservan como registro histórico;
éxito textual no autoriza ejecutar ninguno sin --check sobre producto.

Fecha: 2026-11-25. Base de archivos de producto: 9a0ee8f.
Estos archivos .patch son documentación revisable, no configuración activa.

| Diff | Alcance y gate |
|---|---|
| METADATOS-LICENCIA.patch | POM y tres csproj propios; requiere aprobación previa porque altera potencialmente artefactos/pins |
| MONO-EXCLUSION.patch | ExcludeAssets del helper; faltan lock/deps y validación tras restore autorizado |
| JDK-JAVAFX.patch | Mínimo POM 25/27.0.1.1; faltan build descargable, validación Maven y combinación de tests compatible |

Se verifican individualmente, sin modificar el producto:

```powershell
git apply --check --ignore-space-change helper/pendientes/METADATOS-LICENCIA.patch
git apply --check --ignore-space-change helper/pendientes/MONO-EXCLUSION.patch
git apply --check --ignore-space-change helper/pendientes/JDK-JAVAFX.patch
```

La tolerancia de contexto permite leer copias LF/CRLF de los .patch sin cambiar
los finales de línea del proyecto. Las líneas de contexto vacías tienen un
espacio propio del formato: no se elimina como si fuese código sobrante.
Se comprobaron también sin esa opción sobre las copias LF de esta entrega.

Éxito textual no prueba compilación, funcionamiento, compatibilidad legal ni
regeneración de hashes correcta. No ejecutar una aplicación en lote de estos
diffs como consecuencia de aprobar la documentación. Las decisiones y límites
están en las propuestas enlazadas desde FASE3-B-PARCIAL.md.
