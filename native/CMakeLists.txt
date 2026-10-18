cmake_minimum_required(VERSION 3.28)
project(mace_native VERSION 1.4.2 LANGUAGES C RC)

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
    src/internal/mace_runtime.c
    src/process/process_enumerator.c
    src/process/process_paths.c
    src/process/process_security.c
    src/process/process_identity.c
    src/process/process_api_v2.c
    src/process/process_samples.c
    src/process/process_controller.c
    src/telemetry/nvml_loader.c
    src/telemetry/igcl_provider.c
    src/telemetry/gpu_providers.c
    src/telemetry/cpu_sensors.c
    src/hardware/gpu_inventory.c
    src/extensions/hang_detector.c
    src/extensions/shared_buffer.c
    src/extensions/blackbox_buffer.c
    "${CMAKE_CURRENT_BINARY_DIR}/generated/mace_native.rc"
)

add_library(mace_native SHARED ${MACE_NATIVE_SOURCES})
target_include_directories(mace_native PRIVATE include
    src/process
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

set(MACE_RYZEN_SDK_DIR "" CACHE PATH "External Ryzen Master Monitoring SDK root (contains include/ICPUEx.h)")
if (MACE_RYZEN_SDK_DIR)
    if (NOT EXISTS "${MACE_RYZEN_SDK_DIR}/include/ICPUEx.h" OR
        NOT EXISTS "${MACE_RYZEN_SDK_DIR}/include/IPlatform.h")
        message(FATAL_ERROR "MACE_RYZEN_SDK_DIR does not contain the Ryzen Master Monitoring SDK headers")
    endif()
    execute_process(COMMAND powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass
        -File "${CMAKE_CURRENT_SOURCE_DIR}/scripts/check-ryzen-sdk-version.ps1"
        "${MACE_RYZEN_SDK_DIR}"
        RESULT_VARIABLE MACE_RYZEN_VERSION_RESULT
        OUTPUT_VARIABLE MACE_RYZEN_VERSION_OUTPUT
        ERROR_VARIABLE MACE_RYZEN_VERSION_ERROR)
    if (NOT MACE_RYZEN_VERSION_RESULT EQUAL 0)
        message(FATAL_ERROR "Unsupported Ryzen Master Monitoring SDK: ${MACE_RYZEN_VERSION_ERROR}")
    endif()
    enable_language(CXX)
    set_target_properties(mace_native PROPERTIES
        CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON CXX_EXTENSIONS OFF)
    target_sources(mace_native PRIVATE src/telemetry/amd_ryzen_provider.cpp)
    target_include_directories(mace_native SYSTEM PRIVATE "${MACE_RYZEN_SDK_DIR}/include")
    target_compile_definitions(mace_native PRIVATE MACE_HAS_RYZEN_SDK=1)
    target_link_libraries(mace_native PRIVATE advapi32 version)
    message(STATUS "AMD Ryzen CPU temperature provider enabled (external SDK headers)")
else()
    message(STATUS "AMD Ryzen CPU temperature disabled: set MACE_RYZEN_SDK_DIR to the official SDK")
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
target_link_libraries(test_mace PRIVATE mace_native advapi32)
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

    add_executable(test_process_security tests/test_process_security.c
        src/process/process_security.c src/process/process_identity.c)
    target_include_directories(test_process_security PRIVATE include src/process)
    target_compile_options(test_process_security PRIVATE /W4 /WX /utf-8)
    set_target_properties(test_process_security PROPERTIES
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    add_test(NAME process_security COMMAND test_process_security)

    add_executable(test_process_api tests/test_process_api.c)
    target_include_directories(test_process_api PRIVATE include)
    target_compile_options(test_process_api PRIVATE /W4 /WX /utf-8)
    target_link_libraries(test_process_api PRIVATE mace_native)
    set_target_properties(test_process_api PROPERTIES
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    add_test(NAME process_api COMMAND test_process_api)

    add_executable(test_process_extensions tests/test_process_extensions.c)
    target_include_directories(test_process_extensions PRIVATE include)
    target_compile_options(test_process_extensions PRIVATE /W4 /WX /utf-8)
    target_link_libraries(test_process_extensions PRIVATE mace_native)
    set_target_properties(test_process_extensions PROPERTIES
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    add_test(NAME process_extensions COMMAND test_process_extensions)

    add_executable(test_blackbox_buffer tests/test_blackbox_buffer.c)
    target_include_directories(test_blackbox_buffer PRIVATE include)
    target_compile_options(test_blackbox_buffer PRIVATE /W4 /WX /utf-8)
    target_link_libraries(test_blackbox_buffer PRIVATE mace_native)
    set_target_properties(test_blackbox_buffer PROPERTIES
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    add_test(NAME blackbox_buffer COMMAND test_blackbox_buffer)

    add_executable(test_lifecycle tests/test_lifecycle.c)
    target_include_directories(test_lifecycle PRIVATE include)
    target_compile_options(test_lifecycle PRIVATE /W4 /WX /utf-8)
    target_link_libraries(test_lifecycle PRIVATE mace_native psapi)
    set_target_properties(test_lifecycle PROPERTIES
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    add_test(NAME lifecycle COMMAND test_lifecycle)

    add_executable(test_concurrency tests/test_concurrency.c)
    target_include_directories(test_concurrency PRIVATE include)
    target_compile_options(test_concurrency PRIVATE /W4 /WX /utf-8)
    target_link_libraries(test_concurrency PRIVATE mace_native)
    set_target_properties(test_concurrency PROPERTIES
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    add_test(NAME concurrency COMMAND test_concurrency)

    add_executable(test_buffer_concurrency tests/test_buffer_concurrency.c)
    target_include_directories(test_buffer_concurrency PRIVATE include)
    target_compile_options(test_buffer_concurrency PRIVATE /W4 /WX /utf-8)
    target_link_libraries(test_buffer_concurrency PRIVATE mace_native)
    set_target_properties(test_buffer_concurrency PROPERTIES
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    add_test(NAME buffer_concurrency COMMAND test_buffer_concurrency)

    option(MACE_TEST_PROCESS_TERMINATION
        "Run an explicit termination test against a helper created by CTest" OFF)
    if (MACE_TEST_PROCESS_TERMINATION)
        add_executable(process_helper tests/process_helper.c)
        add_executable(test_process_termination tests/test_process_termination.c)
        target_include_directories(test_process_termination PRIVATE include)
        target_link_libraries(test_process_termination PRIVATE mace_native)
        target_compile_options(process_helper PRIVATE /W4 /WX /utf-8)
        target_compile_options(test_process_termination PRIVATE /W4 /WX /utf-8)
        set_target_properties(process_helper test_process_termination PROPERTIES
            MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
        add_dependencies(test_process_termination process_helper)
        add_test(NAME process_termination COMMAND test_process_termination)
    endif()
endif()

# Prueba técnica opcional de detección automática; no se ejecuta en CI.
add_executable(hardware_probe tests/hardware_probe.c)
target_compile_options(hardware_probe PRIVATE /W4 /WX /utf-8)
target_link_libraries(hardware_probe PRIVATE dxgi dxguid)
set_target_properties(hardware_probe PROPERTIES
    MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

install(TARGETS mace_native RUNTIME DESTINATION bin ARCHIVE DESTINATION lib)
install(TARGETS test_mace RUNTIME DESTINATION bin)
install(DIRECTORY include/ DESTINATION include FILES_MATCHING PATTERN "*.h")
install(DIRECTORY docs/ DESTINATION docs FILES_MATCHING PATTERN "*.md")
install(FILES README.md DESTINATION .)
