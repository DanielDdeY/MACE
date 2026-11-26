# 3.1c — Paso C: política de firma, README y página de descargas

Fecha: **2026-11-25**, America/Lima. Base **24bbee4**, rama
`LHM_implementado`; árbol seguido limpio al comenzar. Se preservaron los dos
archivos locales sin seguimiento, sin incorporarlos ni reproducir su contenido.
Un cambio lógico documental, sin push, merge, publicación o solicitud externa.

## Autorización y fuentes

El usuario aporta README.md, autoriza redactar CODE_SIGNING desde cero,
confirma **docs/index.html** y aprueba las tres sugerencias previas: estado
honesto/atribución condicional, alcance de artefactos y marcas NO VERIFICADO.
No se necesita volver a pedir MIT, titular, roles, contacto, DCO o JavaFX.
Esto autoriza **C documental**, no D/E del prompt de firma, 3.2/3.3 ni builds.

- README adjunto leído y utilizado como borrador; no se modifica el adjunto.
- docs/index.html no existía en esta rama. Se leyó por GET la versión de
  **main d2698a0** y se preparó una copia adaptada local, conservando la base
  de estilos/estructura. No se hizo merge ni se copiaron imágenes/binarios.
- Se contrastaron POM, global.json/Directory.Build.props, scripts de
  publicación, CMake, CI, preferencias, caja negra, tubería y decisiones vigentes.
- Documentación pública consultada por lectura el 25/11:
  [condiciones SignPath](https://signpath.org/terms) y
  [privacidad GitHub](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
  No formulario, contacto, envío de artefactos o aceptación de contratos.

## Cambios por archivo

| Archivo | Resultado |
|---|---|
| README.md | Adaptación del borrador: capacidades condicionadas, actual/propuesto, estado de releases, enlace de política, privacidad y build en clon limpio NV; CI/CD descritos según la rama, sin comandos de push/tag |
| CODE_SIGNING_POLICY.md | Política desde cero: atribución condicional, roles declarados, MFA pendiente, aprobación manual, lista cerrada prevista de siete PE, terceros excluidos, desinstalador NV y cadena 3.6 |
| docs/index.html | Página local con encabezado/enlace de política, privacidad y estado de descargas; no entrega automáticamente una release pre-LHM como candidata nueva |
| PENDIENTE-FASE-3B.md | Actualización aditiva: dudas resueltas por usuario y nueva discrepancia estática CI; conserva deuda y contexto anteriores |
| FASE3-PASOC.md | Esta entrega, comprobaciones, límites y parada |

## Correcciones respecto del borrador y la página base

1. **Instalador:** el borrador prometía todo listo/sin pasos extra y CD de
   instalador operativo. La nueva implementación Inno y su encadenamiento
   PawnIO no están acreditados. El empaquetado de referencia incluye setup
   intacto; no prueba instalación automática ni sensores universales.
2. **CI:** .github/workflows/build-and-test.yml local contiene dos jobs de base,
   no los seis pasos del borrador. CMake exige manifiesto al configurar prod;
   el workflow no lo prepara/proporciona. Es una discrepancia por inspección,
   **no un fallo de una ejecución observada aquí**. release-installer.yml no
   existe en este checkout. No se modificó/ejecutó ningún workflow.
3. **Build:** no se copiaron dos comandos aislados como receta autocontenida.
   README enlaza el plan completo futuro **NO VERIFICADO en clon limpio**.
   No se compila para retirar esa marca sin aprobación de operaciones/insumos.
   Java 22/JavaFX 22.0.2 actuales se distinguen de Temurin 25/JavaFX 27 propuestos.
4. **Descargas:** se retiró de la copia local el selector JavaScript de última
   release y se dejó enlace explícito al historial. No se afirma que el
   usuario ya haya marcado las releases como borrador; no se inventarían sus
   binarios ni se cambia el remoto. No se implementa otro pipeline de release.
5. **Página:** se conservó la presentación general; se retiraron peticiones de
   fuentes externas y una captura cuyo archivo no está en esta rama, sin
   descargar imágenes. La gráfica inline se identifica como ilustración,
   no como medición física de la candidata. No se atribuyen pruebas al usuario.
6. **Sensores:** se elimina la afirmación genérica de que todas las lecturas
   Radeon requieren PawnIO/elevación y la promesa de lectura disponible en
   todo equipo. La DLL conserva su semántica; solo cambia la documentación.
7. **Privacidad:** la web no consulta automáticamente releases ni carga fuentes
   externas; visitar GitHub/alojamiento es distinto de ejecutar MACE. No se
   promete ausencia universal de red del producto/dependencias/sistema.

La página pública aún no está actualizada por esta entrega. El enlace a la
política en **main** necesita integrar/publicar los archivos por el usuario;
no se presenta como enlace ya desplegado ni se cambia GitHub Pages.

## Comprobaciones efectuadas, sin ejecutar producto

| Comprobación | Resultado y límite |
|---|---|
| Revisión de fuentes propias Java/C/C# | **164 archivos**, 0 coincidencias del patrón de clientes HTTP/socket; no auditoría exhaustiva ni captura de terceros |
| Escrituras/registro | Comprobados escritor de caja negra, sink persistente, Java Preferences del HUD y pipe local del helper; inventario previo conserva límites de backend/terceros; no se consultó/escribió registro del host |
| Estructura HTML e IDs/anclas locales | Sin errores detectados por HTMLParser/check estructural; no certificación W3C ni prueba visual en navegador |
| Política visible en README y página | Encabezado requerido y destino local preparado; atribución solo condicionada en la política |
| Marcadores sin rellenar | 0 en los tres documentos públicos. El registro previo conserva una mención instructiva al marcador de verificación; no es un campo pendiente |
| Enlaces Markdown locales / saneado | 0 destinos locales inexistentes y 0 datos locales/correos/SID/hashes completos detectados; alias público autorizado preservado |
| Recursos automáticos de la página | 0 scripts y 0 etiquetas de carga de recursos externos; enlaces a sitios externos solo de navegación |
| Imagen de referencia | **370/370 pins coincidentes, 0 alterados, 0 extras**; no regenerada |
| check-protected-telemetry | PASS, código 0; bloques/ABI/proveedores protegidos sin cambios |
| audit-retired-providers | **181 aceptadas, 0 inesperadas**, código 0; informe ignorado fuera del producto |
| Compilación/regresión/firmas reales | **0 builds y 0 ejecuciones de producto**; helper/CTest/Java/payload no se repiten por documentación |

Los controles estáticos y de hashes son evidencia limitada. No se transforman
en resultados físicos ni en acreditación jurídica. Las salidas saneadas de
comprobación quedan bajo native/build/fase3-paso-c y no se versionan.
El escaneo no sustituye una auditoría completa de seguridad/privacidad.

## Lista de NO VERIFICADO y deuda que sigue abierta

- Admisión de MACE, solicitud presentada, cuenta/MFA y configuración SignPath,
  compatibilidad de roles, PE generados, metadatos, Inno y desinstalador.
- Firma real, sellos de tiempo y procedencia efectiva de un pipeline final.
  Los siete PE son **previstos**; no se afirma que ninguno esté firmado hoy.
- Derechos/fuentes/avisos definitivos y revisión cualificada de terceros.
  THIRD-PARTY-NOTICES no se modifica ni finaliza. Esto no es asesoría legal.
- Clon limpio, ruta con espacios y comandos de construcción completos probados;
  descargas/build concreta/candidata y nueva combinación Java/JavaFX/Mockito.
- Cuatro ensayos Mono código 2: no resueltos; ninguna recomendación de variante
  ni integración de exclusión. Se mantiene su gate de físicas código 0.
- Red/escrituras indirectas del conjunto y dependencias; captura física completa.
- Render visual/responsivo en navegadores y despliegue de la página/política;
  ejecutar HTMLParser no acredita esas pruebas.
- Paso a borrador de las tres releases por el usuario: pendiente de confirmación.
  Instalación real, 3010, HVCI, VM y hardware adicional conservan su deuda.

## Entrega y parada

Solo los cinco archivos documentales indicados. No se modifica código Java/C/C#,
POM/csproj, manifiestos UAC, launchers, CI, política 4A, ABI/wire, helper,
instaladores, imagen o LICENSE. Ningún TXT local o log se incorpora al commit.
No se eleva, termina procesos, instala PawnIO, toca servicios/HVCI, contacta
terceros, firma, crea cuentas, hace push o publica. Las lecturas públicas no
son una solicitud ni envío del proyecto.

**Paso C documental entregado para aprobación. DETENERSE** antes de D/E del
prompt de firma y de 3.2/3.3. El siguiente paso no se deduce de esta política.
El commit local de esta entrega se informa en el mensaje final; la publicación
e integración y cualquier futura prueba/descarga conservan sus aprobaciones.
