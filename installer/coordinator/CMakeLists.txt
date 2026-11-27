cmake_minimum_required(VERSION 3.28)
project(mace_pawnio_coordinator VERSION 1.0.0 LANGUAGES C)

# Coordinador del instalador de MACE para el requisito PawnIO (Fase 3, D2/D3).
# No forma parte de la telemetria: solo detecta, verifica y ejecuta el setup
# oficial intacto de PawnIO. Ver installer/README.md.

if (NOT WIN32 OR NOT CMAKE_C_COMPILER_ID STREQUAL "MSVC")
    message(FATAL_ERROR "El coordinador requiere Windows con MSVC")
endif()
if (NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
    message(FATAL_ERROR "El coordinador requiere un generador x64")
endif()

set(CMAKE_C_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)
# CRT estatico: el equipo destino no necesita el redistribuible de Visual C++.
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

# Pines tomados de la misma politica que usa el empaquetado (stage-runtime.ps1).
set(MACE_PAWNIO_POLICY "${CMAKE_CURRENT_SOURCE_DIR}/../../packaging/pawnio-2.2.0.json"
    CACHE FILEPATH "Politica fijada del setup oficial de PawnIO")
set(MACE_PAWNIO_SIGNER "namazso.eu" CACHE STRING "Nombre simple esperado del firmante Authenticode")

file(READ "${MACE_PAWNIO_POLICY}" PAWNIO_JSON)
string(JSON PAWNIO_SHA256 GET "${PAWNIO_JSON}" sha256)
string(JSON PAWNIO_VERSION GET "${PAWNIO_JSON}" version)
string(TOLOWER "${PAWNIO_SHA256}" PAWNIO_SHA256)
if (NOT PAWNIO_SHA256 MATCHES "^[0-9a-f]+$")
    message(FATAL_ERROR "SHA-256 de PawnIO invalido en ${MACE_PAWNIO_POLICY}")
endif()
string(LENGTH "${PAWNIO_SHA256}" PAWNIO_SHA256_LEN)
if (NOT PAWNIO_SHA256_LEN EQUAL 64)
    message(FATAL_ERROR "SHA-256 de PawnIO debe tener 64 caracteres")
endif()
if (NOT PAWNIO_VERSION MATCHES "^[0-9]+\\.[0-9]+\\.[0-9]+(\\.[0-9]+)?$")
    message(FATAL_ERROR "Version de PawnIO invalida: ${PAWNIO_VERSION}")
endif()
string(REPLACE "." ";" PAWNIO_VERSION_PARTS "${PAWNIO_VERSION}")
list(LENGTH PAWNIO_VERSION_PARTS PAWNIO_VERSION_COUNT)
if (PAWNIO_VERSION_COUNT EQUAL 3)
    list(APPEND PAWNIO_VERSION_PARTS 0)
endif()
list(GET PAWNIO_VERSION_PARTS 0 PAWNIO_V0)
list(GET PAWNIO_VERSION_PARTS 1 PAWNIO_V1)
list(GET PAWNIO_VERSION_PARTS 2 PAWNIO_V2)
list(GET PAWNIO_VERSION_PARTS 3 PAWNIO_V3)
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${MACE_PAWNIO_POLICY}")

file(MAKE_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/generated")
configure_file(src/pawnio_pins.h.in "${CMAKE_CURRENT_BINARY_DIR}/generated/pawnio_pins.h" @ONLY)

function(mace_coordinator target)
    add_executable(${target} src/pawnio_coordinator.c)
    target_include_directories(${target} PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/generated")
    target_compile_definitions(${target} PRIVATE UNICODE _UNICODE)
    target_compile_options(${target} PRIVATE /W4 /WX /sdl /guard:cf)
    target_link_options(${target} PRIVATE /guard:cf /DYNAMICBASE /NXCOMPAT)
    target_link_libraries(${target} PRIVATE bcrypt wintrust crypt32 version advapi32)
endfunction()

# Producto: pines fijos, sin ganchos de prueba.
mace_coordinator(mace-pawnio-setup)

# Solo pruebas: nunca se empaqueta (packaging/ toma unicamente mace-pawnio-setup.exe).
option(MACE_COORDINATOR_TESTS "Compilar binario con ganchos de prueba y setup simulado" ON)
if (MACE_COORDINATOR_TESTS)
    mace_coordinator(mace-pawnio-setup-test)
    target_compile_definitions(mace-pawnio-setup-test PRIVATE MACE_COORDINATOR_TEST_HOOKS=1)

    add_executable(stub_setup tests/stub_setup.c)
    target_compile_definitions(stub_setup PRIVATE UNICODE _UNICODE)
    target_compile_options(stub_setup PRIVATE /W4 /WX /sdl)
endif()
