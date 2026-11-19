param(
    [Parameter(Mandatory=$true)][ValidateSet('RunningToStoppedToRunning','StoppedBeforeMace')][string]$Scenario,
    [ValidateRange(5,30)][int]$StoppedSeconds=10,
    [ValidateRange(35,360)][int]$RecoverySeconds=75
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'phase2-manual-common.ps1')
if (-not (Test-ManualAdmin)) { Write-Host 'NO_VERIFICADO reason=ADMIN_REQUIRED'; exit 2 }
$run=New-ManualRun $Scenario
$actor=[Mace.PackagingTests.ManualActor]::new()
$actor.Blackbox=Join-Path $env:LOCALAPPDATA 'MACE\blackbox'
$actor.Helper=Join-Path $script:ManualImage 'app\lhm\Mace.Lhm.Helper.exe'
$armed=$false; $sampler=$null; $restored=$false; $resultCode=2
try {
    Assert-ManualImage
    Write-Host 'Esta prueba solicita SOLO parada normal de PawnIO y su arranque posterior. No fuerza ni instala nada.'
    Write-Host 'Cierra manualmente MACE/Preview previos y LHM de escritorio antes del escenario inverso.'
    if (-not (Confirm-Manual 'ACEPTO_PAWNIO' 'Tras confirmar, finally intentara dejar PawnIO en RUNNING incluso si hay un error o Ctrl+C.')) { throw 'USER_CANCELLED' }
    $armed=$true
    $state=[uint32]0
    $initial=[Mace.PackagingTests.Manual52]::PawnState([ref]$state)
    [Mace.PackagingTests.Manual52]::Log($run.Log,"kind=PAWNIO_INITIAL state=$state win32=$initial")
    if ($initial -ne 0 -or $state -ne 4) { throw 'INITIAL_RUNNING_NOT_CONFIRMED' }
    if (@(Get-Process MACE,MACE-Preview -ErrorAction SilentlyContinue).Count) { throw 'CLOSE_EXISTING_MACE_MANUALLY' }
    if ($Scenario -eq 'RunningToStoppedToRunning') {
        $actor=Start-ManualActor 'Image' $run.Log
        if (-not (Wait-ManualReady $actor $run.Log)) { throw 'BASELINE_NOT_READY' }
        Watch-Manual $actor $run.Log 5 'PAWNIO_BASELINE'
    }
    # Independent C# sampler keeps a 1 Hz record while synchronous SCM calls wait.
    $sampler=[Mace.PackagingTests.ManualSampler]::new($run.Log,$actor)
    [Mace.PackagingTests.Manual52]::Log($run.Log,'kind=ACTION action=PAWNIO_NORMAL_STOP')
    $stopError=[Mace.PackagingTests.Manual52]::StopPawnNormally([ref]$state)
    [Mace.PackagingTests.Manual52]::Log($run.Log,"kind=STOP_RETURN win32=$stopError state=$state")
    Write-Host "STOP_RETURN win32=$stopError state=$state"
    if ($stopError -ne 0) {
        [Mace.PackagingTests.Manual52]::Log($run.Log,"kind=NO_VERIFICADO reason=STOP_REJECTED win32=$stopError no_force=1")
        throw 'STOP_REJECTED'
    }
    $stopped=$false; $until=[DateTime]::UtcNow.AddSeconds(15)
    while ([DateTime]::UtcNow -lt $until) {
        $errorCode=[Mace.PackagingTests.Manual52]::PawnState([ref]$state)
        if ($errorCode -eq 0 -and $state -eq 1) { $stopped=$true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $stopped) { throw 'STOPPED_NOT_OBSERVED' }
    [Mace.PackagingTests.Manual52]::Log($run.Log,'kind=STOPPED_OBSERVED state=1')
    Write-Host 'STOPPED_OBSERVED; Ctrl+C aqui debe ejecutar finally y restaurar RUNNING.'
    if ($Scenario -eq 'StoppedBeforeMace') {
        $actor=Start-ManualActor 'Image' $run.Log
        $sampler.SetActor($actor)
        if (-not (Wait-ManualReady $actor $run.Log)) { throw 'HELPER_NOT_READY_WHILE_STOPPED' }
    }
    for ($n=0;$n -lt $StoppedSeconds;$n++) {
        Update-ManualActor $actor $run.Log
        Start-Sleep -Seconds 1
    }
    $beforeStart=[Mace.PackagingTests.Manual52]::PawnState([ref]$state)
    if ($beforeStart -eq 0 -and $state -eq 4) {
        [Mace.PackagingTests.Manual52]::Log($run.Log,'kind=NO_VERIFICADO reason=AUTO_RUNNING_BEFORE_REQUESTED_START auto_running_before_requested_start=1')
        throw 'AUTO_RUNNING_BEFORE_REQUESTED_START'
    }
    [Mace.PackagingTests.Manual52]::Log($run.Log,'kind=ACTION action=PAWNIO_START')
    $startError=[Mace.PackagingTests.Manual52]::StartPawn([ref]$state)
    [Mace.PackagingTests.Manual52]::Log($run.Log,"kind=START_RETURN win32=$startError state=$state")
    Write-Host "START_RETURN win32=$startError state=$state"
    if ($startError -ne 0) { throw 'START_REJECTED' }
    $until=[DateTime]::UtcNow.AddSeconds($RecoverySeconds)
    while ([DateTime]::UtcNow -lt $until) { Update-ManualActor $actor $run.Log; Start-Sleep -Seconds 1 }
    $resultCode=0
    [Mace.PackagingTests.Manual52]::Log($run.Log,'kind=RECORDED result_requires_user=1 sensor_recovery_not_automatically_approved=1')
} catch {
    [Mace.PackagingTests.Manual52]::Log($run.Log,('kind=NO_VERIFICADO reason=PAWNIO_SCENARIO_INCOMPLETE hresult='+$_.Exception.HResult))
} finally {
    # First cleanup action even on Ctrl+C: no pipeline cmdlets needed for restoration.
    try { if ($sampler) { $sampler.Dispose() } }
    finally { if ($armed) { $restored=[Mace.PackagingTests.Manual52]::RestorePawnRunning($run.Log,$actor,30) } }
}
if ($armed -and -not $restored) { $resultCode=2 }
Write-Host "CAPTURA_TERMINADA codigo=$resultCode. Verifica RESTORE_END running=1 en result.txt."
Write-Host 'Cierra manualmente MACE cuando termines de observar. No se termina ningun proceso.'
exit $resultCode
