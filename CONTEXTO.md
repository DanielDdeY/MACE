# Contexto de MACE para otro desarrollador o agente

Estado de referencia: **26/11/2026, America/Lima**. Leer junto con
[METODO_ENTREGA.md](METODO_ENTREGA.md). Esta guía consolida decisiones existentes;
no autoriza nuevas descargas, builds, contactos, instalaciones o publicaciones.

## 1. Proyecto y objetivo actual

**MACE** es una aplicación de escritorio para Windows x64: muestra telemetría
de hardware, información de procesos y una Micro-HUD de escritorio. Incluye
acciones manuales sobre procesos. La superposición en videojuegos de pantalla
completa no forma parte del trabajo autorizado actual.

Repositorio: [DanielDdeY/MACE](https://github.com/DanielDdeY/MACE).
Rama de trabajo de esta entrega: **LHM_implementado**. No fusionar a main.

El objetivo es **dejar preparados y verificados el proyecto, sus dependencias,
avisos, procedimiento de construcción y política para que el usuario presente
la solicitud a SignPath y, si es aceptada, pueda firmar el código**. Preparar
la solicitud no equivale a enviarla ni a tener autorización de firma.

Estado del servicio: **solicitud/admisión NO VERIFICADAS; firma MACE no activa**.
Los builds de prueba se mantienen explícitamente sin firma de MACE. No prometer
aceptación de MIT, Inno Setup, PawnIO o de toda la distribución por SignPath.
Las firmas originales de terceros no acreditan permiso de redistribución.

## 2. Arquitectura que debe conservarse

| Parte | Función y límites |
|---|---|
| `app/` | Java, interfaz JavaFX/AtlantaFX y puente FFM hacia la DLL. Preview utiliza datos simulados y no acredita sensores físicos. |
| `native/` | DLL en C con ABI pública, procesos, telemetría y receptor del helper. Mantiene GetSystemTimes/ReadCpuUsagePercent, RAM de Windows, DXGI, NVML primaria en NVIDIA e IGCL para Intel. |
| `helper/` | Helper C# basado en LibreHardwareMonitorLib 0.9.6, protocolo privado, arneses e informes de migración. Publicación .NET self-contained: el payload incluye su runtime; no exigir el SDK al usuario final por inferencia. |
| PawnIO | Controlador usado por LHM para determinados sensores de bajo nivel. Su estado y la elevación afectan la disponibilidad de métricas; no vuelve válidas todas las lecturas por estar instalado. No instalar Mono en el equipo para resolver esta migración. |
| `packaging/` | Staging, manifiestos, verificadores y app-image. Incluye aplicación, DLL, helper/dependencias, setup PawnIO intacto y avisos. El instalador nuevo aún no está validado. |
| `docs/index.html` | Página de descargas preparada localmente. Preparar el archivo no demuestra que GitHub Pages esté actualizado. |

El transporte usa una tubería local con protecciones de identidad y acceso.
El Job Object contiene al helper; el cierre acotado, EOF, instancia única y
watchdog de 10 s deben mantenerse. `CP_Poll*` nunca espera al helper y
`CP_InitializeTelemetry()` no bloquea.

**Congelados:** DLL **1.5.0**, ABI **1.5**, protocolo privado **2**, helper
**1.1.1**, política de reintentos del 4A y estados aditivos **9 a 16**.
No cambiar tamaños/offsets: CPU 56, GPU 104, inventario 296 y legado 28 bytes.
No tratar todo cero como inválido; la validez depende del estado por métrica.

## 3. Decisiones que no hay que volver a pedir

- Código propio de MACE bajo **MIT**; el usuario acepta derivados cerrados.
  Titular público: **DanielDdeY**. LICENSE ya existe y no debe sustituirse ni
  cambiar de titular. Esto no relicencia dependencias.
- El usuario asume autor, revisor y aprobador; contacto público por **GitHub
  Issues**. Declara 2FA en GitHub/Gmail, sin auditoría técnica. No publicar el
  correo alternativo ni extraer identidad del historial Git. Compatibilidad
  de roles, cuenta y MFA efectivos en SignPath siguen NO VERIFICADOS.
- **DCO: no por ahora**. CONTRIBUTING y plantillas permanecen en la rama.
- **Inno Setup** se mantiene sujeto a consulta a SignPath; NSIS no se adoptó.
  Precedentes de otros proyectos son contexto, no aprobación del paquete MACE.
- **No publicar nuevas releases con binarios de terceros hasta cerrar la
  revisión legal cualificada**. No restringir por ello el código fuente MIT.
  THIRD-PARTY-NOTICES sigue siendo borrador, no un dictamen legal.
- El usuario confirmó que pasó a borrador v1.0.0, v1.1.0-rc3 y v1.1.0.
  Son pre-LHM y no base de la candidata; no se han recertificado sus binarios
  ni comprobado de nuevo el remoto. Algunas notas históricas aún dicen pendiente.
- Cuatro ensayos físicos Mono anteriores devolvieron **0**, según el usuario:
  Read, Read RequireCpu, Reconnect RequireCpu y CloseEarly. No transferir ese
  resultado a hardware/bytes nuevos ni confundir CloseEarly con CloseWarming.
- La primera candidata **EXCLUYE Mono**. CONSERVAR es únicamente alternativa
  ante fallo Intel/NVIDIA, sin cambio automático de variante. No recomendar
  otra vez una elección ya hecha.
- Línea Temurin **25 LTS**, build utilizada **25.0.5.1+2**; JavaFX **27.0.1.1
  en JAR**, AtlantaFX **2.0.1**, Mockito **5.20.0**, Byte Buddy **1.17.7**.
  JavaFX sigue sujeto a compatibilidad/UI; una alternativa exige aprobación.
- Reintento manual de sensores: diferido; no implementarlo ni proponerlo de
  nuevo en esta fase. Señal 3010 por registro volátil: aprobada en principio,
  sujeta a esquema/ACL/consumo y aprobación de implementación; no tocarla ahora.
- Dos laptops Intel/NVIDIA declaradas por el usuario **no autorizan actuar
  sobre ellas**, ni acreditan resultados de la combinación nueva.

## 4. Dónde quedó el trabajo

No confundir el **Paso C documental de 3.1c** con las etapas de cierre de deuda
3B o con los pasos generales de Fase 3. El Paso C documental fue entregado.
Las Etapas 1 y 2 del cierre fueron aceptadas; Etapa 3 aportó lotes verificados.
**Etapa 4 está aprobada para una candidata aislada, pero detenida por un
faltante offline. No está terminada. No avanzar a 3.2/3.3 ni a D/E del prompt
de firma sin la aprobación correspondiente.**

Rutas relativas a esta raíz; no recrearlas si ya existen:

| Ubicación / revisión | Contenido y estado |
|---|---|
| `packaging/dist/MACE` del checkout de trabajo | Referencia congelada: **370 archivos, 147 787 297 bytes**, hashes conservados al cierre de Etapa 4. No reemplazar. |
| `native/build/candidata-20261126-02/source` | Clone aislado, rama `candidate/etapa4-excluir-mono`, commit **0352f58**. Parcheado y parcialmente construido. No es la imagen de referencia. |
| `native/build/candidata-20261126-02/helper-publish-true` | Helper seleccionado, sin los tres binarios Mono. 202 entradas publicadas, 199 de runtime; 204 archivos totales con ambos manifiestos. |
| `native/build/candidata-20261126-02/helper-publish-false` | Comparación de propiedad: los mismos **204 archivos, 0 diferencias de nombres/hashes**. No repetir publicaciones sin causa nueva. |
| `native/build/candidata-20261126-02/native/dev` y `native/prod` | DLL y fixtures dev/prod compilados. Instrumentación externa de pruebas fuera de la DLL de producto. |
| `native/build/fase3-lote-cerrado-20261126-01` | 192 JAR y 28 nupkg del lote cerrado; caches e índices privados. No equivale a resolución completa de todos los plugins. |
| `native/build/fase3-etapa3-20261125-01` | Temurin y Maven 3.9.16 verificados y aislados. No sustituirlos por herramientas del PATH. |
| `native/build/candidata-20261126-02/evidence` | Resultados, snapshots/digests privados y diario saneado de procesos; ignorados, no versionados. |

El candidato aplica Mono, metadatos MIT, JDK/JavaFX/Mockito y
DisableTransitiveFrameworkReferenceDownloads **solo en esa copia**. No aplicar
de nuevo los parches ni duplicar los cambios del parche combinado. La propiedad
evita packs especulativos ajenos al grafo; la comparación publicada fue idéntica.
`NuGetAudit=false` es **solo CLI de restore offline**, nunca propiedad de producto.

Toolchain del helper: **SDK .NET 10.0.401**, fijado por global.json, y runtime
**10.0.12**. Nativo: CMake 3.28+ y MSVC/Windows SDK x64; la campaña usó el
generador Visual Studio 18 2026 instalado, no una instalación nueva. Java FFM
está finalizada desde Java 22; la candidata usa 25. La referencia previa usaba
Oracle JDK 22.0.2/JavaFX 22.0.2: no afirmar que esa imagen migró o que sus
componentes quedaron relicenciados por preparar la candidata.

Si se trabaja en otro clone/equipo, esas rutas ignoradas pueden no existir.
Informar qué falta y pedir autorización de transferencia/descarga; no reconstruir
con red, caché personal o insumos supuestamente equivalentes por defecto.

### Evidencia actual de la candidata

| Comprobación | Resultado y alcance |
|---|---|
| dependency:tree del POM parcheado / restore locked | Ambos **0**, sin conexión y caches aisladas |
| Compilación Java development | Recursos y test-compile **0**; no acredita ejecución/UI |
| Helper unitario | **105/105**, consola normal; sin helper físico |
| CTest dev / prod | **76/76 y 77/77**, consola normal, fixtures auditados |
| Java/JUnit | **0 casos ejecutados**: resolución Surefire falló con salida 1 |
| Procesos del arnés | 611 identidades registradas y 0 supervivientes al cierre; no significa 611 cierres forzados |
| Payload, protección, audit-retired de esta campaña | **NO VERIFICADOS**, no ejecutados tras el bloqueo |
| Imagen nueva, UI/AtlantaFX/HUD, admin y hardware nuevo | **NO VERIFICADOS**; la app-image candidata aún no existe |

**Bloqueo inmediato:** falta `commons-io:commons-io:jar:2.15.1` en la caché
aislada. Hay además 17 advertencias de POM ausente. No afirmar que un solo JAR
cerrará el grafo, cambiar Surefire, sustituir commons-io, omitir JUnit o usar
Maven online. Esperar autorización de un **suplemento cerrado** de metadatos
y después de binarios, con versiones/URLs/checksums. Los padres nuevos tampoco
se autorizan implícitamente. La última petición solo creó guías documentales.

Revisiones útiles: **053277e** (`mono-in`, checkpoint previo), **b138489**
(arnés/recetas), **3767f3d** (resultados y bloqueo). La reorganización de
`helper/PENDIENTE-FASE-3B.md` estaba sin commit al comenzar estas guías:
preservarla y no incluirla inadvertidamente en otro commit.

## 5. Reglas operativas del agente

1. Leer estado Git y documentos vigentes antes de actuar; preservar trabajo
   ajeno y cambios sin commit. Instrucciones explícitas posteriores del usuario
   prevalecen sobre propuestas anteriores. Un informe histórico no autoriza
   por sí solo una acción nueva. Si hay conflicto material sin resolver, explicarlo.
2. Solo documentación está autorizada por la petición de estas guías. El objetivo
   final no autoriza completar por cuenta propia toda la Fase 3. No añadir funciones.
3. **Red:** cualquier solicitud fuera de `repo.maven.apache.org` y
   `api.nuget.org`, incluida documentación y redirects, exige detenerse y
   preguntar. Tampoco basta estar en esos dominios: se requiere URL/tipo/lote
   autorizado. **Etapa 4 no permite descargas nuevas**; hay una parada vigente.
   No importar claves, pedir nuevas firmas o ejecutar revisión de vulnerabilidades:
   esas tareas solo tienen propuestas, no autorización de consultas.
4. No enviar solicitudes/archivos a SignPath, PawnIO, LHM u otros terceros;
   no crear cuentas, aceptar contratos, adquirir certificados o contactar a
   mantenedores. El usuario presenta las consultas. No push, PR, merge o publicación.
5. No instalar/ejecutar/desinstalar PawnIO, cambiar HVCI, forzar/tocar servicios,
   claves 3010, manifiestos o launchers. No elevar ni terminar procesos por cuenta
   propia. Pruebas administrativas, hardware, suspensión y cierres reales las
   ejecuta el usuario con instrucciones exactas.
6. La excepción de cierre es **solo para esta regresión CTest/Java de la candidata**:
   procesos simulados creados por la propia prueba, con PID **y fecha de creación**
   registrados, parentesco demostrado y token normal. Nunca selección por nombre,
   `taskkill /IM` o `Stop-Process -Name`. Antes de probar, comprobar que no haya
   MACE/Preview/Mace.Lhm.Helper abiertos; si los hay, detenerse sin tocarlos.
   No incluye helper real, homónimos ajenos, LHM, instaladores ni procesos del
   sistema. Registrar cierres por caso y comprobar supervivientes. Si no se
   satisface el criterio, detenerse; no extender la excepción a otras campañas.
7. No cambiar imagen de referencia, API/ABI/protocolo/versiones congeladas,
   proveedores protegidos, contención, watchdog o política 4A. Cambios de producto
   fuera de los parches aislados aprobados necesitan diff y aprobación aparte.
8. Resolver sin conexión y con caches aisladas. Verificar herramientas reales;
   el CMake de MinGW en PATH no reconoció VS 2026. El generador y toolchain deben
   corresponder al equipo. No instalar herramientas ni cambiar JAVA_HOME/PATH
   globales para sortear fallos. Usar **Windows PowerShell 5.1** para manifiestos
   y payload; el verificador local de runtime pack usa pwsh/.NET 10, por separado.
9. Producto separado del repositorio: no llevar tests, PDB, createdump, scripts,
   docs internas, agentes de auditoría, logs, artifacts o los 183 JAR de
   pruebas/plugins al instalador. Verificar nueve JAR runtime y ausencia de
   duplicación de clases JavaFX en la imagen real cuando exista; hoy sigue NV.
10. No commit de logs, índices privados, caches, `diagnostico-admin.txt`,
    `diagnostico-normal.txt`, `prueba-cpu.txt`, `mono-read-normal.txt` ni equivalentes.
    No publicar perfiles, usuario/equipo, correos, SID, secretos o hashes completos
    en informes compartibles. Modelos CPU/GPU y mediciones están autorizados.
    Los pins/manifiestos técnicos requieren hashes completos: **no borrarlos**
    para sanear informes; mantener la evidencia operativa privada/ignorada.
11. En el directorio superior del proyecto ChatGPT, **`sources/` es solo lectura**:
    no editar, renombrar, mover ni borrar archivos sincronizados.

## 6. Qué falta para estar listo para SignPath

Primero resolver el bloqueo offline con aprobación; reanudar desde Java sobre
la misma candidata sin repetir builds/suites aprobadas sin causa. Después
completar imagen, conteos/hashes/pins, regresión y pruebas del usuario. No
convertir esta secuencia recomendada en permiso para ejecutarla ahora.

Reauditar el **payload real nuevo**, sin repetir filas históricas reemplazadas.
Prioridades: setup/driver y doce módulos PawnIO (incluida discrepancia IsaBridgeEC),
cabecera IGCL, bibliotecas del sistema/controlador, CRT/VC/UCRT nuevos y motor/
desinstalador Inno. Mono fuera del payload no resuelve su procedencia histórica.
Avisos finales, privacidad dinámica y revisión cualificada siguen pendientes.

CI actual no acredita el pipeline completo del helper/manifiesto/producción;
no basta con checks básicos verdes. Resolver también las condiciones de admisión
de SignPath, terceros sin refirma, roles/MFA, procedencia y aprobación manual.
No afirmar que setup firmado implica desinstalador firmado.

La cadena prevista, **cuando exista autorización y servicio habilitado**, es:
helper propio final firmado → manifiesto de bytes finales → DLL de producción
con ese pin y firmada → pin de DLL final/launchers y UAC → imagen verificada →
instalador/desinstalador según admisión y verificación final. Cambiar bytes tras
un pin obliga a reconstruir sus consumidores. Terceros, incluido PawnIO intacto,
no se refirman con la suscripción del proyecto por inferencia.

## 7. Lectura mínima para continuar

1. [Método de entrega](METODO_ENTREGA.md) y [estado 3B](helper/PENDIENTE-FASE-3B.md).
   Las filas PENDIENTE de este último conservan texto histórico: leer sus notas
   de actualización, no interpretar sus antiguos «no aplicado» como estado actual.
2. [Resultados Etapa 4](helper/FASE3-ETAPA4-RESULTADOS.md),
   [bloqueo Maven](helper/FASE3-ETAPA4-BLOQUEO-MAVEN.md) y
   [validación pendiente](helper/FASE3-CANDIDATA-VALIDACION-USUARIO.md).
3. [Lote cerrado](helper/FASE3-LOTE-CERRADO-VERIFICACION.md),
   [restore/propiedad](helper/FASE3-NU1101-PROPIEDAD.md) y
   [firmas/vulnerabilidades propuestas](helper/FASE3-FIRMAS-VULNERABILIDADES-PROPUESTA.md).
4. [Política de firma](CODE_SIGNING_POLICY.md),
   [preguntas no enviadas](helper/FASE3-PREGUNTAS-SIGNPATH.md),
   [dossier cualificado](helper/FASE3-DOSSIER-REVISION.md) y
   [guía sin revisor](helper/FASE3-GUIA-REVISION-SIN-REVISOR.md).

Los documentos fechados conservan el estado de su entrega; algunas propuestas
quedaron superadas. No reescribirlos silenciosamente ni extrapolar sus aprobados.
Para retomar, confirmar qué autoriza el último mensaje y entregar primero la
acción concreta pendiente de aprobación. No prometer una solicitud lista hoy.
