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

install(TARGETS mace_native RUNTIME DESTINATION bin ARCHIVE DESTINATION lib)
install(TARGETS test_mace RUNTIME DESTINATION bin)
install(DIRECTORY include/ DESTINATION include FILES_MATCHING PATTERN "*.h")
install(DIRECTORY docs/ DESTINATION docs FILES_MATCHING PATTERN "*.md")
install(FILES README.md DESTINATION .)
