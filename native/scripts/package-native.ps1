param(
    [ValidateSet('Auto', 'VS2022', 'VS2026')][string]$VisualStudio = 'Auto',
    [string]$BuildDirectory,
    [string]$AdlxSdkDirectory = $env:MACE_ADLX_SDK_DIR
)

. (Join-Path $PSScriptRoot 'native-common.ps1')
$nativeRoot = Get-MaceNativeRoot
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $nativeRoot '..'))
$toolchain = Get-MaceToolchain -VisualStudio $VisualStudio
$build = Get-MaceBuildDirectory -BuildDirectory $BuildDirectory -Preset $toolchain.Preset

$sourceStatus = (& git -C $repositoryRoot status --porcelain -- native) -join "`n"
Assert-MaceCommandSucceeded 'Consulta de estado Git'
if (-not [string]::IsNullOrWhiteSpace($sourceStatus)) {
    throw 'Antes de distribuir, registra los cambios de native/ en la rama aislada para identificar la fuente exacta.'
}
$sourceRevision = (& git -C $repositoryRoot rev-parse HEAD).Trim()
Assert-MaceCommandSucceeded 'Consulta de revisión Git'

& (Join-Path $PSScriptRoot 'build-native.ps1') -Configuration Release `
    -VisualStudio $VisualStudio -BuildDirectory $build -AdlxSdkDirectory $AdlxSdkDirectory

$sourceDll = Join-Path $build 'Release\mace_native.dll'
$sourceLib = Join-Path $build 'Release\mace_native.lib'
if (-not (Test-Path -LiteralPath $sourceDll -PathType Leaf) -or
    -not (Test-Path -LiteralPath $sourceLib -PathType Leaf)) {
    throw 'La compilación no produjo la DLL y la biblioteca de importación esperadas.'
}

$cmakeText = Get-Content -LiteralPath (Join-Path $nativeRoot 'CMakeLists.txt') -Raw
if ($cmakeText -notmatch 'project\(mace_native VERSION (\d+\.\d+\.\d+)') {
    throw 'No se pudo leer la versión de native/CMakeLists.txt.'
}
$nativeVersion = $Matches[1]
$sourceDllHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $sourceDll).Hash
$sourceLibHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $sourceLib).Hash
$timestamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
$packageName = "mace-native-$nativeVersion-win-x64-$timestamp-$($sourceDllHash.Substring(0, 12).ToLowerInvariant())"
$package = Join-Path $nativeRoot "dist\$packageName"
if (Test-Path -LiteralPath $package) { throw "Ya existe el paquete: $package" }

foreach ($relative in @('bin', 'lib', 'include', 'docs', 'examples\ffm')) {
    New-Item -ItemType Directory -Path (Join-Path $package $relative) -Force | Out-Null
}
Copy-Item -LiteralPath $sourceDll -Destination (Join-Path $package 'bin\mace_native.dll')
Copy-Item -LiteralPath $sourceLib -Destination (Join-Path $package 'lib\mace_native.lib')
foreach ($header in Get-ChildItem -LiteralPath (Join-Path $nativeRoot 'include') -Filter '*.h' -File) {
    Copy-Item -LiteralPath $header.FullName -Destination (Join-Path $package 'include')
}
foreach ($document in Get-ChildItem -LiteralPath (Join-Path $nativeRoot 'docs') -Filter '*.md' -File) {
    Copy-Item -LiteralPath $document.FullName -Destination (Join-Path $package 'docs')
}
Copy-Item -LiteralPath (Join-Path $nativeRoot 'README.md') -Destination $package
Copy-Item -LiteralPath (Join-Path $nativeRoot 'examples\ffm\NativeContractProbe.java') `
    -Destination (Join-Path $package 'examples\ffm')

$distributedDll = Join-Path $package 'bin\mace_native.dll'
$distributedLib = Join-Path $package 'lib\mace_native.lib'
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $distributedDll).Hash -ne $sourceDllHash -or
    (Get-FileHash -Algorithm SHA256 -LiteralPath $distributedLib).Hash -ne $sourceLibHash) {
    throw 'La copia de distribución no coincide con la salida compilada.'
}

# Esta verificación vuelve a cargar exactamente la DLL de dist/, no la de build/.
& (Join-Path $PSScriptRoot 'verify-native.ps1') -DllPath $distributedDll `
    -BuildDirectory $build -Configuration Release -SkipCTest `
    -FfmSourcePath (Join-Path $package 'examples\ffm\NativeContractProbe.java')

$metadata = [ordered]@{
    schemaVersion = 1
    project = 'MACE native'
    nativeVersion = $nativeVersion
    abiVersion = '1.2'
    abiInfoRecordVersion = 2
    platform = 'Windows x64'
    configuration = 'Release'
    sourceCommit = $sourceRevision
    generator = $toolchain.Generator
    visualStudioVersion = $toolchain.VisualStudioVersion
    cmakeVersion = $toolchain.CMakeVersion
    builtUtc = [DateTime]::UtcNow.ToString('o')
    dllSha256 = $sourceDllHash
    importLibrarySha256 = $sourceLibHash
    amdAdlxCompiled = -not [string]::IsNullOrWhiteSpace($AdlxSdkDirectory)
    tests = @('CTest abi_layouts', 'CTest abi_exports', 'CTest telemetry_v2', 'CTest gpu_provider_dispatch', 'C exports on distributed DLL', 'JDK 22 FFM on distributed DLL')
}
$metadata | ConvertTo-Json -Depth 5 |
    Set-Content -LiteralPath (Join-Path $package 'build-info.json') -Encoding utf8

$hashLines = Get-ChildItem -LiteralPath $package -Recurse -File |
    Sort-Object FullName | ForEach-Object {
        $relative = $_.FullName.Substring($package.Length).TrimStart('\') -replace '\\', '/'
        $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant()
        "$hash  $relative"
    }
$hashLines | Set-Content -LiteralPath (Join-Path $package 'SHA256SUMS.txt') -Encoding utf8
Write-Host "Paquete nativo verificado: $package"
