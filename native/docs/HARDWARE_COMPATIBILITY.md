> El documento anterior íntegro se conserva en [el histórico](../../docs/historico/native/pre1D-native__docs__HARDWARE_COMPATIBILITY.md). Corresponde al respaldo `1b62000`.

# Cobertura actual

| Plataforma | Evidencia | Límites |
|---|---|---|
| CPU AMD local | Temperatura y potencia elevadas confirmadas por el usuario; uso Windows medido | Otros modelos y HVCI NO VERIFICADO |
| Radeon RX 5500 XT PCI 1002:7340 local | Temperatura, potencia GPU_CHIP, uso, RPM y VRAM LHM observados | Otras placas/controladores NO VERIFICADO |
| Otras Radeon | Sensores generales condicionados a LHM; potencia N/D fuera de 7340 | NO VERIFICADO físicamente |
| CPU Intel | Código LHM/PawnIO y Windows | NO VERIFICADO físicamente |
| NVIDIA | NVML principal y LHM secundario | Ambos NO VERIFICADO físicamente |
| GPU Intel | IGCL principal y LHM secundario | NO VERIFICADO físicamente |
| PawnIO ausente en equipo limpio | Simulación de ausencia/detención | NO VERIFICADO físicamente |

No se demuestra equivalencia general por retirar alternativas. Véanse las
evidencias y las restricciones de potencia en ../../helper/COBERTURA-AMD-POTENCIA.md.
