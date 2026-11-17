param([string]$SigningScript = '')
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
    & dotnet restore 'Mace.Lhm.Tests\Mace.Lhm.Tests.csproj' --locked-mode
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la restauracion bloqueada.' }
    & dotnet build 'Mace.Lhm.Tests\Mace.Lhm.Tests.csproj' -c Release --no-restore
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la compilacion.' }
    & '.\Mace.Lhm.Tests\bin\Release\net10.0-windows\win-x64\Mace.Lhm.Tests.exe'
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas 1A.' }
    # Build in a fresh directory; a failed publish cannot masquerade as the previous package.
    $output = Join-Path $PSScriptRoot ('artifacts\' + [Guid]::NewGuid().ToString('N'))
    & dotnet publish 'Mace.Lhm.Helper\Mace.Lhm.Helper.csproj' -c Release --no-restore --self-contained true -o $output
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la publicacion.' }
    $signed = $false
    if ($SigningScript) {
        $hook = (Resolve-Path -LiteralPath $SigningScript).Path
        foreach ($file in Get-ChildItem -LiteralPath $output -File | Where-Object {$_.Name -like 'Mace.Lhm.*' -and $_.Extension -in @('.exe','.dll')}) {
            & $hook -FilePath $file.FullName
            if (-not $?) { throw ('Fallo la firma de ' + $file.Name) }
            if ((Get-AuthenticodeSignature -LiteralPath $file.FullName).Status -ne 'Valid') { throw ('Firma invalida: ' + $file.Name) }
        }
        $signed = $true
    } else {
        Write-Warning 'Firma omitida. Falta certificado de publicacion; este artefacto queda sin firma de MACE.'
    }
    $files = @(Get-ChildItem -LiteralPath $output -File | Sort-Object Name | ForEach-Object {
        [pscustomobject]@{file=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
    })
    [pscustomobject]@{protocol=2;sdk='10.0.401';runtime='10.0.12';lhm='0.9.6';signed=$signed;files=$files} |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output 'artifact-manifest.json') -Encoding UTF8
    & (Join-Path $PSScriptRoot 'Verify-Artifacts.ps1') -Directory $output
    $output | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'artifacts\latest.txt') -Encoding UTF8
    Write-Host ('Helper publicado en ' + $output)
} finally { Pop-Location }
