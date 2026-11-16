# Pruebas conservadas de proveedores retirados

Proveedor retirado en la Subfase 1D; se conserva como referencia.

Código previo recuperable en `1b62000eddd0819258ceb33bf64eed69c2f09d44`.
Estos archivos `.txt` están fuera de CMake, CTest y Maven. No son proveedores
ni pruebas de producción; no se copian al instalador.

| Archivo | Propósito anterior | Motivo |
|---|---|---|
| 1D-1-lhm_fixture_cpu.c.txt | Sustituía la inicialización hardware conservando GetSystemTimes | Ya no existe esa inicialización; el fixture vigente utiliza directamente el módulo Windows |
