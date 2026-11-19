> El documento anterior íntegro se conserva en [el histórico](../../docs/historico/native/pre1D-native__docs__HARDWARE_COMPATIBILITY.md). Corresponde al respaldo `1b62000`.

# Cobertura actual

| Plataforma | Evidencia | Límites |
|---|---|---|
| CPU AMD local | Temperatura y potencia elevadas confirmadas por el usuario; uso Windows medido | Otros modelos y HVCI NO VERIFICADO |
| Radeon RX 5500 XT PCI 1002:7340 local | Temperatura, potencia GPU_CHIP, uso, RPM y VRAM LHM observados | Otras placas/controladores NO VERIFICADO |
| Otras Radeon | Sensores generales condicionados a LHM; potencia N/D fuera de 7340 | NO VERIFICADO físicamente |
| CPU Intel Core i5-8250U (laptop) | Temperatura 57.0 °C mostrada por MACE vía LHM con PawnIO 2.2.0 y MACE como administrador; confirmada por el usuario el 2026-11-19 | Potencia no registrada; otros modelos Intel y HVCI NO VERIFICADO |
| CPU Intel Core Ultra 5 225F (escritorio) | Medición 2026-11-16 con `03-Medir.ps1` (sensores LHM, 3 ejecuciones × 15 muestras): temperatura por núcleo P/E, Package 46–57 °C, Core Max y Average; potencia Package 14.9–25.2 W y Cores 6.8–16.8 W | CPU Memory y CPU Platform devuelven 0 W; lectura de LHM, no del panel de MACE; HVCI NO VERIFICADO |
| NVIDIA GeForce RTX 5060 (mismo equipo) | Misma medición: GPU Core 33.3–33.6 °C, Memory Junction 42 °C, Package 12.7–13.4 W | Ventilador en 0 RPM y 0 % (reposo o sin sensor: no distinguido); camino NVML del panel de MACE NO VERIFICADO |
| NVIDIA (otros modelos) | NVML principal y LHM secundario | NO VERIFICADO físicamente |
| GPU Intel UHD 620 (misma laptop) | MACE ejecutado en el equipo; IGCL principal y LHM secundario | Lecturas de GPU no registradas: NO VERIFICADO sensor a sensor |
| PawnIO ausente en equipo limpio | Simulación de ausencia/detención | NO VERIFICADO físicamente |

No se demuestra equivalencia general por retirar alternativas. Véanse las
evidencias y las restricciones de potencia en ../../helper/COBERTURA-AMD-POTENCIA.md.
