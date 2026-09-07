# MACE (CorePulse)

Sistema de telemetría de hardware pasivo de alta precisión y gestor de procesos interactivo para Windows, construido con arquitectura hexagonal desacoplada.

## Stack Tecnológico

- **Capa Nativa (C):** C17/C23, Win32 API (`psapi.h`, `tlhelp32.h`), NVIDIA Management Library (NVML), CMake 3.28+, MSVC 2022.
- **Capa de Aplicación (Java):** OpenJDK 22+ (Foreign Function & Memory API / Panama), JavaFX 22+, AtlantaFX, Apache Maven.
- **Empaquetado:** `jlink` + `jpackage` con manifiesto UAC para privilegios de administrador.

## Arquitectura

- **Arquitectura Hexagonal (Puertos y Adaptadores):** Casos de uso y reglas de negocio puros en Java, totalmente aislados de la API de Windows mediante interfaces (`ProcessLifecyclePort`, `HardwareSensorsPort`).
- **Control Manual Estricto:** Sin finalización ni suspensión automática de procesos para garantizar la seguridad operativa.
- **Preparado para Extensiones:** Ganchos arquitectónicos listos para detector de cuelgues, caja negra, dashboard móvil y micro-HUD.

## Prerrequisitos de Compilación

1. **Windows 10/11 x64**
2. **Visual Studio 2022** con herramientas de C++ y Windows 10/11 SDK.
3. **CMake 3.28+** y **Ninja**.
4. **JDK 22 o superior**.
5. **Apache Maven 3.9+**.