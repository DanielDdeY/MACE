MACE - biblioteca nativa

Para ejecutar la interfaz con datos reales, coloca en esta carpeta:

    mace_native.dll

La DLL debe corresponder a la misma versión de los contratos C/FFM del proyecto
y a la arquitectura x64.

Alternativas:
- Define la variable de entorno MACE_NATIVE_LIB con la ruta completa de la DLL.
- Compila native/ con CMake; NativeLibraryLocator también busca en las carpetas
  habituales native/build/Release, native/build/Debug, etc.

La aplicación arranca en modo nativo por defecto. Para forzar datos simulados:
    -Dmace.infra.mode=mock
