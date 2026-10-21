Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-MaceNativeRoot {
    return [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
}

function Repair-MacePathEnvironment {
    # Algunos lanzadores inyectan PATH y Path simultáneamente. CMake no encuentra
    # cl.exe en ese entorno; la corrección afecta solo a este proceso de PowerShell.
    $variables = [Environment]::GetEnvironmentVariables('Process')
    $entries = @($variables.Keys | Where-Object { $_ -ieq 'PATH' } |
        ForEach-Object { [pscustomobject]@{ Name = [string]$_; Value = [string]$variables[$_] } })
    if ($entries.Count -gt 1) {
        $selected = ($entries | Sort-Object { $_.Value.Length } -Descending | Select-Object -First 1).Value
        Remove-Item -LiteralPath 'Env:PATH' -ErrorAction Stop
        $env:Path = $selected
        $remaining = @([Environment]::GetEnvironmentVariables('Process').Keys |
            Where-Object { $_ -ieq 'PATH' })
        if ($remaining.Count -ne 1) {
            throw 'No se pudo normalizar PATH/Path para las herramientas de Visual Studio.'
        }
    }
}

function Get-MaceToolchain {
    param([ValidateSet('Auto', 'VS2022', 'VS2026')][string]$VisualStudio = 'Auto')

    Repair-MacePathEnvironment
    $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
    if (-not (Test-Path -LiteralPath $vswhere -PathType Leaf)) {
        throw 'No se encontró vswhere.exe. Instala Visual Studio con Desktop development with C++.'
    }
    $json = (& $vswhere -all -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -format json) -join "`n"
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($json)) {
        throw 'vswhere no pudo encontrar una instalación de Visual Studio con MSVC x64.'
    }
    $instances = @(ConvertFrom-Json -InputObject $json) |
        Sort-Object { [version]$_.installationVersion } -Descending

    foreach ($instance in $instances) {
        $major = ([version]$instance.installationVersion).Major
        if ($major -notin @(17, 18)) { continue }
        if ($VisualStudio -eq 'VS2022' -and $major -ne 17) { continue }
        if ($VisualStudio -eq 'VS2026' -and $major -ne 18) { continue }

        $generator = if ($major -eq 17) { 'Visual Studio 17 2022' } else { 'Visual Studio 18 2026' }
        $preset = if ($major -eq 17) { 'vs2022-x64' } else { 'vs2026-x64' }
        $bundled = Join-Path $instance.installationPath 'Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe'
        $candidates = @($bundled)
        $onPath = Get-Command cmake.exe -ErrorAction SilentlyContinue
        if ($null -ne $onPath) { $candidates += $onPath.Source }

        foreach ($candidate in $candidates) {
            if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
            $versionLine = (& $candidate --version | Select-Object -First 1)
            if ($LASTEXITCODE -ne 0 -or $versionLine -notmatch 'cmake version (\d+\.\d+(?:\.\d+)?)') { continue }
            $cmakeVersion = [version]$Matches[1]
            if ($cmakeVersion -lt [version]'3.28') { continue }
            return [pscustomobject]@{
                CMake = $candidate
                CMakeVersion = $cmakeVersion.ToString()
                Generator = $generator
                Preset = $preset
                VisualStudioVersion = [string]$instance.installationVersion
            }
        }
    }
    throw 'No se encontró Visual Studio 2022/2026 con MSVC x64 y CMake 3.28 o superior.'
}

function Get-MaceBuildDirectory {
    param([string]$BuildDirectory, [string]$Preset)
    if ([string]::IsNullOrWhiteSpace($BuildDirectory)) {
        return (Join-Path (Get-MaceNativeRoot) "build\$Preset")
    }
    return [IO.Path]::GetFullPath($BuildDirectory)
}

function Assert-MaceCommandSucceeded {
    param([string]$Description)
    if ($LASTEXITCODE -ne 0) {
        throw "$Description falló con código $LASTEXITCODE."
    }
}
