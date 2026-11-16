# Pruebas conservadas de proveedores retirados

Proveedor retirado en la Subfase 1D; se conserva como referencia.

Código previo recuperable en `1b62000eddd0819258ceb33bf64eed69c2f09d44`.
Estos archivos `.txt` están fuera de CMake, CTest y Maven. No son proveedores
ni pruebas de producción; no se copian al instalador.

| Archivo | Propósito anterior | Motivo |
|---|---|---|
| 1D-1-lhm_fixture_cpu.c.txt | Sustituía la inicialización hardware conservando GetSystemTimes | Ya no existe esa inicialización; el fixture vigente utiliza directamente el módulo Windows |
| 1D-3-test_gpu_provider_dispatch.c.txt | Despacho con mock ADLX además de NVML/IGCL | Se conserva el original y se adapta el test vigente para conservar NVML/IGCL sin el proveedor retirado |
