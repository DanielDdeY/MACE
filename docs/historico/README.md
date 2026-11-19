# Archivo de desarrollo previo a Subfase 1D

Proveedor retirado en la Subfase 1D; se conserva como referencia.

Respaldo: `1b62000eddd0819258ceb33bf64eed69c2f09d44` en la rama LHM_implementado.
native/ conserva íntegros los documentos mixtos antes de actualizar sus rutas
operativas; pruebas-retiradas/ conserva fixtures/mocks originales como texto y
scripts/ conserva el comprobador sin ejecución. No se compilan ni se empaquetan.
Las rutas relativas dentro de los originales corresponden a su ubicación previa;
para reproducirlos se recupera el commit indicado, con sus dependencias externas.

No se modificó el contenido para anonimizarlo. Las evidencias originales pueden
describir el hardware y las herramientas locales de aquellas fechas. No acreditan
soporte actual ni deben utilizarse para activar proveedores en la revisión vigente.

Comandos propuestos, no ejecutados:

```powershell
git tag -a pre-1D 1b62000eddd0819258ceb33bf64eed69c2f09d44 -m "Respaldo anterior a Subfase 1D"
git branch archivo/pre-1D 1b62000eddd0819258ceb33bf64eed69c2f09d44
```
