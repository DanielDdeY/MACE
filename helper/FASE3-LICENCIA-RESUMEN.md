# Paso A: base provisional y aclaración para el B parcial

Actualizado: **2026-11-25**, America/Lima. No es respaldo legal ni evidencia suficiente para SignPath.
El usuario aprueba la auditoría **solo como base provisional** y confirma publicar MACE bajo MIT, incluidos derivados cerrados.
LICENSE se verificó en GitHub/main, con copyright 2026 DanielDdeY; no existe en este checkout. No se copia ni modifica.

## a. Cifras históricas, sin promoverlas a cumplimiento

Auditoría del 23/11: 103 filas; 74 con texto local, 8 con metadatos, 5 con URL y 16 sin evidencia suficiente del artefacto.
Los porcentajes 71,84 % / 7,77 % / 4,85 % / 15,53 % clasifican fuentes, no derechos ni admisión.
370 archivos de imagen, helper 202/205 según conjunto, 10 JAR y 11 módulos; 45 cotejos de versión y 58 filas NV.
Muestreo: 9 cotejos limitados y 4 NO VERIFICADOS. No se recalifica todo el inventario por la nueva publicación de LICENSE.

## b. Las 16 filas: componente, motivo y destino

**(a)** binario actual en imagen, previsto en el instalador; el instalador definitivo aún NO está construido/verificado.
**(b)** binario de imagen excluido del instalador: ninguno identificado en estas filas.
**(c)** aviso/metadato; **(c*)** fila documental sobre binario externo del sistema que no se copia: no confundir con (b).
Los avisos agregados no prueban qué código está enlazado. Las filas propias conservan MIT confirmado; componentes ajenos siguen separados.

| Fila / componente | Tipo | Motivo que sigue abierto |
|---|---|---|
| R001 · JAR MACE | (a) | MIT pública; falta sincronización local y materialización en paquete/metadatos |
| R002 · MACE.exe / Preview | (a) | Stubs jpackage y CRT tienen términos propios además del código MACE |
| R003 · mace_native.dll | (a) | MIT propia no acredita cabecera IGCL/CRT enlazado |
| R004 · Helper exe/DLL | (a) | MIT propia; apphost/runtime y avisos requieren alcance separado |
| R005 · Protocol.dll | (a) | MIT pública; falta materialización local/de paquete, no una nueva elección |
| R011 · Mono.Posix.NETStandard.dll | (a) | Licencia/fuente histórica de los bytes 1.0.0 no acreditadas |
| R012 · Dos DLL nativas Mono | (a) | Mismo vacío histórico; firma Xamarin no concede redistribución |
| R019 · Siete nativos OpenJFX | (a), en JAR | No se acreditó licencia/composición exacta de todas sus porciones |
| R021 · Cinco VC de JavaFX | (a), en JAR | Términos Microsoft y excepción del programa pendientes |
| R022 · 42 UCRT/API sets de JavaFX | (a), en JAR | Redistribución/encaje no acreditados por la licencia del JAR |
| R023 · Tres VC del runtime Java | (a) | Términos y encaje de los binarios exactos pendientes |
| R025 · PawnIO_setup.exe | (a), intacto | No acreditada la licencia/fuente de todo su contenido |
| R032 · CRT MSVC estático | (a), en DLL propia | Código enlazado, no archivo adicional; derechos/encaje pendientes |
| R029 · Runtime IGCL | (c*), externo | Lo aporta el driver; versión/términos concretos y encaje pendientes |
| R030 · NVML | (c*), externo | Lo aporta el driver NVIDIA; no se redistribuye, contrato/encaje pendientes |
| R031 · APIs/DLL Windows | (c*), externo | Dependencias del SO, no binarios copiados; excepción por concretar |

Son 13 filas (a), 3 filas documentales externas (c*) y 0 (b); no 16 archivos distintos.

## c. Los cuatro cotejos del muestreo NO VERIFICADOS

| Componente | Tipo | Qué faltó acreditar |
|---|---|---|
| PawnIO_setup.exe | (a) | Términos/fuentes correspondientes al instalador completo |
| Módulos PawnIO de LHM | (a), recursos de DLL | Correspondencia entre doce binarios y fuentes/licencias exactas |
| Mono.Posix.NETStandard | (a) | Correspondencia histórica; fuente Unix identificada no prueba exclusión ejecutada |
| musl, aviso de .NET | (c) | Evidencia disponible: aviso agregado; presencia/versión/porciones binarias no acreditadas |

## d. Decisiones vigentes y entregas

- Inno se mantiene sujeto a consulta; solo comparación documental con NSIS, sin migración ni instalación.
- Mono: propuesta de exclusión priorizada; carga por reflexión bajo IsUnix en la fuente fijada. Cambio sin aplicar.
- JDK: propuesta de Temurin 25 LTS y JavaFX compatible antes de 3.3; sin cambiar host ni imagen.
- Revisión cualificada antes de publicar: dossier acotado preparado, no enviado.
- B parcial: CONTRIBUTING y plantillas; DCO pendiente de decisión, no obligatorio; metadatos como diff sin aplicar.
- Avisos conservan PENDING/no legal clearance; no finalizados. C, D y E no autorizados.

## e. Bloqueos y NO VERIFICADO

Persisten derechos/fuentes exactos PawnIO/Mono, bibliotecas del sistema, encaje Inno/SignPath y subcomponentes de runtimes.
No se confunde MIT elegida/publicada con admisión del paquete. No hay solicitud, firma ni aprobación de SignPath.
Exclusión Mono ejecutada, regresión JDK/JavaFX nuevos, builds concretas, VM y hardware: NO VERIFICADO.
**Detenerse tras B parcial y las cuatro propuestas; esperar aprobación de cada diff de producto.**
