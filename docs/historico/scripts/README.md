# Comprobadores retirados

Proveedor retirado en la Subfase 1D; se conserva como referencia.

`check-ryzen-sdk-version.ps1.txt` comprobaba las DLL Platform/Device del SDK de
CPU AMD. Existía en el respaldo `1b62000eddd0819258ceb33bf64eed69c2f09d44`.
Se retira al desaparecer ese proveedor. Es texto histórico y no se invoca desde
CMake, pruebas o empaquetado. No se distribuye con el producto.
