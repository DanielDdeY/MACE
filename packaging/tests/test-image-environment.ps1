param([string]$ImageDirectory,[ValidateRange(5,120)][int]$TimeoutSeconds=45)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'image-process-observer.ps1')
$repo=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $ImageDirectory) { $ImageDirectory=Join-Path $repo 'packaging\dist\MACE' }
$run=Join-Path $repo ('native\build\fase2-paso5.1\environment-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $run -Force | Out-Null
$log=Join-Path $run 'result.txt'
function Record([string]$Message) {
    # Callers supply fixed fields/booleans only: never raw exception text or paths.
    $Message | Add-Content -LiteralPath $log -Encoding UTF8
    Write-Host $Message
}
$admin=([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
Record "TEST_CONTEXT Administrator=$admin"
if (-not $admin) {
    Record 'NO_VERIFICADO reason=ADMIN_REQUIRED no_process_started=1'
    exit 2
}
$reason='INPUT_VALIDATION'
$capture=$null
$process=$null
try {
    $image=(Resolve-Path -LiteralPath $ImageDirectory).Path
    $exe=Join-Path $image 'MACE.exe'
    $dll=Join-Path $image 'app\mace_native.dll'
    $helper=Join-Path $image 'app\lhm\Mace.Lhm.Helper.exe'
    if (@(Get-Process MACE,MACE-Preview -ErrorAction SilentlyContinue).Count) {
        $reason='CLOSE_EXISTING_MACE_MANUALLY'; throw 'Controlled rejection'
    }
    # Verify without copying development scripts/code into the image.
    & (Join-Path (Split-Path -Parent $PSScriptRoot) 'verify-runtime.ps1') -RuntimeDirectory (Join-Path $image 'app') -AppImage | Out-Null
    $config=@(Get-Content -LiteralPath (Join-Path $image 'app\MACE.cfg'))
    if ($config -cnotcontains 'java-options=-Dmace.infra.mode=native' -or
        $config -cnotcontains 'java-options=-Dmace.native.lib=$APPDIR\mace_native.dll') {
        $reason='LAUNCHER_PINS'; throw 'Controlled rejection'
    }
    $hashLine=@($config | Where-Object { $_ -cmatch '^java-options=-Dmace\.native\.sha256=[A-Fa-f0-9]{64}$' })
    if ($hashLine.Count -ne 1) { $reason='LAUNCHER_PINS'; throw 'Controlled rejection' }
    $expectedHash=$hashLine[0].Substring($hashLine[0].LastIndexOf('=')+1)
    if ((Get-FileHash -LiteralPath $dll -Algorithm SHA256).Hash -ne $expectedHash) {
        $reason='LAUNCHER_PINS'; throw 'Controlled rejection'
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $jar=@(Get-ChildItem -LiteralPath (Join-Path $image 'app') -Filter 'mace-app-*.jar' -File)
    if ($jar.Count -ne 1) { $reason='JAVA_PROFILE'; throw 'Controlled rejection' }
    $archive=[IO.Compression.ZipFile]::OpenRead($jar[0].FullName)
    try {
        $entry=$archive.GetEntry('com/mace/infrastructure/nativebridge/native-profile.properties')
        if (-not $entry) { $reason='JAVA_PROFILE'; throw 'Controlled rejection' }
        $reader=[IO.StreamReader]::new($entry.Open())
        try { $profile=$reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($profile.Trim() -cne 'profile=production') { $reason='JAVA_PROFILE'; throw 'Controlled rejection' }
    } finally { $archive.Dispose() }
    $nativeFixture=Join-Path $repo 'native\build\fase2-paso4b\dev\Release\mace_native.dll'
    $helperFixture=Join-Path $repo 'native\build\fase2-paso4b\dev\Release\lhm_fake_helper.exe'
    $foreignDll=Join-Path $run 'foreign-native.dll'
    $foreignHelper=Join-Path $run 'foreign-helper.exe'
    Copy-Item -LiteralPath $nativeFixture -Destination $foreignDll
    Copy-Item -LiteralPath $helperFixture -Destination $foreignHelper
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName=$exe
    $start.WorkingDirectory=$image
    $start.UseShellExecute=$false
    $start.CreateNoWindow=$true # Suppress console; the interactive MACE GUI stays visible.
    $start.RedirectStandardOutput=$true
    $start.RedirectStandardError=$true
    foreach ($key in @($start.EnvironmentVariables.Keys)) {
        if ([string]$key -like 'MACE_*') { $start.EnvironmentVariables.Remove([string]$key) }
    }
    foreach ($key in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS')) { $start.EnvironmentVariables.Remove($key) }
    $start.EnvironmentVariables['MACE_NATIVE_LIB']=$foreignDll
    $start.EnvironmentVariables['MACE_NATIVE_SHA256']=(Get-FileHash -LiteralPath $foreignDll -Algorithm SHA256).Hash
    $start.EnvironmentVariables['MACE_LHM_HELPER']=$foreignHelper
    $start.EnvironmentVariables['MACE_LHM_HELPER_SHA256']=(Get-FileHash -LiteralPath $foreignHelper -Algorithm SHA256).Hash
    Record 'PREFLIGHT production_java=1 launcher_pins=1 runtime_verified=1 foreign_variables=4'
    $reason='START_FAILED'
    $capture=New-ImageOutputSummary
    $process=[Diagnostics.Process]::Start($start) # Inherits administrator token supplied by USER; no RunAs.
    $startedUtc=$process.StartTime.ToUniversalTime()
    # C# callbacks retain only fixed flags; no raw text or PowerShell runspace callbacks.
    $capture.Attach($process)
    Record 'START image_process=1 user_or_machine_environment_changed=0'
    $reason='OBSERVATION_TIMEOUT'
    $deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $stable=0
    $lastJvm=0
    $lastShape=''
    while ([DateTime]::UtcNow -lt $deadline) {
        $process.Refresh()
        if ($process.HasExited) {
            Record ("LAUNCHER_EXIT code="+$process.ExitCode)
            $reason='MACE_EXITED_BEFORE_OBSERVATION'; throw 'Controlled rejection'
        }
        $reason='PROCESS_TREE_INSPECTION'
        $state=Find-ImageJvmProcess -Launcher $process -Executable $exe -JvmLibrary (Join-Path $image 'runtime\bin\server\jvm.dll') -StartedUtc $startedUtc
        $reason='OBSERVATION_TIMEOUT'
        $shape="TREE image_processes=$($state.ImageProcesses) jvm_processes=$($state.JvmProcesses)"
        if ($shape -ne $lastShape) { Record $shape; $lastShape=$shape }
        $jvm=$state.Process
        if (-not $jvm) { $stable=0; Start-Sleep -Milliseconds 500; continue }
        if ($jvm.Id -ne $lastJvm) { $stable=0; $lastJvm=$jvm.Id }
        $modules=@($jvm.Modules)
        if (@($modules | Where-Object { Test-ImageSamePath $_.FileName $foreignDll }).Count) {
            $reason='FOREIGN_NATIVE_LOADED'; throw 'Controlled rejection'
        }
        $native=@($modules | Where-Object { $_.ModuleName -ieq 'mace_native.dll' })
        if ($native.Count -and ($native.Count -ne 1 -or -not (Test-ImageSamePath $native[0].FileName $dll))) {
            $reason='NATIVE_LOCATION_MISMATCH'; throw 'Controlled rejection'
        }
        $children=@($state.Rows | Where-Object { $_.ParentProcessId -eq $jvm.Id })
        if (@($children | Where-Object { Test-ImageSamePath $_.ExecutablePath $foreignHelper }).Count) {
            $reason='FOREIGN_HELPER_STARTED'; throw 'Controlled rejection'
        }
        $owned=@($children | Where-Object { $_.Name -ieq 'Mace.Lhm.Helper.exe' })
        if (@($owned | Where-Object { -not $_.CreationDate -or $_.CreationDate.ToUniversalTime() -lt $jvm.StartTime.ToUniversalTime().AddMilliseconds(-1) }).Count) {
            $reason='HELPER_IDENTITY_UNVERIFIED'; throw 'Controlled rejection'
        }
        if ($owned.Count -and ($owned.Count -ne 1 -or -not (Test-ImageSamePath $owned[0].ExecutablePath $helper))) {
            $reason='HELPER_LOCATION_MISMATCH'; throw 'Controlled rejection'
        }
        if ($native.Count -eq 1 -and $owned.Count -eq 1) {
            $stable++
            if ($stable -ge 3) {
                Record ("OBSERVED launcher_is_jvm="+[int]($jvm.Id -eq $process.Id)+" helper_parent_is_jvm=1")
                Record 'PASS actual_MACE_exe=1 packaged_native_loaded=1 packaged_helper_started=1 foreign_native_loaded=0 foreign_helper_started=0'
                Record 'SCOPE component_selection_only=1 cpu_sensor_validity_not_asserted=1'
                Record 'HANDOFF close_the_visible_MACE_manually=1 no_process_terminated_by_script=1'
                exit 0
            }
        } else { $stable=0 }
        Start-Sleep -Milliseconds 500
    }
    throw 'Controlled rejection'
} catch {
    $fixedReasons=@('IMAGE_ROOT_IDENTITY_UNVERIFIED','IMAGE_PROCESS_IDENTITY_UNVERIFIED','IMAGE_JVM_LOCATION_MISMATCH','IMAGE_JVM_AMBIGUOUS')
    if ($_.Exception.Message -cin $fixedReasons) { $reason=$_.Exception.Message }
    if ($capture) {
        Record ("OUTPUT native_mode=$($capture.NativeMode) mock_mode=$($capture.MockMode) fx_start_failure=$($capture.FxStartFailure) missing_class=$($capture.MissingClass) unsatisfied_link=$($capture.UnsatisfiedLink) out_of_memory=$($capture.OutOfMemory) lines=$($capture.OutputLines)")
    }
    Record ("FAIL reason=$reason hresult="+$_.Exception.HResult)
    Record 'HANDOFF close_any_visible_MACE_manually=1 no_process_terminated_by_script=1'
    exit 1
}
