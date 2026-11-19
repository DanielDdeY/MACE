param([Parameter(Mandatory=$true)][string]$HelperExe,[Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($OutputDirectory)
$runtime=Join-Path $root 'runtime'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
Copy-Item -LiteralPath $HelperExe -Destination (Join-Path $runtime 'Mace.Lhm.Helper.exe') -Force
[IO.File]::WriteAllText((Join-Path $runtime 'dependency.dll'),'trusted-fixture-dependency',[Text.Encoding]::ASCII)
$lines=@('MACE-LHM-RUNTIME 2')
foreach ($name in @('dependency.dll','Mace.Lhm.Helper.exe')) {
    $lines+=((Get-FileHash -LiteralPath (Join-Path $runtime $name) -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+$name)
}
$manifest=Join-Path $runtime 'helper-runtime.manifest'
[IO.File]::WriteAllText($manifest,($lines -join "`n")+"`n",[Text.Encoding]::ASCII)
$hash=(Get-FileHash -LiteralPath $manifest -Algorithm SHA256).Hash.ToLowerInvariant()
$config="#define MACE_LHM_DEVELOPMENT 0`n#define MACE_LHM_REQUIRE_AUTHENTICODE 0`n#define MACE_LHM_MANIFEST_SHA256 `"$hash`"`n"
[IO.File]::WriteAllText((Join-Path $root 'lhm_security_config.h'),$config,[Text.Encoding]::ASCII)
