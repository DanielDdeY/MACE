# Inno y NSIS: comparación acotada, sin cambio de herramienta

**Actualización 2026-11-25:** se mantiene **Inno sujeto a consulta SignPath**.
Ver [evidencia pública nueva](FASE3-EVIDENCIA-INNO-SIGNPATH.md); precedentes
no garantizan admisión de MACE y no autorizan contactar, instalar ni implementar.

Fecha: **2026-11-25**. **Inno se mantiene elegido y sujeto a consulta a
SignPath.** No hay .iss/.nsi en este checkout: existe diseño, no un instalador
Inno implementado que pueda portarse y probarse hoy. No se instaló ni ejecutó
toolchain alguno. Versión/build concreta futura: **NO VERIFICADA**.

## Licencia y avisos

| Aspecto | Inno Setup | NSIS, alternativa no elegida |
|---|---|---|
| Fuente primaria | [license.txt oficial](https://raw.githubusercontent.com/jrsoftware/issrc/main/license.txt) | [licencias oficiales](https://nsis.sourceforge.io/License) |
| Identificador | InnoSetup | Zlib para núcleo/compresión zlib; compresores/plugins con otros términos |
| Estado del catálogo | [SPDX](https://spdx.org/licenses/InnoSetup.json) marca no OSI | [OSI](https://opensource.org/license/zlib) acredita Zlib; no acredita todo plugin posible |
| Avisos exigidos | Conservar los copyrights/URLs existentes en binarios; fuente conserva términos; marcar cambios y no atribuirse autoría ajena | No retirar aviso de fuente, marcar alteraciones y no falsear origen; reconocimiento del producto se aprecia pero no es obligación universal de mostrar un About |
| Componentes del resultado | Loader/motor/desinstalador son código redistribuido | Stub/descompresor/desinstalador y plugins también cuentan |

Fuentes consultadas el 25/11. La base NSIS no permite llamar Zlib al paquete
completo sin revisar compresión y plugins: bzip2 y LZMA tienen licencias
distintas, incluido CPL-1.0 para el módulo LZMA según la página del proyecto.
La opción de menor superficie a estudiar sería compresión zlib y plugins
estrictamente necesarios, con sus avisos. No se quitan avisos para aparentar
que todo el instalador es código propio MIT.

## Comportamientos y esfuerzo

| Contrato de MACE | Inno | NSIS / coste de cambio |
|---|---|---|
| Silencioso exterior | /SILENT o /VERYSILENT; /NORESTART evita reinicio del coordinador | /S, sensible a mayúsculas; rediseñar/paridad de opciones y callbacks |
| Consentimiento PawnIO | Página explícita y opción propia en silencioso; no se deriva del simple modo silencioso | Igual obligación, con UI/script propios; no viene resuelta por /S |
| Proceso hijo | Exec permite obtener salida si espera al fin | ExecWait captura código; ambos waits simples pueden superar 180 s |
| Espera aprobada 180 s | Necesita espera acotada sobre proceso sin kill, no un Exec bloqueante indefinido | Mismo trabajo adicional con APIs/puente auditado; ExecWait solo no satisface D3 |
| 3010 | Capturar código real del hijo y mapear explícitamente; no asumir que RestartIfNeededByRun reconoce cualquier 3010 | SetRebootFlag/SetErrorLevel permiten representar resultado; no son propagación automática del hijo |
| Desinstalación/actualización | Definir identidad, downgrade, reparación y conservación de datos/PawnIO | Reescribir equivalentes y pruebas; no hereda transacciones MSI |
| Firma/verificación | Fijar/verificar setup PawnIO intacto antes de invocarlo; excluirlo de firma propia | Idéntico requisito, más revisión de origen de plugins/herramientas añadidas |

Fuentes operativas:
[Inno parámetros](https://jrsoftware.org/inno-setup/documentation/topic_setupcmdline.htm),
[Exec](https://jrsoftware.org/inno-setup/documentation/topic_isxfunc_exec.htm),
[eventos/código de salida](https://jrsoftware.org/inno-setup/documentation/topic_scriptevents.htm),
[RestartIfNeededByRun](https://jrsoftware.org/inno-setup/documentation/topic_setup_restartifneededbyrun.htm),
[NSIS scripting](https://nsis.sourceforge.io/Docs/Chapter4.html).

El flujo futuro debe seguir instalando **MACE antes de PawnIO**. Dejar otras
versiones de PawnIO sin actualización automática; compatible inicial exacta
2.2.0.0. Argumentos `-install -silent` de PawnIO solo constan como evidencia
estática del diseño anterior, **no ejecución verificada**; no sustituirlos
por los switches de Inno/NSIS ni inventar un switch de no reinicio del hijo.

Tras 180 s: resultado indeterminado, sin matar el instalador, sin segundo
intento ni señal 3010. Un mecanismo futuro de espera no debe meter al instalador
PawnIO en un job que lo mate al cerrar el coordinador. Esa implementación se
revisará en 3.3; no se añade ahora código, plugin, DLL o script ejecutable.

Solo el 3010 **observado del hijo verificado** habilita la futura señal PawnIO.
El reinicio pedido por archivos del coordinador es otro origen. Por defecto
Inno puede devolver 0 aun requiriendo reinicio; /RESTARTEXITCODE y eventos
permiten configurarlo. NSIS requiere configurar su salida explícitamente.
Nada escribe ahora la clave volátil ni implementa su consumo 3.4.

Esfuerzo estimado **relativo, no medido**: medio/alto para portar el contrato
de diseño a NSIS, aunque no hay código Inno que reescribir. Abarca UI,
consentimiento silencioso, proceso/timeout, logs saneados, actualización,
desinstalación, retorno y pruebas en VM. No se inventan días ni ahorro probado.
Ambas herramientas necesitan los mismos escenarios 0/3010/error/timeout,
versión ajena, consentimiento ausente y MACE abierto. Todo físico sigue NV.

## Relevancia para SignPath

Los [términos del programa](https://signpath.org/terms) exigen licencias OSI
sin doble licencia comercial y delimitan bibliotecas del sistema/terceros.
Inno pendiente de interpretación no se transforma en aceptado por ser gratuito.
NSIS con núcleo Zlib reduce ese punto de fricción, pero no resuelve PawnIO,
IGCL/CRT/NVML, Mono o runtimes ni garantiza admisión. No se refirma código
ajeno para eludir el análisis. No se ha enviado consulta.

## Decisiones necesarias

1. No se pide elegir otra vez: **mantener Inno sujeto a consulta**, decisión recibida.
2. Si la consulta resulta desfavorable, autorizar entonces un diseño NSIS con
   compresión/plugins concretos y pruebas; hoy solo queda comparado.
3. Aprobar más adelante el contrato completo de parámetros/retornos en 3.3;
   no tratar los ejemplos documentales como interfaz ya implementada.
