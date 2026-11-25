# Dossier acotado para revisión cualificada

**Actualización 2026-11-25:** el usuario aprueba el contenido de este dossier;
no tiene revisor designado. Regla 5 confirmada: no publicar nuevas releases
con binarios de terceros hasta cerrar revisión legal; fuente pública no afectada.
Ver [guía D](FASE3-GUIA-REVISION-SIN-REVISOR.md). No se envió ni contrató nada.

Fecha: **2026-11-25**. Preparado para el usuario/revisor que este designe;
**no enviado**, no asesoría legal y no sustituto de revisión cualificada.
No es el dossier de solicitud a SignPath del Paso E ni una política del Paso C.

## Alcance y hechos mínimos

MACE propio: MIT confirmada, derivados cerrados aceptados; LICENSE existe
en [GitHub/main](https://github.com/DanielDdeY/MACE/blob/main/LICENSE),
verificado por lectura, y falta en este checkout. No se cambia el titular
ni se atribuye una opinión jurídica sobre derechos previos del equipo.

Base de prueba, no release aprobada: imagen Windows con Java, DLL nativa,
helper .NET autocontenido, bibliotecas de terceros, setup PawnIO intacto y
avisos provisionales. Versiones: DLL 1.5.0/ABI 1.5, wire 2, helper 1.1.1,
LHM 0.9.6, PawnIO setup 2.2.0.0. No se adjunta el inventario de 103 filas ni
binarios, hashes completos, logs personales o datos de usuarios finales.

## 1. PawnIO: setup, driver y módulos

Fuentes públicas consultadas el 25/11:
[driver/excepción](https://raw.githubusercontent.com/namazso/PawnIO/master/README.md),
[módulo IntelMSR actual](https://raw.githubusercontent.com/namazso/PawnIO.Modules/main/IntelMSR.p),
[release setup](https://github.com/namazso/PawnIO.Setup/releases/tag/2.2.0).
Fuente actual declara GPL-2.0-or-later con excepción específica; el módulo
consultado LGPL-2.1-or-later. El setup completo y los doce recursos históricos
no quedan licenciados automáticamente por esas declaraciones actuales.

Preguntas concretas:

1. ¿Qué licencia/permiso cubre el **EXE instalador completo 2.2.0.0** y cada
   componente interno, incluida cualquier diferencia entre edición firmada y
   otras? ¿Cuál es la fuente correspondiente de esa release exacta?
2. ¿Qué entrega de fuentes, textos, scripts de compilación y/o oferta válida
   debe acompañar a MACE en la modalidad de distribución elegida? Precisar
   archivo/versión y obligaciones: no responder solo con enlace a main.
3. ¿Cómo se aplica la excepción IOCTL a MACE/helper/LHM, y qué obligaciones
   distintas alcanzan a los módulos Pawn cargados/embebidos en LHM?
4. ¿Cuál es la fuente/licencia de **cada uno de los doce recursos binarios**
   correspondientes a LHM 0.9.6? ¿Requieren medidas de sustitución/relink u
   otras obligaciones que el paquete propuesto todavía no cumpla?
5. ¿El anuncio de licencias alternativas implica un modelo comercial relevante
   para la regla del programa? Separar opción libre usada de otro contrato
   concreto, sin presumir que MACE tiene uno.

Resultado esperado de revisión: matriz pequeña setup/driver/módulos con
permisos, obligaciones y prueba de correspondencia, o bloqueo explícito.
No extracción, instalación, refirma ni contacto con el editor realizados aquí.

## 2. Bibliotecas del sistema: IGCL, CRT y NVML

Hechos: la cabecera IGCL versionada tiene licencia Intel 10.07.21 y restricción
de plataforma; su aviso viaja. IGCL/NVML de los drivers no se copian. MSVC CRT
está enlazado en DLL propia; VC/UCRT también viajan con Java/JavaFX.
Firma del editor no acredita por sí sola derecho de redistribución.
Referencia pública: [Microsoft redistribución](https://learn.microsoft.com/cpp/windows/redistributing-visual-cpp-files);
fuente local: `native/vendor_headers/intel_igcl/LICENSE`, sin modificar.

Preguntas:

1. ¿Qué derechos/condiciones concretas cubren usar esa cabecera y distribuir
   la DLL resultante, y cómo preservar sus términos al coexistir con MIT propia?
2. ¿Qué términos del toolchain y redistribuibles exactos autorizan CRT estático,
   las cinco VC y 42 UCRT internas de JavaFX y las tres VC del runtime Java?
3. ¿Qué dependencias califican como bibliotecas de sistema **para la política
   de SignPath**, y cuáles requieren decisión de la fundación? No confundir
   esa política privada con compatibilidad legal de MIT ni con licencia Windows.
4. ¿Qué obligaciones afectan a la interfaz/carga dinámica de NVML/IGCL aunque
   sus binarios no se copien? Identificar versiones/documentos faltantes.

## 3. Mono.Posix 1.0.0

Nuspec no identifica repo/tag; enlace hacia LICENSE de Mono actual, sin
correspondencia histórica acreditada de las tres DLL Windows. Hay propuesta
separada de excluir activos: no se aplicó y el paquete puede seguir en el grafo.
[Ficha NuGet](https://www.nuget.org/packages/Mono.Posix.NETStandard/1.0.0),
[licencia actual](https://raw.githubusercontent.com/mono/mono/main/LICENSE).

Preguntas: ¿qué evidencia vincula fuentes/licencias con esos bytes? ¿El enlace
del paquete acredita sus condiciones a la fecha correspondiente? ¿Qué derechos
de uso/restauración subsisten si se dejan de redistribuir las DLL? ¿Qué aviso
debe mantenerse y qué se podría retirar solo después de comprobar la exclusión?

## 4. Inno Setup

Se mantiene elegido, no implementado. Fuente:
[licencia oficial](https://raw.githubusercontent.com/jrsoftware/issrc/main/license.txt)
y [SPDX](https://spdx.org/licenses/InnoSetup.json), consultadas el 25/11.

Preguntas: ¿qué partes del motor/loader/desinstalador y avisos deben acompañar
al EXE MACE? ¿Qué efecto tienen posibles modificaciones de esa herramienta?
¿Su licencia no listada OSI exige cambiar herramienta para este programa, o
debe decidirlo directamente SignPath? NSIS es solo alternativa comparada:
si se adopta, revisar compresor/plugins exactos, no una aprobación genérica.

## Separación de responsabilidades y cierre de revisión

El revisor cualificado identifica derechos/obligaciones y carencias. SignPath
decide la admisión según sus [términos](https://signpath.org/terms); nadie ha
aprobado MACE en su nombre. El usuario conserva la decisión y presentará la
solicitud. No se solicitan identidad legal, correo adicional o pago de certificados.

Pedir un resultado breve: permitido/condicionado/bloqueado/NV por grupo,
fuente concreta, obligación accionable y evidencia que falta. La revisión
debe cerrarse antes de publicar, no por una firma electrónica o un test exitoso.
Fuera de este dossier: dictamen universal sobre todo el inventario, contrato
de firma, privacidad completa, implementación del instalador y pruebas en VM.

## Decisiones necesarias

1. Designar cómo se realizará la revisión cualificada y cuándo se presentará
   este dossier; el agente no lo envía ni contrata servicios.
2. Aprobar qué evidencia adicional se entregará al revisor, siempre saneada;
   no compartir binarios/código con terceros por autorización implícita.
3. Resolver con los resultados los bloqueos antes de publicar; cualquier
   exclusión, sustitución o cambio de producto requiere aprobación aparte.
