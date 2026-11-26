param([Parameter(Mandatory=$true)][string]$OutputFile)
# Local SDK/package/OS trust store only. No key import, store write or network.
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$root=Join-Path $repo 'native/build/fase3-lote-cerrado-20261126-01'
$package=Join-Path $root 'deps/nupkg-feed/microsoft.netcore.app.runtime.win-x64.10.0.12.nupkg'
$sdkRoot=Split-Path -Parent (Get-Command dotnet).Source
$sdk=Join-Path $sdkRoot 'sdk/10.0.401'
[void][Reflection.Assembly]::LoadFrom((Join-Path $sdk 'NuGet.Common.dll'))
[void][Reflection.Assembly]::LoadFrom((Join-Path $sdk 'NuGet.Packaging.dll'))
$expected=(Get-Content (Join-Path $root 'evidence/downloads-private.json') -Raw | ConvertFrom-Json).artifacts | Where-Object {$_.file -like '*microsoft.netcore.app.runtime.win-x64.10.0.12.nupkg'}
if((Get-FileHash $package -Algorithm SHA256).Hash -ine $expected.sha256_private){throw 'LOCAL_PACKAGE_CHANGED'}
$reader=[NuGet.Packaging.PackageArchiveReader]::new($package)
try {
    $signature=$reader.GetPrimarySignatureAsync([Threading.CancellationToken]::None).GetAwaiter().GetResult()
    if(-not $signature){throw 'PACKAGE_UNSIGNED'}
    $signature.SignedCms.CheckSignature($true)
    [void]$reader.ValidateIntegrityAsync($signature.SignatureContent,[Threading.CancellationToken]::None).GetAwaiter().GetResult()
    $chain=[Security.Cryptography.X509Certificates.X509Chain]::new()
    try {
        $chain.ChainPolicy.DisableCertificateDownloads=$true
        $chain.ChainPolicy.RevocationMode=[Security.Cryptography.X509Certificates.X509RevocationMode]::Offline
        $chain.ChainPolicy.ExtraStore.AddRange($signature.SignedCms.Certificates)
        $trusted=$chain.Build($signature.SignerInfo.Certificate)
        $statuses=@($chain.ChainStatus | ForEach-Object {$_.Status.ToString()})
    } finally {$chain.Dispose()}
    $matched=0;$different=0;$notPresent=0
    $shared=Join-Path $sdkRoot 'shared/Microsoft.NETCore.App/10.0.12'
    $zip=[IO.Compression.ZipFile]::OpenRead($package)
    try {
        foreach($entry in $zip.Entries){
            if($entry.FullName -notmatch '^runtimes/win-x64/(lib/net10\.0|native)/' -or -not $entry.Name){continue}
            $installed=Join-Path $shared $entry.Name
            if(-not (Test-Path -LiteralPath $installed -PathType Leaf)){$notPresent++;continue}
            $stream=$entry.Open()
            try {$packageHash=[Security.Cryptography.SHA256]::HashData($stream)}finally{$stream.Dispose()}
            $localHash=[Security.Cryptography.SHA256]::HashData([IO.File]::ReadAllBytes($installed))
            if([Convert]::ToHexString($packageHash) -ceq [Convert]::ToHexString($localHash)){$matched++}else{$different++}
        }
    }finally{$zip.Dispose()}
    $result=@{version='10.0.12';signature_type=$signature.Type.ToString();signature_math=$true;archive_integrity=$true;
        certificate_chain_offline=$trusted;chain_status=$statuses;certificate_downloads=$false;
        shared_runtime_matches=$matched;shared_runtime_differences=$different;shared_runtime_missing=$notPresent;
        independent_published_package_checksum='NO_VERIFICADO';key_imports=0}
    $result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $OutputFile -Encoding utf8
    Write-Host ('RUNTIMEPACK_LOCAL integrity=1 signature_math=1 chain_offline='+[int]$trusted+' shared_matches='+$matched+' shared_differences='+$different+' shared_missing='+$notPresent)
} finally {$reader.Dispose()}
