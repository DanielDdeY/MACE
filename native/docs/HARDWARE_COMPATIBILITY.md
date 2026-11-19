> El documento anterior íntegro se conserva en [el histórico](../../docs/historico/native/pre1D-native__docs__HARDWARE_COMPATIBILITY.md). Corresponde al respaldo `1b62000`.

# Cobertura actual

| Plataforma | Evidencia | Límites |
|---|---|---|
| CPU AMD local | Temperatura y potencia elevadas confirmadas por el usuario; uso Windows medido | Otros modelos y HVCI NO VERIFICADO |
| Radeon RX 5500 XT PCI 1002:7340 local | Temperatura, potencia GPU_CHIP, uso, RPM y VRAM LHM observados | Otras placas/controladores NO VERIFICADO |
| Otras Radeon | Sensores generales condicionados a LHM; potencia N/D fuera de 7340 | NO VERIFICADO físicamente |
| CPU Intel Core i5-8250U (laptop) | Temperatura 57.0 °C mostrada por MACE vía LHM con PawnIO 2.2.0 y MACE como administrador; confirmada por el usuario el 2026-11-19 | Potencia no registrada; otros modelos Intel y HVCI NO VERIFICADO |
| Equipo Intel + NVIDIA | MACE con datos reales de CPU y GPU, confirmado por el usuario el 2026-11-19 | Modelos no registrados; no se registró si la GPU se leyó por NVML o por LHM |
| GPU Intel UHD 620 (misma laptop) | MACE ejecutado en el equipo; IGCL principal y LHM secundario | Lecturas de GPU no registradas: NO VERIFICADO sensor a sensor |
| PawnIO ausente en equipo limpio | Simulación de ausencia/detención | NO VERIFICADO físicamente |

No se demuestra equivalencia general por retirar alternativas. Véanse las
evidencias y las restricciones de potencia en ../../helper/COBERTURA-AMD-POTENCIA.md.
