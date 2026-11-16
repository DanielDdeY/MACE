> El documento anterior íntegro se conserva en [el histórico](../../docs/historico/native/pre1D-native__docs__TESTING.md). Corresponde al respaldo `1b62000`.

# Pruebas nativas

Desde la raíz del repositorio:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\helper\Build-Helper.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-lhm1b.ps1 -BuildDirectory .\native\build\vs2026-lhm1b
```

CTest conserva ABI, uso Windows, RAM, procesos, buffers, concurrencia, receptor y
dispatch NVML/IGCL. Los cuatro casos nuevos cubren helper predeterminado ausente,
ruta explícita ausente, ausencia con PCI duplicada y PawnIO Stopped. Missing,
falta de elevación, caducidad, replay, identidad y ceros legítimos siguen cubiertos.

Con hardware y el helper publicado, añadir -RealHelper; desde consola elevada,
añadir -RequireCpu para exigir temperatura/potencia CPU válidas. No instala drivers.
Test_MACE_new.exe es un diagnóstico de desarrollo, excluido del instalador final.
Las pruebas Java requieren DLL/hash/helper configurados como en el informe 1C.
