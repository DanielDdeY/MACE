# Propuesta: excluir Mono del payload Windows

**Actualización 2026-11-25:** ExcludeAssets=all aprobado **solo para experimento
separado**, ya realizado: [resultado B](FASE3-EXPERIMENTO-MONO.md).
202 entradas publicadas/199 runtime; imagen de referencia conserva 370.
El diff completo de tres archivos y locks está propuesto, **no integrado**.
No volver a pedir aprobación del experimento ya autorizado. Licencia abierta.

Fecha: **2026-11-25**, America/Lima. Base local **9a0ee8f**.
**Cambio de producto propuesto, NO aplicado.** No se borran DLL ni se
restaura, compila o ejecuta helper/MACE. La licencia sigue investigándose.

## Conclusión y evidencia nueva

Hay evidencia estática para proponer exclusión en Windows: la carga de Mono
encontrada en LHM está protegida por `IsUnix`. **No es una prueba de ejecución
sin Mono** ni demuestra por sí sola que el host .NET acepte cualquier deps.

| Evidencia | Resultado / límite |
|---|---|
| Paquete restaurado LHM 0.9.6 | DLL net10 coincide con la DLL de imagen; nuspec declara commit 3d331e3. No se recompiló upstream para probar reproducibilidad |
| csproj upstream fijado | Incluye Mono.Posix.NETStandard 1.0.0 para todos los TFM distintos de net472; incluye por tanto net10. Es dependencia de publicación aunque no se use en una ruta Windows |
| OpCode.Open/Close, fuente fijada | En rama IsUnix ejecuta Assembly.Load de Mono; en Windows reserva/libera memoria con VirtualAlloc/VirtualFree |
| Software.OperatingSystem, fuente fijada | IsUnix solo es verdadero para PlatformID.Unix/MacOSX |
| Metadatos de 183 ensamblados reales | 0 AssemblyReference a Mono; LHM sí contiene el nombre dinámico Mono.Posix.NETStandard. La ausencia de referencias estáticas NO excluye reflexión |
| Código propio del helper | No usa tipos Mono ni carga explícitamente ese ensamblado; habilita CPU/GPU mediante LHM |

Las tres fuentes upstream se leyeron por el conector GitHub en el commit
declarado, no tomando una búsqueda de la rama actual como prueba del pasado:
[csproj](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/3d331e3/LibreHardwareMonitorLib/LibreHardwareMonitorLib.csproj),
[OpCode](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/3d331e3/LibreHardwareMonitorLib/Hardware/OpCode.cs),
[IsUnix](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/3d331e3/LibreHardwareMonitorLib/Software/OperatingSystem.cs).
Consulta: **2026-11-25**. La búsqueda de código en la rama actual solo sirvió
para localizar archivos; no se la equipara al commit fijado.

No se ha demostrado que toda dependencia ajena carezca de otras cargas
dinámicas, ni se inspeccionó exhaustivamente el IL de todos los caminos.
La conclusión se limita a las rutas y activos seleccionados aquí descritos.

## Mecanismo propuesto y alternativa descartada

Se presenta [MONO-EXCLUSION.patch](pendientes/MONO-EXCLUSION.patch): referencia
directa fijada a 1.0.0 con `ExcludeAssets="all"` en el proyecto helper.
Se excluyen activos durante resolución/publicación, **antes** de construir
deps y manifiestos. No se parchea ni recompila LHM.
[Semántica NuGet de activos](https://learn.microsoft.com/en-us/nuget/consume-packages/package-references-in-project-files#controlling-dependency-assets),
leída el 25/11. La eficacia concreta con SDK 10.0.401 está NO VERIFICADA.

No se recomienda borrar tres archivos después de publicar: deps podría
seguir declarándolos y el host podría rechazar el arranque. Tampoco se propone
un fork de LHM ni cambiar sensores, elevación, estados o recuperación 4A.

El parche es un **primer diff**, no el cambio completo listo para publicar.
La nueva referencia directa modifica la clasificación en lock y puede afectar
el grafo de Tests. Tras aprobación habrá que regenerar y revisar los lock
de Helper/Tests; `--locked-mode` no debe eludirse para ocultar discrepancias.
No se inventa aquí el resultado de restore ni un diff de lock sin generarlo.

## Efecto esperado en hashes y manifiestos

Excluir: Mono.Posix.NETStandard.dll, MonoPosixHelper.dll y libMonoPosixHelper.dll.
Proyección **solo si no cambia ningún otro archivo**:

| Conjunto | Actual | Proyección Mono aislado |
|---|---:|---:|
| Publicado, artifact-manifest | 205 | 202 |
| Runtime, helper-runtime.manifest | 202 | 199 |
| Imagen total | 370 | 367 |

No son nuevas cifras verificadas. Metadatos MIT y JDK pueden cambiar el
conjunto después; no se prometen conteos combinados. El paquete Mono puede
permanecer en lock/cache o en el grafo sin activos: eso no significa que sus
bytes viajen, ni elimina la revisión de sus derechos de uso como dependencia.

Cadena obligatoria en un directorio nuevo, tras autorización:

1. Resolver con lock revisado y publicar helper; comprobar que no se copian
   los tres activos ni deps exige rutas inexistentes. Revisar todos los RID/TFM
   utilizados, sin exportar versiones futuras del grafo como hechos.
2. Comprobar arranque/muestreo con ese publicado; no usar el éxito de los
   105 tests con el paquete antiguo como prueba de exclusión.
3. Firma futura, si está autorizada, antes de generar artifact-manifest y
   helper-runtime.manifest. Recalcular todos los hashes; no asumir DLL/EXE
   idénticos por no cambiar fuente C#.
4. Recompilar DLL de producción contra el manifiesto final: cambia el pin
   MACE_LHM_MANIFEST_SHA256 y probablemente los bytes/hash de la DLL, sin
   cambiar DLL 1.5.0, ABI 1.5, wire 2, helper 1.1.1 ni layouts/4A.
5. Regenerar pins del launcher, runtime-manifest y sumas de una **imagen nueva**.
   No editar la actual ni reutilizar su aceptación como evidencia de la nueva.

## Regresión que demostraría la exclusión

En entorno aislado autorizado: restauración bloqueada, ausencia de activos,
arranque frío y reapertura de LHM en Windows, CPU/GPU con y sin elevación,
reconexión y cierre, comparación con el baseline y verificación completa de
integridad/deps. Ejecutar helper 105, CTest 76 desarrollo/77 producción,
Java 347 + 1 omitida, payload 8 + rechazos, protección y auditoría 181/0;
explicar cifras nuevas si cambian. Usar los scripts existentes y nuevas
pruebas de ausencia/arranque si resultan necesarias, presentadas aparte.

Las pruebas que lancen producto, requieran administrador o terminen procesos
necesitan el alcance autorizado correspondiente; **no ejecutadas ahora**.
Intel integrada/NVIDIA, HVCI y recuperación real pendiente siguen NV.

## Licencia: investigación en paralelo

Se releyeron nupkg/nuspec exactos: no hay LICENSE/NOTICE/COPYING local;
la ficha [NuGet 1.0.0](https://www.nuget.org/packages/Mono.Posix.NETStandard/1.0.0)
no acredita un commit/tag de los tres binarios. El enlace de licencia remite
al [LICENSE actual de Mono](https://raw.githubusercontent.com/mono/mono/main/LICENSE),
que describe MIT y porciones ajenas. Consulta del 25/11; **no se acredita
todavía la correspondencia histórica de cada DLL ni se le asigna MIT por nombre**.
No se contactó al editor ni aceptó una licencia/restauración nueva.

## Decisiones necesarias

1. Aprobar o ajustar el mecanismo ExcludeAssets para un experimento de producto
   separado, incluyendo revisión de lock/deps; este documento no lo aplica.
2. Aprobar después el diff completo y el plan de pruebas, conservando imagen
   actual como referencia; decidir el entorno Windows autorizado para ejecutarlo.
3. Mantener abierta la acreditación de licencia aunque se retiren los bytes
   del payload. No declarar resuelto el requisito de SignPath por esa exclusión.
