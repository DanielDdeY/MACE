# Contribuir a MACE

El código propio de MACE se publica bajo MIT. La elección permite derivados
cerrados. Las contribuciones de código propio se proponen bajo MIT, conservando
avisos y licencias de terceros. Declara origen/licencia del material ajeno;
si no tienes derecho a aportarlo, no lo incluyas ni retires sus atribuciones.

La licencia se verificó en la rama pública principal. Si tu checkout aún no
tiene LICENSE, coordina la sincronización con el mantenedor antes de preparar
una distribución; no reemplaces términos de terceros.
[LICENSE público](https://github.com/DanielDdeY/MACE/blob/main/LICENSE).

## Proponer y validar un cambio

1. Describe problema, alcance y resultado esperado; usa una rama y un PR.
2. Identifica archivos de producto, empaquetado, dependencias y documentación.
3. Conserva contratos congelados: DLL 1.5.0, ABI 1.5, protocolo 2, helper 1.1.1,
   estados 9–16 y política 4A. Cualquier cambio exige aprobación aparte.
4. Si afecta payload/metadatos, presenta antes su efecto en hashes, manifiestos
   y pins. No regeneres la imagen congelada como efecto implícito.
5. Usa los scripts versionados bajo `native/scripts/`, `helper/` y
   `packaging/tests/`, leyendo sus prerrequisitos. Registra lo ejecutado,
   fallos y contexto: normal/admin, desarrollo/producción, simulado/físico.
   Marca NO VERIFICADO lo que no comprobaste; no rebajes tests para aprobar.

No instales componentes ni ejecutes pruebas de kill, servicios o HVCI en un
equipo ajeno sin permiso y recuperación preparada. Las pruebas de instalación
se realizan en VM limpia con instantánea. Preview no es una prueba de hardware.

## Evidencia y origen

Antes de adjuntar logs elimina perfiles, nombres de usuario/equipo, SID,
secretos, correos personales y hashes completos. No publiques los TXT locales
excluidos ni cajas negras/volcados sin revisar su contenido. Las plantillas
no realizan envíos automáticos desde MACE.

El mantenedor revisa la propuesta antes de incorporarla. **DCO/sign-off no es
obligatorio actualmente**: no se exigen cambios al historial ni datos personales
adicionales. Una firma Git, un sign-off y Authenticode son mecanismos distintos.
Las opciones de DCO esperan decisión explícita del titular.

La revisión legal de redistribución y la admisión de SignPath siguen pendientes.
No envíes binarios ni alteres política/pipeline de firma como parte implícita
de una contribución.
