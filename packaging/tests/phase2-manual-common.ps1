# Definitions only; consumers are manual scripts. Never ship this directory.
. (Join-Path $PSScriptRoot 'image-process-observer.ps1')
$script:ManualRepo=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$script:ManualImage=Join-Path $script:ManualRepo 'packaging\dist\MACE'
function Initialize-Manual52 {
    if ([IntPtr]::Size -ne 8) { throw 'X64_POWERSHELL_REQUIRED' }
    if (-not ('Mace.PackagingTests.Manual52' -as [type])) { Add-Type -Path (Join-Path $PSScriptRoot 'phase2-manual-support.cs') }
}
function Test-ManualAdmin {
    return ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}
function New-ManualRun([string]$Case) {
    Initialize-Manual52
    $directory=Join-Path $script:ManualRepo ('native\build\fase2-paso5.2\'+$Case+'-'+[Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $log=Join-Path $directory 'result.txt'
    [Mace.PackagingTests.Manual52]::Log($log,"kind=BEGIN case=$Case administrator=$([int](Test-ManualAdmin)) status=NO_VERIFICADO execution=USER_MANUAL")
    Write-Host ('RESULTADO_RELATIVO=native/build/fase2-paso5.2/'+(Split-Path -Leaf $directory)+'/result.txt')
    return [pscustomobject]@{Directory=$directory;Log=$log}
}
function Assert-ManualImage {
    # Public per-file inventory is outside MACE; no executable is invoked here.
    $inventory=Join-Path $script:ManualRepo 'packaging\dist\MACE-SHA256SUMS.txt'
    foreach ($line in Get-Content -LiteralPath $inventory) {
        if ($line -cnotmatch '^([0-9a-f]{64})  (MACE/[^:]+)$') { throw 'IMAGE_INVENTORY_FORMAT' }
        $hash=$Matches[1]; $relative=$Matches[2]
        if ($relative.Contains('..') -or $relative.Contains('\')) { throw 'IMAGE_INVENTORY_PATH' }
        if ((Get-FileHash -LiteralPath (Join-Path (Split-Path -Parent $script:ManualImage) $relative) -Algorithm SHA256).Hash -ne $hash) { throw 'IMAGE_CHANGED' }
    }
}
function New-ManualInfo([ValidateSet('Image','Java','Preview')][string]$Kind) {
    $info=[Diagnostics.ProcessStartInfo]::new()
    $info.UseShellExecute=$false
    $info.CreateNoWindow=$true # GUI stays visible. No RunAs, lowering token or launcher edits.
    $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    $info.WorkingDirectory=$script:ManualImage
    foreach ($key in @($info.EnvironmentVariables.Keys)) { if ([string]$key -like 'MACE_*') { $info.EnvironmentVariables.Remove([string]$key) } }
    foreach ($key in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS')) { $info.EnvironmentVariables.Remove($key) }
    if ($Kind -eq 'Image') { $info.FileName=Join-Path $script:ManualImage 'MACE.exe' }
    elseif ($Kind -eq 'Preview') { $info.FileName=Join-Path $script:ManualImage 'MACE-Preview.exe' }
    else {
        # Real native production Java, copied pins from immutable image CFG. Not MACE.exe.
        $info.FileName=Join-Path $script:ManualImage 'runtime\bin\java.exe'
        $app=Join-Path $script:ManualImage 'app'
        $options=@(); $classpath=@()
        foreach ($line in Get-Content -LiteralPath (Join-Path $app 'MACE.cfg')) {
            if ($line.StartsWith('java-options=')) { $options+=$line.Substring(13).Replace('$APPDIR',$app) }
            if ($line.StartsWith('app.classpath=')) { $classpath+=$line.Substring(14).Replace('$APPDIR',$app) }
        }
        if (-not $classpath.Count) { throw 'IMAGE_CLASSPATH_MISSING' }
        $arguments=$options+@('-cp',($classpath -join ';'),'com.mace.presentation.MaceLauncher')
        # Arguments are fixed options or Windows paths (quotes not valid in these paths).
        $info.Arguments=($arguments | ForEach-Object { '"'+$_+'"' }) -join ' '
    }
    return $info
}
function Start-ManualActor([ValidateSet('Image','Java','Preview')][string]$Kind,[string]$Log) {
    $info=New-ManualInfo $Kind
    $capture=New-ImageOutputSummary
    $process=[Diagnostics.Process]::Start($info)
    $capture.Attach($process)
    $actor=[Mace.PackagingTests.ManualActor]::new()
    $actor.Launcher=$process; $actor.RootId=$process.Id; $actor.RootBirthTicks=$process.StartTime.ToUniversalTime().Ticks
    $actor.Executable=$info.FileName
    $actor.JvmLibrary=Join-Path $script:ManualImage 'runtime\bin\server\jvm.dll'
    $actor.NativeLibrary=Join-Path $script:ManualImage 'app\mace_native.dll'
    $actor.Helper=Join-Path $script:ManualImage 'app\lhm\Mace.Lhm.Helper.exe'
    $actor.Blackbox=Join-Path $env:LOCALAPPDATA 'MACE\blackbox'
    $actor.Simulated=$Kind -eq 'Preview'
    if ($actor.Simulated) { $actor.Blackbox=Join-Path $actor.Blackbox 'simulated' }
    [Mace.PackagingTests.Manual52]::Log($Log,"kind=START actor=$Kind root_pid=$($actor.RootId)")
    return $actor
}
function Update-ManualActor($Actor,[string]$Log) {
    if ([Mace.PackagingTests.Manual52]::Alive($Actor.Launcher)) {
        $state=Find-ImageJvmProcess -Launcher $Actor.Launcher -Executable $Actor.Executable -JvmLibrary $Actor.JvmLibrary -StartedUtc ([DateTime]::new($Actor.RootBirthTicks,[DateTimeKind]::Utc))
        if ($state.Process) {
            if ($Actor.Jvm -and $Actor.Jvm.Id -ne $state.Process.Id) { [Mace.PackagingTests.Manual52]::Log($Log,'kind=UNEXPECTED_JVM_CHANGE') }
            [Mace.PackagingTests.Manual52]::BindJvm($Actor,$state.Process)
        }
    }
    # Preserve captured JVM/helper handles after launcher death. No global-name reattachment.
    [Mace.PackagingTests.Manual52]::RefreshHelpers($Actor)
}
function Write-ManualSample($Actor,[string]$Log,[string]$Stage) {
    try { Update-ManualActor $Actor $Log }
    catch { [Mace.PackagingTests.Manual52]::Log($Log,'kind=NO_VERIFICADO reason=TREE_INSPECTION_UNAVAILABLE') }
    [Mace.PackagingTests.Manual52]::Sample($Log,$Actor,$Stage)
}
function Wait-ManualReady($Actor,[string]$Log,[int]$Seconds=45) {
    $deadline=[DateTime]::UtcNow.AddSeconds($Seconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        Write-ManualSample $Actor $Log 'WAIT_READY'
        if ($Actor.SawJvm -and [Mace.PackagingTests.Manual52]::Alive($Actor.Jvm)) {
            $native=@($Actor.Jvm.Modules | Where-Object { $_.ModuleName -ieq 'mace_native.dll' -and (Test-ImageSamePath $_.FileName $Actor.NativeLibrary) })
            if ($native.Count -eq 1 -and [Mace.PackagingTests.Manual52]::CurrentHelper($Actor)) { return $true }
        }
        if (-not [Mace.PackagingTests.Manual52]::Alive($Actor.Launcher)) { break }
        Start-Sleep -Seconds 1
    }
    [Mace.PackagingTests.Manual52]::Log($Log,'kind=NO_VERIFICADO reason=BASELINE_NOT_READY')
    return $false
}
function Confirm-Manual([string]$Token,[string]$Prompt) {
    Write-Host $Prompt
    return (Read-Host "Escribe $Token para continuar") -ceq $Token
}
function Invoke-ManualKill($Actor,[ValidateSet('Jvm','Launcher','Tree','Helper')][string]$Target,[string]$Log) {
    Update-ManualActor $Actor $Log
    $process=$null; $expected=$null
    switch ($Target) {
        'Jvm' { $process=$Actor.Jvm; $expected=$Actor.Executable }
        'Launcher' {
            if (-not $Actor.Jvm -or $Actor.Launcher.Id -eq $Actor.Jvm.Id) { throw 'NO_DISTINCT_LAUNCHER' }
            $process=$Actor.Launcher; $expected=$Actor.Executable
        }
        'Tree' { $process=$Actor.Launcher; $expected=$Actor.Executable }
        'Helper' { $process=[Mace.PackagingTests.Manual52]::CurrentHelper($Actor); $expected=$Actor.Helper }
    }
    if (-not [Mace.PackagingTests.Manual52]::Alive($process)) { throw 'TARGET_NOT_ALIVE_OR_AMBIGUOUS' }
    $birth=$process.StartTime.ToUniversalTime()
    Write-Host "OBJETIVO=$Target PID=$($process.Id)"
    if (-not (Confirm-Manual 'CONFIRMO' 'Se terminara unicamente el objetivo identificado del caso manual.')) { throw 'USER_CANCELLED' }
    # Fresh identity check against saved handle and fresh process immediately before /PID.
    if (-not [Mace.PackagingTests.Manual52]::Alive($process)) { throw 'TARGET_EXITED' }
    $current=Get-Process -Id $process.Id -ErrorAction Stop
    if ($current.StartTime.ToUniversalTime() -ne $birth -or -not (Test-ImageSamePath $current.MainModule.FileName $expected)) { throw 'TARGET_IDENTITY_CHANGED' }
    $arguments=@('/PID',[string]$process.Id,'/F')
    if ($Target -eq 'Tree') { $arguments+='/T' }
    [Mace.PackagingTests.Manual52]::Log($Log,"kind=ACTION action=TASKKILL target=$Target target_pid=$($process.Id) tree=$([int]($Target -eq 'Tree'))")
    & (Join-Path $env:SystemRoot 'System32\taskkill.exe') @arguments > $null 2>&1
    [Mace.PackagingTests.Manual52]::Log($Log,"kind=TASKKILL_RETURN code=$LASTEXITCODE")
}
function Watch-Manual($Actor,[string]$Log,[int]$Seconds,[string]$Stage) {
    $deadline=[DateTime]::UtcNow.AddSeconds($Seconds)
    while ([DateTime]::UtcNow -lt $deadline) { Write-ManualSample $Actor $Log $Stage; Start-Sleep -Seconds 1 }
}
