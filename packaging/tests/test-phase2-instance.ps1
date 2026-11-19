param(
    [Parameter(Mandatory=$true)][ValidateSet('ElevatedOwner','NormalOwner','PreviewOwner','SecondElevated','SecondNormal','SecondPreview')][string]$Role,
    [ValidateRange(15,300)][int]$ObserveSeconds=60
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'phase2-manual-common.ps1')
$admin=Test-ManualAdmin
$normal=$Role -in @('NormalOwner','SecondNormal','PreviewOwner')
if (($normal -and $admin) -or (-not $normal -and -not $admin)) { Write-Host 'NO_VERIFICADO reason=WRONG_CONSOLE_TOKEN'; exit 2 }
$run=New-ManualRun $Role
try {
    Assert-ManualImage
    Write-Host 'Usa la MISMA cuenta y sesion en ambas consolas. No uses ejecutar como otro usuario.'
    if (-not (Confirm-Manual 'MISMA_CUENTA' 'Confirma la cuenta/sesion y el orden del caso del documento.')) { throw 'USER_CANCELLED' }
    $kind='Image'
    if ($Role -in @('NormalOwner','SecondNormal')) { $kind='Java' }
    if ($Role -in @('SecondPreview','PreviewOwner')) { $kind='Preview' }
    $actor=Start-ManualActor $kind $run.Log
    if ($Role -in @('ElevatedOwner','NormalOwner','PreviewOwner')) {
        if ($Role -eq 'PreviewOwner') {
            $readyUntil=[DateTime]::UtcNow.AddSeconds(30)
            while (-not $actor.SawJvm -and [DateTime]::UtcNow -lt $readyUntil) { Write-ManualSample $actor $run.Log 'PREVIEW_READY'; Start-Sleep -Seconds 1 }
            if (-not $actor.SawJvm) { throw 'PREVIEW_NOT_READY' }
        } elseif (-not (Wait-ManualReady $actor $run.Log)) { throw 'OWNER_NOT_READY' }
        Write-Host "OWNER_READY ROOT_PID=$($actor.RootId) JVM_PID=$($actor.Jvm.Id). Abre ahora la segunda consola indicada."
    } else {
        Write-Host 'Debe aparecer aviso de instancia existente. Dejalo visible unos segundos y aceptalo; no cierres al propietario.'
    }
    $deadline=[DateTime]::UtcNow.AddSeconds($ObserveSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        Write-ManualSample $actor $run.Log 'INSTANCE_OBSERVATION'
        if ($actor.Jvm -and [Mace.PackagingTests.Manual52]::Alive($actor.Jvm)) {
            $native=@($actor.Jvm.Modules | Where-Object { $_.ModuleName -ieq 'mace_native.dll' })
            [Mace.PackagingTests.Manual52]::Log($run.Log,"kind=INSTANCE native_module_loaded=$([int]($native.Count -gt 0))")
        }
        Start-Sleep -Seconds 1
    }
    [Mace.PackagingTests.Manual52]::Log($run.Log,'kind=RECORDED result_requires_user=1 status=NO_VERIFICADO')
    Write-Host 'Captura terminada. Cierra manualmente el propietario al concluir ambas capturas.'
    exit 0
} catch {
    [Mace.PackagingTests.Manual52]::Log($run.Log,('kind=NO_VERIFICADO reason=INSTANCE_NOT_COMPLETED hresult='+$_.Exception.HResult))
    Write-Host 'NO_VERIFICADO. No se elevo ni termino otro proceso.'; exit 2
}
