param([Parameter(Mandatory=$true)][string]$RequestFile,[Parameter(Mandatory=$true)][string]$ResultFile)
# PowerShell 7 / SDK assemblies only. No restore, CLI, certificate chain or network.
$ErrorActionPreference='Stop'
$answer=@{ok=$false;reason='NOT_COMPLETED'}
try {
    $request=Get-Content -LiteralPath $RequestFile -Raw | ConvertFrom-Json
    $sdk=Join-Path (Split-Path -Parent (Get-Command dotnet).Source) 'sdk/10.0.401'
    [void][Reflection.Assembly]::LoadFrom((Join-Path $sdk 'NuGet.Common.dll'))
    [void][Reflection.Assembly]::LoadFrom((Join-Path $sdk 'NuGet.Packaging.dll'))
    $reader=[NuGet.Packaging.PackageArchiveReader]::new([string]$request.file)
    try {
        $raw=[Convert]::ToBase64String([Security.Cryptography.SHA512]::HashData([IO.File]::ReadAllBytes($request.file)))
        $signature=$reader.GetPrimarySignatureAsync([Threading.CancellationToken]::None).GetAwaiter().GetResult()
        if($signature){
            # true means verify the mathematical CMS signature WITHOUT building a trust chain.
            $signature.SignedCms.CheckSignature($true)
            [void]$reader.ValidateIntegrityAsync($signature.SignatureContent,[Threading.CancellationToken]::None).GetAwaiter().GetResult()
        }
        $content=$reader.GetContentHash([Threading.CancellationToken]::None,[Func[string]]{$raw})
        $answer=@{ok=$true;contentHash=$content;rawSha512=$raw;signed=($null -ne $signature);integrity=$true;certificateTrust='NO_VERIFICADO';lockComparison='NO_CONTENTHASH_IN_LOCK'}
        if($request.expected){
            if($content -cne $request.expected){throw 'LOCK_CONTENTHASH_MISMATCH'}
            $answer.lockComparison='MATCH'
        }
    } finally {$reader.Dispose()}
} catch {
    $reason=if($_.Exception.Message -eq 'LOCK_CONTENTHASH_MISMATCH'){'LOCK_CONTENTHASH_MISMATCH'}else{'NUPKG_INTEGRITY_OR_API_FAILED'}
    $answer=@{ok=$false;reason=$reason}
}
$answer | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $ResultFile -Encoding utf8
if(-not $answer.ok){Write-Host ('STOP reason='+$answer.reason);exit 2}
Write-Host ('NUPKG_PASS lock='+$answer.lockComparison+' signed='+[int]$answer.signed+' certificate_trust_asserted=0')
