/**
 * Capa de infraestructura de MACE (adaptadores de salida de la arquitectura hexagonal).
 *
 * <p>Subpaquetes:</p>
 * <ul>
 *   <li>{@code nativebridge}: puente Java ↔ C con la FFM API (JEP 454) sobre el contrato V2
 *       (ABI 1.4). {@code NativeLayouts} replica las estructuras de {@code mace_types.h} y
 *       {@code mace_types_v2.h}; {@code NativeFunctions} verifica {@code CP_GetAbiInfo} antes de
 *       enlazar los símbolos; {@code FfmSensorsAdapter} / {@code FfmProcessAdapter} implementan
 *       los puertos del dominio y {@code NativeBlackboxRing} maneja el anillo V2 por token.</li>
 *   <li>{@code mock}: adaptadores simulados con el mismo contrato V2 y escenarios reproducibles
 *       ({@code MockScenario}) para trabajar sin la DLL (ni Windows).</li>
 *   <li>{@code telemetry}: publicador Flow compartido por dashboard y Micro-HUD y caja negra
 *       persistente ({@code PersistentBlackboxSink}), dueña exclusiva del archivo histórico.</li>
 * </ul>
 *
 * <p>{@link com.mace.infrastructure.InfrastructureAdapters} es el punto de composición:
 * elige nativo o simulado según {@code -Dmace.infra.mode=auto|native|mock}.</p>
 *
 * <p>Opciones de JVM relevantes:</p>
 * <ul>
 *   <li>{@code --enable-native-access=ALL-UNNAMED}: evita las advertencias de acceso restringido de FFM.</li>
 *   <li>{@code -Dmace.native.lib=<ruta>} (o {@code MACE_NATIVE_LIB}): ubicación de la DLL verificada.</li>
 *   <li>{@code -Dmace.native.sha256=<hash>} (o {@code MACE_NATIVE_SHA256}): rechaza cualquier otra DLL.</li>
 *   <li>{@code -Dmace.mock.scenario=<NOMBRE>} y {@code -Dmace.mock.seed=<n>}: perfil simulado.</li>
 *   <li>{@code -Dmace.blackbox.dir=<carpeta>}: carpeta de la caja negra (por defecto
 *       {@code %LOCALAPPDATA%\MACE\blackbox}).</li>
 * </ul>
 */
package com.mace.infrastructure;
