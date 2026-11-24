> El documento anterior íntegro se conserva en [el histórico](../../docs/historico/native/pre1D-native__docs__PHASE2.md). Corresponde al respaldo `1b62000`.

# Telemetría vigente después de Subfase 1D

CPU temperatura/potencia y sensores AMD GPU usan LHM mediante el receptor privado.
CPU uso sigue en GetSystemTimes. NVIDIA mantiene NVML e Intel GPU mantiene IGCL;
ambos son primarios por adaptador y no se completan por métrica con LHM.
RAM y el inventario GPU siguen usando Windows/DXGI.

El sondeo solo consulta cachés; nunca espera al helper. Sin helper se informa
NO_PROVIDER. La capacidad total DXGI puede mantenerse como dato Windows cuando
la identidad es única y la caché es válida; no valida temperatura, uso o potencia.
Muestras caducadas/fallos de transporte se invalidan y GPU ambigua queda N/D.
Los ceros GPU legítimos siguen permitidos; porcentaje y RPM son independientes.

ABI 1.4 y registros V2 no cambian. La potencia AMD solo se admite en PCI 1002:7340
con las guardias de 1C. La tabla física está en HARDWARE_COMPATIBILITY.md.
