cmake_minimum_required(VERSION 3.28)
project(mace_native C)

set(CMAKE_C_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Opciones de compilación para MSVC x64
if (MSVC)
    add_compile_options(/W4 /WX /utf-8)
    add_definitions(-D_CRT_SECURE_NO_WARNINGS -DUNICODE -D_UNICODE)
endif()

# Inclusión de cabeceras públicas
include_directories(include)

# Lista de fuentes
set(SOURCES
    src/mace_api.c
    src/process/process_enumerator.c
    src/process/process_filter.c
    src/process/process_controller.c
    src/telemetry/nvml_loader.c
    src/telemetry/cpu_sensors.c
    src/extensions/hang_detector_stub.c
    src/extensions/memory_map_stub.c
)

add_library(mace_native SHARED ${SOURCES})

# Exportación de símbolos y enlace con librerías Win32
target_compile_definitions(mace_native PRIVATE MACE_EXPORTS)
target_link_libraries(mace_native PRIVATE kernel32 psapi user32)

# Propiedades del binario de salida
set_target_properties(Mace_native PROPERTIES
    OUTPUT_NAME "mace_native"
    PREFIX ""
)