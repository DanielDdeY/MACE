param([Parameter(Mandatory = $true)][string]$SdkDirectory)

$ErrorActionPreference = 'Stop'
$expected = '3.1.1.5478'
foreach ($name in @('Platform.dll', 'Device.dll')) {
    $path = Join-Path $SdkDirectory "bin\$name"
    $file = Get-Item -LiteralPath $path -ErrorAction Stop
    if ($file.VersionInfo.FileVersion -ne $expected) {
        throw "$name es versión $($file.VersionInfo.FileVersion); este adaptador requiere $expected."
    }
}
Write-Output $expected
