# Puntos 3, 4 y 5 — Verificación local y propuestas sin ejecutar

Fecha: **2026-11-26**, America/Lima. Ninguna consulta remota, clave importada,
contacto, instalación o auditoría de vulnerabilidades ejecutada en esta entrega.
Los dominios siguientes son **propuestas para aprobación futura**, no permiso
para solicitarlos. Incluso documentación fuera de los dos dominios autorizados
requiere detenerse y preguntar antes.

## 3. Runtime pack NETCore 10.0.12: evidencia local adicional

Se releyó el nupkg aprobado, comprobando su SHA-256 contra el índice privado
del lote y usando NuGet.Packaging del SDK existente:

- Firma de tipo **Author**, comprobación matemática CMS e integridad de archivo
  mediante ValidateIntegrityAsync: **aprobadas**. No se añadió contentHash al lock.
- Cadena X509 con descargas de certificados deshabilitadas, ExtraStore limitado
  a certificados del paquete y revocación **offline**: **NO VERIFICADA**.
  Estados: NotTimeValid, RevocationStatusUnknown y OfflineRevocation. La prueba
  local no valida la cadena a la fecha del timestamp; no permite declarar
  inválida la firma histórica ni afirmar confianza completa del editor.
- Comparación independiente con la instalación local de
  shared/Microsoft.NETCore.App/10.0.12: **188 archivos coinciden en SHA-256**,
  **0 difieren**, **1 no tiene contraparte** en ese directorio compartido.
  Acredita las copias coincidentes, no todos los bytes ZIP ni su procedencia.
- Checksum de paquete completo publicado en una fuente independiente del editor:
  **NO VERIFICADO**. No se hizo una nueva descarga de metadatos durante Etapa 4.

Script reproducible de lectura local:
`auditoria-licencias/verificar-runtimepack-local.ps1`; resultado/digests privados
en el directorio ignorado del lote. Sin cambios de almacenes de certificados,
importación de claves o activación de servicios. La ausencia del hash en locks
sigue explícita; estas comprobaciones no lo sustituyen por un dato inventado.

Ese script usa **pwsh con .NET 10**, como en la comprobación local realizada;
no Windows PowerShell 5.1, reservado aquí para manifiestos y payload. Ejemplo
de repetición solo de lectura, desde la raíz del repositorio y sin administrador:

```powershell
pwsh.exe -NoProfile -File .\helper\auditoria-licencias\verificar-runtimepack-local.ps1 -OutputFile .\native\build\candidata-20261126-02\evidence\runtimepack-user-local.json
```

No se solicita repetirlo durante la parada actual de la build; el archivo de
salida contiene solo el resumen local y queda fuera de commits.

## 4. Propuesta de autenticación de claves — NO EJECUTADA

### AtlantaFX 2.0.1

Huella **declarada por el .asc**, no autenticada:
`12ED17B48803C23BE8B1DE9179DD1EB96702CC23`.

Fuentes exactas propuestas:

1. Repositorio y tag del editor: dominio **github.com**,
   `https://github.com/mkpaz/atlantafx/tree/v2.0.1`.
2. Inventario del tag, si el usuario autoriza lectura por API: **api.github.com**,
   `https://api.github.com/repos/mkpaz/atlantafx/contents?ref=v2.0.1`.
3. Claves públicas asociadas a la cuenta del mantenedor: **api.github.com**,
   `https://api.github.com/users/mkpaz/gpg_keys`.

Antes de solicitar cualquiera, aprobar dominio/URL. Buscar anuncio/archivo de
clave o huella controlado por el editor y con contexto histórico de la release;
comprobar el vínculo con el namespace Maven y el repo. Una clave en GitHub por
sí sola no acredita que firmara esa release. Si se obtiene un archivo público,
usar GnuPG **--show-keys --with-colons --fingerprint**, sin importarlo, y comparar
la huella completa exacta con la declarada. Revisar validez, subclave firmante,
fecha y revocación; si falta un vínculo autenticado, **NO VERIFICADO**, sin import.

### JavaFX 27.0.1.1

Los ocho .asc locales declaran key ID **6A9FBE152D4C64D1**; todavía no hay huella
completa autenticada. No se confunde un key ID con una huella única.

Fuentes exactas propuestas para localizar la publicación oficial de la clave:

- **gluonhq.com**: `https://gluonhq.com/products/javafx/`.
- **openjfx.io**: `https://openjfx.io/` y **github.com**:
  `https://github.com/openjdk/jfx`, para acreditar el vínculo de la distribución
  Maven con sus responsables. El POM local de OpenJFX declara ese repositorio;
  esto no atribuye automáticamente una clave de Gluon a OpenJDK ni viceversa.
- Solo si se autoriza y no hay una clave oficial enlazada: **keyserver.ubuntu.com**,
  `https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x6A9FBE152D4C64D1`,
  como **fuente no confiable de candidatos**, nunca como prueba de identidad.

No se supone que una página contenga una clave ni se inventa su ubicación.
Primero localizar un anuncio/clave con aprobación; después obtener la huella
completa con show-keys y contrastar con una fuente oficial independiente de
Central. Comprobar que la subclave firmante corresponde al key ID del .asc,
al emisor autenticado y a las fechas de la release. Si no se acredita, mantener
NO VERIFICADO. No activar recuperación automática ni importar claves ahora.
No se pedirían firmas adicionales: solo los nueve .asc de runtime ya obtenidos.

Estas URLs no se consultaron en este paso: existencia exacta de sus contenidos,
huellas publicadas y asociación histórica **NO VERIFICADAS**. La falta de
verificación de firmas no bloquea técnicamente Etapa 4 según el usuario;
no equivale a aprobación de redistribución o de firma de MACE.

## 5. Propuesta de vulnerabilidades — NO EJECUTADA

| Conjunto | Fuente y dominio exactos propuestos | Procedimiento separado y límites |
|---|---|---|
| NuGet efectivamente restaurados, incluidos transitivos | Índice oficial **api.nuget.org**: `https://api.nuget.org/v3/index.json`; recurso VulnerabilityInfo declarado por ese índice, candidato `https://api.nuget.org/v3/vulnerabilities/index.json` | Tras aprobación, descargar únicamente metadatos de vulnerabilidades autorizados y cruzarlos localmente con IDs/versiones del restore/locks. No cambiar paquetes, locks ni política del producto. NuGetAudit=false del restore offline no prueba ausencia de vulnerabilidades. |
| Nueve JAR runtime, JavaFX 27.0.1.1 y AtlantaFX 2.0.1 | OSV, **api.osv.dev**, `https://api.osv.dev/v1/querybatch` | Proponer consulta de solo GAV/versiones públicas Maven, sin archivos, rutas, datos de MACE o credenciales; requiere aprobación explícita antes de enviar esa consulta. Alternativa: copia local de una base aprobada y cruce sin transmitir inventario. |
| Cotejo humano de hallazgos | **github.com**, `https://github.com/advisories`; **nvd.nist.gov**, `https://nvd.nist.gov/vuln/search` | Solo después de autorizar esas lecturas; confirmar versiones afectadas/corregidas, clasificación y aplicación al binario concreto. No convertir una coincidencia textual o CPE genérico en vulnerabilidad confirmada. |

Existencia/respuesta actual de endpoints y cobertura de cada ecosistema:
**NO VERIFICADO por consulta en esta entrega**. No se promete que una fuente
cubra todos los componentes, nativos embebidos o avisos sin identificador.
Entregar después fuente/fecha/ID del aviso, paquete/versión, condición afectada,
versión corregida, aplicabilidad y resultado no concluyente si procede.
Resultados vacíos no significan software seguro; revisión legal y firma son
comprobaciones distintas. No escanear los 183 JAR de tests/plugins como si
viajaran en el producto; una revisión de herramientas de build sería otro alcance.
