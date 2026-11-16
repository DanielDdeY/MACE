> El documento anterior íntegro se conserva en [el histórico](../../docs/historico/native/pre1D-native__docs__AMD_CPU_TEMPERATURE.md). Corresponde al respaldo `1b62000`.

# Sensores CPU AMD

Temperatura y potencia proceden del helper LHM/PawnIO. Se exige proceso elevado,
PawnIO Running, helper Ready, muestra coherente y edad máxima de 3000 ms. Si falta
acceso se informa NO_PROVIDER; un cero CPU no verificable no pasa a VALID.
El uso global continúa mediante GetSystemTimes y no depende del helper.

La CPU AMD local está confirmada por el usuario en la UI elevada y mediante la
prueba -RealHelper -RequireCpu. Otros modelos, portátiles y HVCI: NO VERIFICADO.
Las instrucciones vigentes están en ../../helper/RESULTADOS-1C.md y RESULTADOS-1D-PASO1.md.
