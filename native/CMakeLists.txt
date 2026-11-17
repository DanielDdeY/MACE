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
option(MACE_LHM_DEVELOPMENT "Allow helper path/hash overrides (never ship this profile)" OFF)
option(MACE_LHM_REQUIRE_AUTHENTICODE "Require cached Authenticode validation (certificate pending)" OFF)
set(MACE_LHM_RUNTIME_MANIFEST "" CACHE FILEPATH "Trusted, final helper runtime manifest after signing")
if (NOT MACE_LHM_DEVELOPMENT AND NOT EXISTS "${MACE_LHM_RUNTIME_MANIFEST}")
    message(FATAL_ERROR "Production requires -DMACE_LHM_RUNTIME_MANIFEST=<published helper/helper-runtime.manifest> or use explicit development profile")
endif()
set(MACE_LHM_MANIFEST_SHA256 "")
if (EXISTS "${MACE_LHM_RUNTIME_MANIFEST}")
    file(SHA256 "${MACE_LHM_RUNTIME_MANIFEST}" MACE_LHM_MANIFEST_SHA256)
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${MACE_LHM_RUNTIME_MANIFEST}")
endif()
configure_file(cmake/lhm_security_config.h.in "${CMAKE_CURRENT_BINARY_DIR}/generated/lhm_security_config.h" @ONLY)
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
    src/telemetry/legacy_telemetry.c
    src/telemetry/lhm_wire.c
    src/telemetry/lhm_transport.c
    src/telemetry/lhm_trust.c
    src/telemetry/lhm_receiver.c
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
target_compile_definitions(mace_native PRIVATE MACE_EXPORTS
    _CRT_SECURE_NO_WARNINGS UNICODE _UNICODE)
target_compile_options(mace_native PRIVATE /W4 /WX /utf-8)
target_link_libraries(mace_native PRIVATE kernel32 psapi user32 dxgi dxguid gdi32 shell32 ole32 advapi32 bcrypt wintrust crypt32)
set_target_properties(mace_native PROPERTIES
    OUTPUT_NAME mace_native
    PREFIX ""
    MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>"
)

# Demostración manual de telemetría V2 y procesos; depende del equipo.
add_executable(test_mace test_main.c)
add_executable(Test_MACE_new test_main.c)
target_include_directories(Test_MACE_new PRIVATE include)
target_compile_options(Test_MACE_new PRIVATE /W4 /WX /utf-8)
target_link_libraries(Test_MACE_new PRIVATE mace_native advapi32)
set_target_properties(Test_MACE_new PROPERTIES MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
add_executable(test_lhm_live tests/test_lhm_live.c)
target_include_directories(test_lhm_live PRIVATE include)
target_compile_options(test_lhm_live PRIVATE /W4 /WX /utf-8)
target_link_libraries(test_lhm_live PRIVATE mace_native)
set_target_properties(test_lhm_live PROPERTIES MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
target_include_directories(test_mace PRIVATE include)
target_compile_options(test_mace PRIVATE /W4 /WX /utf-8)
target_link_libraries(test_mace PRIVATE mace_native advapi32)
set_target_properties(test_mace PROPERTIES
    MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

include(CTest)
if (BUILD_TESTING)
    # Real API/runtime/receiver with deterministic inventory/providers; never installed.
    set(LHM_FIXTURE_SOURCES ${MACE_NATIVE_SOURCES})
    list(REMOVE_ITEM LHM_FIXTURE_SOURCES src/telemetry/nvml_loader.c
        src/telemetry/igcl_provider.c src/telemetry/gpu_providers.c src/hardware/gpu_inventory.c
        src/telemetry/cpu_sensors.c)
    add_library(mace_native_lhm_test SHARED ${LHM_FIXTURE_SOURCES}
        tests/lhm_fixture_providers.c tests/lhm_fixture_cpu.c)
    target_include_directories(mace_native_lhm_test PRIVATE include src/process
        "${CMAKE_CURRENT_BINARY_DIR}/generated")
    target_compile_definitions(mace_native_lhm_test PRIVATE MACE_EXPORTS _CRT_SECURE_NO_WARNINGS UNICODE _UNICODE MACE_LHM_DEVELOPMENT=1 MACE_LHM_REQUIRE_AUTHENTICODE=0)
    target_link_libraries(mace_native_lhm_test PRIVATE psapi user32 dxgi dxguid gdi32 shell32 ole32 advapi32 bcrypt wintrust crypt32)
    add_executable(lhm_fake_helper tests/lhm_fake_helper.c src/telemetry/lhm_wire.c)
    target_include_directories(lhm_fake_helper PRIVATE include)
    target_link_libraries(lhm_fake_helper PRIVATE advapi32)
    add_executable(test_lhm_receiver tests/test_lhm_receiver.c)
    target_include_directories(test_lhm_receiver PRIVATE include)
    target_link_libraries(test_lhm_receiver PRIVATE mace_native_lhm_test)
    foreach(target mace_native_lhm_test lhm_fake_helper test_lhm_receiver)
        target_compile_options(${target} PRIVATE /W4 /WX /utf-8)
        target_compile_definitions(${target} PRIVATE UNICODE _UNICODE)
        set_target_properties(${target} PROPERTIES MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    endforeach()
    add_executable(test_lhm_job tests/test_lhm_job.c src/telemetry/lhm_transport.c src/telemetry/lhm_trust.c)
    target_include_directories(test_lhm_job PRIVATE include "${CMAKE_CURRENT_BINARY_DIR}/generated")
    target_compile_definitions(test_lhm_job PRIVATE MACE_LHM_DEVELOPMENT=1 MACE_LHM_REQUIRE_AUTHENTICODE=0)
    target_link_libraries(test_lhm_job PRIVATE mace_native_lhm_test advapi32 bcrypt shell32 ole32 wintrust crypt32)
    add_executable(test_lhm_shutdown tests/test_lhm_shutdown.c)
    target_include_directories(test_lhm_shutdown PRIVATE include)
    target_link_libraries(test_lhm_shutdown PRIVATE mace_native_lhm_test)
    foreach(target test_lhm_job test_lhm_shutdown)
        target_compile_options(${target} PRIVATE /W4 /WX /utf-8)
        target_compile_definitions(${target} PRIVATE UNICODE _UNICODE)
        set_target_properties(${target} PROPERTIES MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    endforeach()
    foreach(scenario contained forced-close descendants abrupt-parent restrictive-job control-close eof-close foreign-homonym)
        add_test(NAME lhm_job_${scenario} COMMAND test_lhm_job "$<TARGET_FILE:lhm_fake_helper>" ${scenario})
        set_tests_properties(lhm_job_${scenario} PROPERTIES TIMEOUT 20)
    endforeach()
    add_test(NAME lhm_shutdown_budget COMMAND test_lhm_shutdown "$<TARGET_FILE:lhm_fake_helper>")
    set_tests_properties(lhm_shutdown_budget PROPERTIES TIMEOUT 20)
    add_executable(lhm_secure_fake_helper tests/lhm_fake_helper.c src/telemetry/lhm_wire.c)
    target_include_directories(lhm_secure_fake_helper PRIVATE include)
    target_compile_definitions(lhm_secure_fake_helper PRIVATE MACE_SECURE_TEST_HELPER=1 UNICODE _UNICODE)
    target_compile_options(lhm_secure_fake_helper PRIVATE /W4 /WX /utf-8)
    target_link_libraries(lhm_secure_fake_helper PRIVATE advapi32)
    set(security_fixture "${CMAKE_CURRENT_BINARY_DIR}/security-fixture")
    add_custom_command(OUTPUT "${security_fixture}/lhm_security_config.h"
        COMMAND powershell -NoProfile -ExecutionPolicy Bypass -File "${CMAKE_CURRENT_SOURCE_DIR}/scripts/make-lhm-security-fixture.ps1"
            -HelperExe "$<TARGET_FILE:lhm_secure_fake_helper>" -OutputDirectory "${security_fixture}"
        DEPENDS lhm_secure_fake_helper "${CMAKE_CURRENT_SOURCE_DIR}/scripts/make-lhm-security-fixture.ps1" VERBATIM)
    add_custom_target(lhm_security_fixture DEPENDS "${security_fixture}/lhm_security_config.h")
    add_library(mace_native_secure_test SHARED ${LHM_FIXTURE_SOURCES} tests/lhm_fixture_providers.c tests/lhm_fixture_cpu.c)
    add_dependencies(mace_native_secure_test lhm_security_fixture)
    target_include_directories(mace_native_secure_test PRIVATE "${security_fixture}" include src/process "${CMAKE_CURRENT_BINARY_DIR}/generated")
    target_compile_definitions(mace_native_secure_test PRIVATE MACE_EXPORTS _CRT_SECURE_NO_WARNINGS UNICODE _UNICODE)
    target_compile_options(mace_native_secure_test PRIVATE /W4 /WX /utf-8)
    target_link_libraries(mace_native_secure_test PRIVATE psapi user32 dxgi dxguid gdi32 shell32 ole32 advapi32 bcrypt wintrust crypt32)
    set_target_properties(mace_native_secure_test PROPERTIES RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/security")
    add_executable(test_lhm_security tests/test_lhm_security.c)
    target_include_directories(test_lhm_security PRIVATE include)
    target_compile_definitions(test_lhm_security PRIVATE UNICODE _UNICODE)
    target_compile_options(test_lhm_security PRIVATE /W4 /WX /utf-8)
    target_link_libraries(test_lhm_security PRIVATE mace_native_secure_test)
    set_target_properties(test_lhm_security PROPERTIES RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/security")
    foreach(scenario valid ignore-env missing manifest tampered extra locks relaunch)
        add_test(NAME lhm_security_${scenario} COMMAND test_lhm_security "$<TARGET_FILE:lhm_fake_helper>" "${security_fixture}/runtime" ${scenario})
        set_tests_properties(lhm_security_${scenario} PROPERTIES TIMEOUT 20 RESOURCE_LOCK lhm_security_files)
    endforeach()
    foreach(target lhm_secure_fake_helper mace_native_secure_test test_lhm_security)
        set_target_properties(${target} PROPERTIES MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    endforeach()
    if (NOT MACE_LHM_DEVELOPMENT)
        add_executable(test_lhm_production_profile tests/test_lhm_production_profile.c)
        target_include_directories(test_lhm_production_profile PRIVATE include)
        target_compile_options(test_lhm_production_profile PRIVATE /W4 /WX /utf-8)
        target_compile_definitions(test_lhm_production_profile PRIVATE UNICODE _UNICODE)
        set_target_properties(test_lhm_production_profile PROPERTIES MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
        add_test(NAME lhm_actual_production_ignores_environment COMMAND test_lhm_production_profile "$<TARGET_FILE:mace_native>" "$<TARGET_FILE:lhm_fake_helper>")
        set_tests_properties(lhm_actual_production_ignores_environment PROPERTIES TIMEOUT 15)
    endif()
    foreach(scenario slow valid stale stale-arrival bad-pid wrong-client malformed disconnect
        no-elevation driver-missing zero-cpu zero-unprivileged unknown-power unknown-gpu duplicate-pci replay nan fragmented fallback
        gpu-zero bad-server-pid hash-mismatch init-race amd-chip
        missing-helper-default missing-helper-explicit missing-helper-duplicate driver-stopped old-version)
        add_test(NAME lhm_${scenario} COMMAND test_lhm_receiver "$<TARGET_FILE:lhm_fake_helper>" ${scenario})
        set_tests_properties(lhm_${scenario} PROPERTIES TIMEOUT 25)
    endforeach()
    add_executable(test_lhm_wire tests/test_lhm_wire.c src/telemetry/lhm_wire.c)
    target_include_directories(test_lhm_wire PRIVATE include)
    target_compile_options(test_lhm_wire PRIVATE /W4 /WX /utf-8)
    set_target_properties(test_lhm_wire PROPERTIES MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    add_test(NAME lhm_wire COMMAND test_lhm_wire)
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
