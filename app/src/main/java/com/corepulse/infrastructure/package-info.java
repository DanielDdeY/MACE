/**
 * Capa de infraestructura de MACE (adaptadores de salida de la arquitectura hexagonal).
 *
 * <p>Subpaquetes:</p>
 * <ul>
 *   <li>{@code nativebridge}: puente Java ↔ C con la FFM API (JEP 454). {@code NativeLayouts}
 *       replica las estructuras de {@code mace_types.h}, {@code NativeFunctions} enlaza los
 *       símbolos de {@code mace_api.h} y {@code FfmSensorsAdapter} / {@code FfmProcessAdapter}
 *       implementan los puertos del dominio sobre la DLL.</li>
 *   <li>{@code mock}: {@code MockSensorsAdapter} y {@code MockProcessAdapter} generan datos
 *       verosímiles para trabajar sin la DLL (ni Windows).</li>
 * </ul>
 *
 * <p>{@link com.corepulse.infrastructure.InfrastructureAdapters} es el punto de composición:
 * elige nativo o simulado según {@code -Dmace.infra.mode=auto|native|mock}.</p>
 *
 * <p>Opciones de JVM relevantes:</p>
 * <ul>
 *   <li>{@code --enable-native-access=ALL-UNNAMED}: evita las advertencias de acceso restringido de FFM.</li>
 *   <li>{@code -Dmace.native.lib=<ruta>} (o la variable {@code MACE_NATIVE_LIB}): ubicación explícita
 *       de {@code mace_native.dll} si no está en las carpetas de compilación habituales.</li>
 * </ul>
 */
package com.corepulse.infrastructure;
