# MACE

Monitor de hardware y gestor manual de procesos para Windows 10/11 x64.
La capa nativa en C obtiene datos de CPU, GPU, RAM y procesos; Java/JavaFX
presenta las métricas, sus estados, las alertas y el historial local.
La disponibilidad de cada sensor depende del equipo y de sus proveedores:
una lectura no disponible se muestra como **N/D**.

**Estado al 25/11/2026:** preparación de la solicitud a SignPath Foundation;
aprobación no acreditada y builds de prueba sin firma de MACE. No publicar
nuevas releases con binarios de terceros hasta cerrar la revisión legal.
Esta restricción no impide mantener público el código fuente.

## Características y límites

- Uso de CPU y RAM mediante Windows; enumeración y gestión manual de procesos.
- Temperatura/potencia CPU mediante el helper LibreHardwareMonitor, cuando
  existen sensores compatibles, acceso suficiente y PawnIO operativo.
- GPU NVIDIA mediante NVML, Intel mediante IGCL y LHM como respaldo; Radeon
  mediante LHM, con límites de cobertura y ámbito de potencia documentados.
- Micro-HUD para escritorio, alertas e historial/caja negra local. Superposición
  sobre juegos de pantalla completa: **NO VERIFICADO**.
- La finalización de programas del usuario requiere su confirmación. MACE
  gestiona automáticamente el cierre/recuperación de su propio helper.

No se ofrece consumo eléctrico total del equipo ni compatibilidad universal
de sensores. Preview y datos simulados no acreditan funcionamiento físico.
Consultar [compatibilidad y límites nativos](native/docs/HARDWARE_COMPATIBILITY.md).

## Requisitos y ejecutables

Windows 10/11 de 64 bits y controladores del fabricante compatibles.
El launcher principal solicita elevación; eso no garantiza por sí solo una
lectura válida. CPU puede quedar en N/D si falta PawnIO, permisos o un sensor.
El helper es autocontenido: no se exige instalar un SDK .NET para ejecutarlo.

El empaquetado app-image contiene:

| Archivo | Función |
|---|---|
| `MACE.exe` | Interfaz con telemetría real; manifiesto requireAdministrator |
| `MACE-Preview.exe` | Interfaz simulada, sin cargar la DLL de sensores ni solicitar administrador |
| `app/mace_native.dll` | Biblioteca nativa, ABI 1.5 |
| `app/lhm/` | Helper 1.1.1, protocolo privado 2 y runtime/dependencias |
| `runtime/`, `app/*.jar` | Java y dependencias de la aplicación |
| `app/prerequisites/PawnIO_setup.exe` | Instalador oficial intacto, con pin y firma upstream verificados por el empaquetado |

La carpeta completa es necesaria; copiar un launcher aislado no incluye su
runtime. El flujo actual de MACE no ejecuta el instalador de PawnIO por sí
solo. El instalador definitivo Inno y el encadenamiento automático de PawnIO
**no están implementados/validados como resultado de este paso**; no se
promete instalación automática ni que no haga falta preparar ese requisito.

## Descargas y estado de las releases

El contenido de la página de descargas está en [docs/index.html](docs/index.html).
La URL indicada por el borrador es <https://danielddey.github.io/MACE/>;
su despliegue actualizado queda pendiente de la integración/publicación por
el mantenedor. Esta entrega solo modifica la rama de trabajo.

Las releases `v1.0.0`, `v1.1.0-rc3` y `v1.1.0` son anteriores a LHM según el
usuario y **no son base validada de la nueva distribución**. El mantenedor
las pasará a borrador; ese cambio sigue pendiente de su confirmación.
[Historial de releases](https://github.com/DanielDdeY/MACE/releases).
No se ofrece automáticamente una de ellas como nueva candidata LHM.

## Code signing policy

[Política de firma y privacidad](CODE_SIGNING_POLICY.md).

SignPath Foundation es el programa elegido; no consta confirmación de una
solicitud presentada ni aceptación del proyecto. La atribución al servicio,
los artefactos previstos y el orden de firma se describen allí de forma
condicionada. Una suma SHA-256 identifica bytes: no sustituye una firma del
editor, la revisión legal o una prueba funcional.

## Compilar desde el código fuente

**NO VERIFICADO en un clon limpio**, incluida una ruta con espacios.
No se ha construido ni descargado nada para validar esta sección en el Paso C.
Los procedimientos enlazados son referencias de preparación, no una receta
certificada de release ni autorización para descargar, instalar o publicar.

La fuente actual declara C17/MSVC x64, CMake 3.28+, Java release **22**,
JavaFX **22.0.2**, AtlantaFX **2.0.1**, Mockito **5.12.0** y .NET SDK **10.0.401**
con runtime autocontenido **10.0.12**. También se necesitan Windows SDK y Maven.
La imagen congelada conserva su Java anterior; no se ha migrado al JDK nuevo.

La combinación seleccionada para una candidata futura es **Temurin 25 LTS**,
JavaFX **27.0.1.1 JAR** provisional, Mockito **5.20.0** y Byte Buddy **1.17.7**.
La build concreta de Temurin, las descargas y los diffs siguen pendientes de
aprobación. Compatibilidad AtlantaFX, dependency:tree y regresión de esa
combinación: **NO VERIFICADO**. No confundir selección con implementación.

Preparación documentada:

1. Aprobar insumos, diffs y una variante Mono; la exclusión no se integra en
   ninguna candidata hasta obtener los resultados físicos código 0 exigidos.
2. Publicar primero el helper final y verificar su manifiesto; compilar después
   la DLL de producción ligada a él. La DLL de desarrollo no se distribuye.
3. Usar la DLL final, PawnIO oficial intacto y avisos en el staging; crear
   runtime, launchers y app-image antes de cualquier instalador.
4. Ejecutar la regresión con las precondiciones de integración, distinguir
   simulación/hardware y validar manifiestos, pins y contenido del paquete.

Ver [plan aislado con dos variantes](helper/FASE3-PLAN-CANDIDATA-AISLADA.md),
[comandos futuros NO VERIFICADOS](helper/FASE3-CANDIDATA-COMANDOS.md) y
[descargas propuestas, no autorizadas por este README](helper/FASE3-CANDIDATA-DESCARGAS.md).
El [procedimiento histórico de empaquetado](packaging/README.md) describe la
imagen anterior; tampoco acredita por sí solo un clon limpio de la candidata.
No se presentan los dos comandos aislados del borrador como receta completa.

## CI y contribuciones

La [configuración CI de esta rama](.github/workflows/build-and-test.yml) contiene
tests Java con JDK 22 y un build nativo básico. No acredita una regresión LHM
completa: su configuración nativa no publica el helper ni proporciona el
manifiesto exigido por producción. Esa observación estática no es una ejecución
nueva de Actions. La corrección del workflow queda fuera del Paso C.

El archivo `release-installer.yml` citado en el borrador no está en este
checkout. No se da por implementado un pipeline SignPath ni publicación
automática de una release nueva. No se cambian workflows ni se crean tags aquí.
Para aportar cambios, ver [CONTRIBUTING.md](CONTRIBUTING.md) y las plantillas
del repositorio. DCO no adoptado por ahora. La integración a main la decide
el mantenedor; no se presume configuración de protecciones o checks obligatorios.

## Privacidad

MACE consulta información de hardware y procesos y conserva caja negra y
preferencias localmente. La revisión estática del código propio no identificó
un cliente que envíe automáticamente esas métricas a un servidor. **No se ha
verificado mediante una captura exhaustiva el tráfico o diagnóstico de terceros**,
incluidos runtimes, controladores y sistema operativo.

Los datos de procesos e informes pueden ser sensibles: revisar antes de
compartir por [GitHub Issues](https://github.com/DanielDdeY/MACE/issues).
No adjuntar perfiles, identidades locales, correos, SID, secretos o datos de
máquina. Modelos CPU/GPU y mediciones pueden compartirse según la decisión
del mantenedor. Alcance y límites en la [política](CODE_SIGNING_POLICY.md#privacidad).

## Licencias

Código propio bajo [MIT](LICENSE), copyright declarado en ese archivo.
Los terceros conservan sus términos: MIT no los relicencia ni acredita su
redistribución. [THIRD-PARTY-NOTICES.txt](packaging/THIRD-PARTY-NOTICES.txt) sigue
**como borrador**, no como lista final jurídicamente aprobada. La revisión
cualificada y los [pendientes de B](helper/PENDIENTE-FASE-3B.md) siguen abiertos.
