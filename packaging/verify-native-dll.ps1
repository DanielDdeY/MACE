# ============================================================================
#  MACE - Verificacion de la DLL nativa antes de jpackage
# ----------------------------------------------------------------------------
#  Comprueba que la DLL a empaquetar es exactamente la del paquete verificado
#  por Dev 1 (SHA-256) y que su ABI es compatible con el puente FFM, cargandola
#  con el mismo codigo Java que usara la aplicacion (NativeAbiVerifier).
#
#  Orden de busqueda del hash de referencia:
#    1. -ExpectedSha256 (o variable MACE_NATIVE_SHA256)
#    2. build-info.json del paquete (campo dllSha256), junto a bin\
#    3. SHA256SUMS.txt del paquete (linea bin/mace_native.dll)
#  Sin hash de referencia la verificacion falla: no se empaqueta una DLL anonima.
# ============================================================================
param(
    [Parameter(Mandatory = $true)][string]$DllPath,
    [Parameter(Mandatory = $true)][string]$AppJar,
    [Parameter(Mandatory = $true)][string]$JavaExe,
    [Parameter(Mandatory = $true)][string]$ReportPath,
    [string]$ExpectedSha256 = $env:MACE_NATIVE_SHA256
)

$ErrorActionPreference = 'Stop'

try {
    $dll = (Resolve-Path -LiteralPath $DllPath).Path
    $packageRoot = Split-Path -Parent (Split-Path -Parent $dll)
    $buildInfoPath = Join-Path $packageRoot 'build-info.json'
    $sumsPath = Join-Path $packageRoot 'SHA256SUMS.txt'
    $buildInfo = $null
    if (Test-Path -LiteralPath $buildInfoPath -PathType Leaf) {
        $buildInfo = Get-Content -LiteralPath $buildInfoPath -Raw | ConvertFrom-Json
    }

    $source = 'parametro/MACE_NATIVE_SHA256'
    if ([string]::IsNullOrWhiteSpace($ExpectedSha256) -and $buildInfo -and $buildInfo.dllSha256) {
        $ExpectedSha256 = $buildInfo.dllSha256
        $source = $buildInfoPath
    }
    if ([string]::IsNullOrWhiteSpace($ExpectedSha256) -and (Test-Path -LiteralPath $sumsPath -PathType Leaf)) {
        $line = Get-Content -LiteralPath $sumsPath | Where-Object { $_ -match '\s+bin/mace_native\.dll$' } | Select-Object -First 1
        if ($line) {
            $ExpectedSha256 = ($line -split '\s+')[0]
            $source = $sumsPath
        }
    }
    if ([string]::IsNullOrWhiteSpace($ExpectedSha256)) {
        throw "No hay hash de referencia para $dll. Usa bin\mace_native.dll del paquete de Dev 1 (con build-info.json) o define MACE_NATIVE_SHA256."
    }

    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $dll).Hash.ToUpperInvariant()
    $ExpectedSha256 = $ExpectedSha256.Trim().ToUpperInvariant()
    if ($actual -ne $ExpectedSha256) {
        throw "SHA-256 de $dll = $actual no coincide con $ExpectedSha256 ($source). No se empaqueta."
    }

    # stderr de un ejecutable nativo no debe convertirse en error terminante de PowerShell.
    $ErrorActionPreference = 'Continue'
    $verifier = & $JavaExe --enable-native-access=ALL-UNNAMED -cp $AppJar `
        com.mace.infrastructure.nativebridge.NativeAbiVerifier $dll $actual 2>&1 | ForEach-Object { "$_" }
    $verifierExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($verifierExit -ne 0) {
        throw "La DLL no supera la verificacion de ABI:`n$($verifier -join "`n")"
    }

    $adlx = if ($buildInfo) { $buildInfo.amdAdlxCompiled } else { 'desconocido' }
    $ryzen = if ($buildInfo) { $buildInfo.amdRyzenSdkCompiled } else { 'desconocido' }
    $report = @(
        'MACE - DLL nativa empaquetada',
        "Fecha (UTC)        : $([DateTime]::UtcNow.ToString('o'))",
        "Paquete de Dev 1   : $(Split-Path -Leaf $packageRoot)",
        "DLL                : $dll",
        "SHA-256            : $actual",
        "Hash de referencia : $source",
        "ADLX compilado     : $adlx",
        "Ryzen SDK compilado: $ryzen",
        'Verificacion FFM   :'
    ) + ($verifier | ForEach-Object { "  $_" })
    $report | Set-Content -LiteralPath $ReportPath -Encoding utf8

    if ($buildInfo -and (-not $buildInfo.amdAdlxCompiled -or -not $buildInfo.amdRyzenSdkCompiled)) {
        Write-Warning 'La DLL se compilo sin ADLX y/o sin Ryzen Master SDK: tendra menos sensores AMD que la version de referencia.'
    }
    Write-Output $actual
} catch {
    # Salida explicita con 1: el .bat la detecta aunque PowerShell devuelva otro codigo.
    [Console]::Error.WriteLine("[ERROR] " + $_.Exception.Message)
    exit 1
}
