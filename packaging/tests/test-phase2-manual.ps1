param(
    [Parameter(Mandatory=$true)][ValidateSet('CloseX','EndTaskWindow','KillJvm','KillLauncher','KillTree','KillHelper','CloseWarming','CoexistLhm','SuspendResume')][string]$Case,
    [ValidateRange(15,600)][int]$ObserveSeconds=45
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'phase2-manual-common.ps1')
if (-not (Test-ManualAdmin)) { Write-Host 'NO_VERIFICADO reason=ADMIN_REQUIRED'; exit 2 }
$run=New-ManualRun $Case
try {
    Assert-ManualImage
    Write-Host 'Cierra manualmente MACE/Preview previos, incluidos los abiertos desde fuentes.'
    if (-not (Confirm-Manual 'PREPARADO' "Caso $Case. El script abrira una instancia nueva; no cierra procesos previos.")) { throw 'USER_CANCELLED' }
    if (@(Get-Process MACE,MACE-Preview -ErrorAction SilentlyContinue).Count) { throw 'EXISTING_IMAGE_INSTANCE' }
    if ($Case -eq 'CloseWarming') { Write-Host 'Al aparecer MACE, intenta cerrar con X mientras muestra Preparando lecturas. No se retrasa ni suspende el helper.' }
    if ($Case -eq 'CoexistLhm') { Write-Host 'Abre/cierra LHM de escritorio tu mismo segun el documento. El script no lo busca ni lo toca.' }
    $actor=Start-ManualActor 'Image' $run.Log
    if ($Case -ne 'CloseWarming' -and -not (Wait-ManualReady $actor $run.Log)) { throw 'BASELINE_NOT_READY' }
    if ($actor.Jvm) { Write-Host "LAUNCHER_PID=$($actor.RootId) JVM_PID=$($actor.Jvm.Id)" }
    Watch-Manual $actor $run.Log 3 'BASELINE'
    switch ($Case) {
        'KillJvm' { Invoke-ManualKill $actor 'Jvm' $run.Log }
        'KillLauncher' { Invoke-ManualKill $actor 'Launcher' $run.Log }
        'KillTree' { Invoke-ManualKill $actor 'Tree' $run.Log }
        'KillHelper' { Invoke-ManualKill $actor 'Helper' $run.Log }
        'CloseX' { Write-Host 'ACCION MANUAL AHORA: cierra con X la ventana principal de MACE.' }
        'CloseWarming' { Write-Host 'ACCION MANUAL: X durante WARMING_UP si aun es observable; si no lo fue, anota NO VERIFICADO.' }
        'EndTaskWindow' { Write-Host 'ACCION MANUAL: Administrador de tareas > Procesos > ventana MACE > Finalizar tarea. Anota fila/grupo y PID mostrado; no sustituyas por Detalles.' }
        'SuspendResume' { Write-Host 'ACCION MANUAL: suspende desde Inicio, espera al menos 15 s y reanuda. Observa la UI inmediatamente. El script no solicita suspension.' }
        'CoexistLhm' { Write-Host 'ACCION MANUAL: sigue el orden de apertura/cierre de ambas apps descrito en el documento.' }
    }
    [Mace.PackagingTests.Manual52]::Log($run.Log,"kind=OBSERVATION_BEGIN case=$Case seconds=$ObserveSeconds")
    Watch-Manual $actor $run.Log $ObserveSeconds 'AFTER_ACTION'
    [Mace.PackagingTests.Manual52]::Log($run.Log,'kind=RECORDED result_requires_user=1 status=NO_VERIFICADO no_automatic_approval=1')
    Write-Host 'OBSERVACIONES REGISTRADAS; codigo 0 significa captura terminada, no prueba aprobada.'
    Write-Host 'Cierra manualmente las ventanas que sigan abiertas. No hay limpieza automatica ni kill adicional.'
    exit 0
} catch {
    [Mace.PackagingTests.Manual52]::Log($run.Log,('kind=NO_VERIFICADO reason=CASE_NOT_COMPLETED hresult='+$_.Exception.HResult))
    Write-Host 'NO_VERIFICADO. Revisa result.txt y cierra manualmente cualquier ventana restante.'
    exit 2
}
