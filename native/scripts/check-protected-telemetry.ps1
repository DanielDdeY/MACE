param([string]$Baseline='1b62000eddd0819258ceb33bf64eed69c2f09d44')
$ErrorActionPreference='Stop'
$repoRoot=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$protected=@('native/src/telemetry/nvml_loader.c','native/src/telemetry/igcl_provider.c',
    'native/src/hardware/gpu_inventory.c','native/src/hardware/gpu_inventory.h',
    'native/include/mace_types.h','native/include/mace_types_v2.h')
& git -C $repoRoot diff --exit-code $Baseline -- @protected
if ($LASTEXITCODE -ne 0) { throw 'Cambió un archivo protegido.' }
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$beforeStatus=(& git -C $repoRoot show "${Baseline}:native/include/mace_status.h") -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'No se pudo leer el contrato de proveedores del respaldo.' }
$afterStatus=[IO.File]::ReadAllText((Join-Path $repoRoot 'native/include/mace_status.h'))
$providerPattern='(?m)^\s*MACE_PROVIDER_[A-Z_]+\s*=\s*[0-9]+'
$beforeIds=([regex]::Matches($beforeStatus,$providerPattern) | ForEach-Object { $_.Value.Trim() }) -join "`n"
$afterIds=([regex]::Matches($afterStatus,$providerPattern) | ForEach-Object { $_.Value.Trim() }) -join "`n"
if (-not $beforeIds -or $beforeIds -cne $afterIds) { throw 'Cambiaron los IDs publicos de proveedores.' }
foreach ($entry in @(
    @('native/src/telemetry/cpu_sensors.c','static float ReadCpuUsagePercent\(void\)'),
    @('native/src/telemetry/cpu_sensors.c','static unsigned long long filetime_to_u64\(const FILETIME\* ft\)'),
    @('native/src/mace_api.c','CP_API MaceStatus CP_PollSystemMemoryV2\('),
    @('native/src/telemetry/gpu_providers.c','static void windows_memory_capacity\(')
)) {
    $relative=$entry[0]
    $old=(& git -C $repoRoot show "${Baseline}:$relative") -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo leer el respaldo.' }
    $current=[IO.File]::ReadAllText((Join-Path $repoRoot $relative)).Replace("`r`n","`n")
    $pattern='(?ms)^'+$entry[1]+'.*?^}'
    $before=[regex]::Match($old,$pattern).Value
    $after=[regex]::Match($current,$pattern).Value
    if (-not $before -or $before -cne $after) { throw "Cambió el bloque protegido: $relative / $($entry[1])" }
}
Write-Host 'PASS: NVML, IGCL, DXGI, layouts, IDs publicos, ReadCpuUsagePercent, FILETIME y RAM intactos respecto al respaldo.'
