# Registro de decisiones para candidata aislada

Fecha: **2026-11-25**, America/Lima. Base de la entrega **4668085**,
rama `LHM_implementado`. Registro posterior: no modifica ni sustituye
silenciosamente informes históricos. Alcance A–E de la nueva solicitud;
no autoriza los pasos C/D/E del prompt original de firma ni 3.2/3.3.

## Decisiones 1 a 7, según declaración del usuario

| Nº | Decisión y alcance exacto | Estado operativo |
|---|---|---|
| 1 | v1.0.0, v1.1.0-rc3 y v1.1.0 son anteriores a LHM y no se usan como base. **El usuario** los marcará como borrador | Cambio a borrador **PENDIENTE DE CONFIRMACIÓN DEL USUARIO**. No se afirma realizado, no se inventaría su contenido ni se modifica el remoto |
| 2 | JavaFX **27.0.1.1 con JAR**, selección provisional por preferencia de acceso público; sujeta a coordenadas, AtlantaFX 2.0.1 y regresión UI. Temurin **25 LTS** como línea. Mockito **5.20.0** y Byte Buddy **1.17.7**, verificando dependency:tree antes | No se cambia JDK/POM/imagen. Si JavaFX falla de forma no corregible, presentar **25.0.5.1** con evidencia y esperar aprobación, sin sustitución automática |
| 3 | Inno sujeto a consulta SignPath; Stellarium/VSCodium son contexto para preguntar, no garantía | NSIS **no se adopta**; no contacto ni implementación |
| 4 | Incorporar LICENSE adjunto exacto, procedente de main, sin cambiar titular | **Realizado** en commit exclusivo **7be9ab0**; ver comprobación debajo |
| 5 | CONTRIBUTING.md y plantillas permanecen en rama de trabajo | PR a main la decide el usuario después; no abrir PR, merge ni push |
| 6 | El usuario acepta publicar modelos CPU/GPU y mediciones; no requieren saneado | Mantener fuera rutas de perfil, identidades de usuario/equipo, correos, SID y datos de máquina. No interpretar como permiso para enviar datos personales o modificar informes antiguos |
| 7 | DCO: **no por ahora** | No imponer sign-off, bots, trailers ni rehacer commits |

La preferencia por una línea pública no garantiza que un proveedor mantenga
indefinidamente acceso o soporte gratuito. La disponibilidad comprobada de
JavaFX en A sigue siendo evidencia de aquella consulta; regresión, AtlantaFX
y compatibilidad real de la combinación elegida permanecen **NO VERIFICADAS**.
MIT propia y titular siguen confirmados. El alias público del copyright se
conserva por la instrucción expresa; no se agregan correos personales.

## LICENSE: comprobación A

- SHA-256 coincide con el proporcionado por el usuario (**prefijo B3A7BCF8,
  sufijo 9AF4269**); comparación de **1067 bytes**, igualdad exacta.
- Se leyó la entrada LICENSE del árbol `main` en GitHub por GET el 25/11.
  Su identificador de blob coincidió con el del mirror público anterior;
  se leyeron esos bytes Git y se compararon directamente con adjunto y copia.
  No se confunde un texto normalizado por el conector con prueba de bytes.
- Commit A contiene **solo LICENSE**. No se crea otra licencia ni se importa
  main. Los informes históricos sobre su ausencia quedan como evidencia fechada.
- Git avisó de conversión futura LF/CRLF por configuración local. El archivo
  actual y el blob del commit conservan los bytes verificados; un checkout
  futuro puede requerir nueva comparación. No se cambia .gitattributes/config
  en esta tarea para resolver ese comportamiento sin revisión separada.

Comprobación reproducible local, sin descargar:

```powershell
Get-FileHash -LiteralPath .\LICENSE -Algorithm SHA256
git show --stat --oneline 7be9ab0
```

El resultado completo del hash se entrega al usuario; los informes no
reproducen hashes completos de dependencias o datos de máquina.

## Reglas que siguen vigentes

No nuevas releases con binarios de terceros hasta cerrar revisión legal;
fuente pública no afectada. THIRD-PARTY-NOTICES continúa como borrador.
No tocar DLL 1.5.0, ABI 1.5, wire 2, helper 1.1.1, política 4A ni estados 9–16.
Metadatos MIT/Mono/JDK son propuestas, no cambios aplicados al producto.
No instalaciones, servicios, HVCI, elevación, terminaciones de procesos,
firma, contactos, solicitudes, PR o push por el agente.

La decisión sobre Mono se registra con su diagnóstico aparte: los cuatro
ensayos comunicados devolvieron 2, en consolas normal y administrador,
sin evidencia física a favor o en contra. Ninguna variante queda recomendada
ni autorizada para construcción por aprobar este registro documental.

## Estado y siguiente decisión

Esperar confirmación del cambio de releases por el usuario, resultados físicos
diagnosticables y aprobación expresa de descargas y construcción futura.
Esta entrega se detiene tras el diseño; no ejecuta esa construcción.
