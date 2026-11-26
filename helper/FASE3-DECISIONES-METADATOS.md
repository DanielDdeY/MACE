# Decisiones vigentes tras aceptación de las Etapas 1 y 2

Fecha: **2026-11-25**, America/Lima. Rama `LHM_implementado`.
Registro del último mensaje del usuario; no sustituye los resultados históricos
ni anticipa la consolidación final de Etapa 7.

1. El usuario **acepta las Etapas 1 y 2** y confirma en su equipo código **0**
   para Read, Read RequireCpu, Reconnect RequireCpu y CloseEarly. Se registra
   como **VERIFICADO POR EL USUARIO**; sin atribuir tokens, modelos, lecturas,
   mediciones o logs nuevos que no haya proporcionado. CloseWarming sigue aparte.
2. **La primera candidata EXCLUYE Mono**. Esta decisión reemplaza la instrucción
   anterior de construir primero CONSERVAR. El parche autorizado es
   `pendientes/MONO-EXPERIMENTO-COMPLETO.patch`, solo para el directorio aislado.
   No se aplica todavía: la Etapa 4 continúa detenida hasta aprobación del lote
   binario. No se aplica también el parche combinado EXCLUIR encima, porque
   duplicaría la modificación Mono. Los metadatos MIT/Java comunes requieren
   la composición prevista, sin duplicar cambios y sin tocar la referencia.
3. **CONSERVAR** queda como alternativa si falla Intel o NVIDIA; no se cambia
   de variante automáticamente ni se interviene en las laptops. Su disponibilidad
   declarada **no autoriza acciones del agente** sobre ellas.
4. Se evita reauditar las filas históricas que sustituirá la migración:
   Oracle JDK 22, OpenJFX 22, sus VC/UCRT y los tres binarios Mono excluidos.
   La revisión posterior se hará sobre el **payload real** de candidata.
   Permanecen prioritarios setup/driver/módulos PawnIO, cabecera IGCL,
   VC/UCRT/CRT de la nueva build y motor/desinstalador Inno. No se les da
   conformidad jurídica por haber decidido una migración.
5. Se actualiza la consulta PawnIO y se prepara otra a LHM sobre los doce
   módulos. **Ninguna enviada**; el usuario decide si las publica.
6. Ampliación de Etapa 3: solo POM, maven-metadata*.xml y checksums desde
   `repo.maven.apache.org`, versiones fijas y caché aislada; sin JAR/NuGet,
   Maven/plugins, builds, caché del usuario o cambios de JAVA_HOME/PATH.
   El siguiente lote binario requiere aprobación expresa.
7. GnuPG existente puede utilizarse sin instalar; se encontró el de Git for
   Windows y su versión se comprobó. No hay firmas locales de los binarios.
   El permiso estricto de tipos de archivo **no incluye** descargar `.asc`,
   `.sig` o claves desde otros dominios: no se hicieron esas solicitudes.
8. Se mantienen DLL 1.5.0, ABI 1.5, protocolo 2, helper 1.1.1, estados y 4A,
   imagen congelada, avisos borrador, sin servicios/PawnIO/HVCI/UAC, push/PR/merge
   ni contactos. No avanzar a Etapa 4, 3.2 o 3.3.

## Punto de retorno solicitado

Antes de los demás cambios se creó **053277e**, mensaje exacto **mono-in**,
con padre **abec236**. Es un commit vacío deliberado: el árbol seguido estaba
limpio y los dos archivos sin seguimiento preexistentes se dejaron fuera.
No contiene logs, TXT locales ni descargas. El punto preserva el producto
con Mono previo a cualquier aplicación futura del parche aislado.

Consulta sin modificar ni descartar trabajo actual:

```powershell
git show --stat 053277e
```

Informe de metadatos y límites: [FASE3-ETAPA3-CIERRE-METADATOS.md](FASE3-ETAPA3-CIERRE-METADATOS.md).
