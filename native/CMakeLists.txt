cmake_minimum_required(VERSION 3.28)
project(mace_native VERSION 1.2.0 LANGUAGES C RC)

if (NOT WIN32 OR NOT CMAKE_C_COMPILER_ID STREQUAL "MSVC")
    message(FATAL_ERROR "mace_native requires Windows with the MSVC C compiler")
endif()
if (NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
    message(FATAL_ERROR "mace_native requires an x64 generator/toolchain (not x86)")
endif()

set(CMAKE_C_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

file(MAKE_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/generated")
configure_file(cmake/mace_version.h.in
    "${CMAKE_CURRENT_BINARY_DIR}/generated/mace_version.h" @ONLY)
configure_file(resources/mace_native.rc.in
    "${CMAKE_CURRENT_BINARY_DIR}/generated/mace_native.rc" @ONLY)

set(MACE_NATIVE_SOURCES
    src/mace_api.c
    src/mace_contract.c
    src/process/process_enumerator.c
    src/process/process_filter.c
    src/process/process_controller.c
    src/telemetry/nvml_loader.c
    src/telemetry/igcl_provider.c
    src/telemetry/gpu_providers.c
    src/telemetry/cpu_sensors.c
    src/hardware/gpu_inventory.c
    src/extensions/hang_detector_stub.c
    src/extensions/memory_map_stub.c
    "${CMAKE_CURRENT_BINARY_DIR}/generated/mace_native.rc"
)

add_library(mace_native SHARED ${MACE_NATIVE_SOURCES})
target_include_directories(mace_native PRIVATE include
    vendor_headers/intel_igcl
    "${CMAKE_CURRENT_BINARY_DIR}/generated")
set(MACE_ADLX_SDK_DIR "" CACHE PATH "External AMD ADLX SDK root (contains SDK/Include/ADLX.h)")
if (MACE_ADLX_SDK_DIR AND EXISTS "${MACE_ADLX_SDK_DIR}/SDK/Include/ADLX.h")
    target_sources(mace_native PRIVATE src/telemetry/adlx_provider.c)
    target_include_directories(mace_native PRIVATE "${MACE_ADLX_SDK_DIR}")
    target_compile_definitions(mace_native PRIVATE MACE_HAS_ADLX=1)
elseif (MACE_ADLX_SDK_DIR)
    message(FATAL_ERROR "MACE_ADLX_SDK_DIR does not contain SDK/Include/ADLX.h")
else()
    message(STATUS "AMD ADLX provider disabled: set MACE_ADLX_SDK_DIR to the official SDK")
endif()
target_compile_definitions(mace_native PRIVATE MACE_EXPORTS
    _CRT_SECURE_NO_WARNINGS UNICODE _UNICODE)
target_compile_options(mace_native PRIVATE /W4 /WX /utf-8)
target_link_libraries(mace_native PRIVATE kernel32 psapi user32 dxgi dxguid gdi32 shell32 ole32)
set_target_properties(mace_native PROPERTIES
    OUTPUT_NAME mace_native
    PREFIX ""
    MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>"
)

# Demostración manual de telemetría V2 y procesos; depende del equipo.
add_executable(test_mace test_main.c)
target_include_directories(test_mace PRIVATE include)
target_compile_options(test_mace PRIVATE /W4 /WX /utf-8)
target_link_libraries(test_mace PRIVATE mace_native)
set_target_properties(test_mace PROPERTIES
    MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

include(CTest)
if (BUILD_TESTING)
    add_executable(test_abi_layouts tests/test_abi_layouts.c)
    target_include_directories(test_abi_layouts PRIVATE include)
    target_compile_options(test_abi_layouts PRIVATE /W4 /WX /utf-8)
    set_target_properties(test_abi_layouts PROPERTIES
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    add_test(NAME abi_layouts COMMAND test_abi_layouts)

    # Carga la DLL exacta por ruta, sin enlazar con una copia accidental.
    add_executable(test_abi_exports tests/test_abi_exports.c)
    target_include_directories(test_abi_exports PRIVATE include)
    target_compile_options(test_abi_exports PRIVATE /W4 /WX /utf-8)
    target_link_libraries(test_abi_exports PRIVATE version)
    set_target_properties(test_abi_exports PROPERTIES
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    add_test(NAME abi_exports COMMAND test_abi_exports "$<TARGET_FILE:mace_native>")

    add_executable(test_telemetry_v2 tests/test_telemetry_v2.c)
    target_include_directories(test_telemetry_v2 PRIVATE include)
    target_compile_options(test_telemetry_v2 PRIVATE /W4 /WX /utf-8)
    target_link_libraries(test_telemetry_v2 PRIVATE mace_native)
    set_target_properties(test_telemetry_v2 PROPERTIES
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    add_test(NAME telemetry_v2 COMMAND test_telemetry_v2)

    add_executable(test_gpu_provider_dispatch tests/test_gpu_provider_dispatch.c
        src/telemetry/gpu_providers.c)
    target_include_directories(test_gpu_provider_dispatch PRIVATE include src/telemetry)
    target_compile_definitions(test_gpu_provider_dispatch PRIVATE MACE_HAS_ADLX=1)
    target_compile_options(test_gpu_provider_dispatch PRIVATE /W4 /WX /utf-8)
    target_link_libraries(test_gpu_provider_dispatch PRIVATE dxgi dxguid)
    set_target_properties(test_gpu_provider_dispatch PROPERTIES
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    add_test(NAME gpu_provider_dispatch COMMAND test_gpu_provider_dispatch)
endif()

# Prueba técnica opcional de detección automática; no se ejecuta en CI.
add_executable(hardware_probe tests/hardware_probe.c)
target_compile_options(hardware_probe PRIVATE /W4 /WX /utf-8)
target_link_libraries(hardware_probe PRIVATE dxgi dxguid)
set_target_properties(hardware_probe PROPERTIES
    MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

install(TARGETS mace_native RUNTIME DESTINATION bin ARCHIVE DESTINATION lib)
install(DIRECTORY include/ DESTINATION include FILES_MATCHING PATTERN "*.h")
install(DIRECTORY docs/ DESTINATION docs FILES_MATCHING PATTERN "*.md")
install(FILES README.md DESTINATION .)
