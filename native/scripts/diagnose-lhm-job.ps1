param(
    [string]$BuildDirectory='',
    [string]$FixtureBuildDirectory='',
    [ValidateSet('Auto','VS2022','VS2026')][string]$VisualStudio='Auto',
    [switch]$SkipBuild
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'native-common.ps1')
$repo=Split-Path -Parent (Get-MaceNativeRoot)
if (-not $BuildDirectory) { $BuildDirectory=Join-Path $repo 'native\build\diagnostico-4b\dev' }
$BuildDirectory=[IO.Path]::GetFullPath($BuildDirectory)
if (-not $FixtureBuildDirectory) { $FixtureBuildDirectory=Join-Path $repo 'native\build\fase2-paso4b\dev' }
$FixtureBuildDirectory=[IO.Path]::GetFullPath($FixtureBuildDirectory)
$tc=Get-MaceToolchain -VisualStudio $VisualStudio
$elevated=([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
Write-Output "LHM_DIAG elevated=$elevated"
$hashName='MACE_LHM_HELPER_SHA256'
$inherited=[Environment]::GetEnvironmentVariable($hashName,'Process')
foreach ($scope in @('Process','User','Machine')) {
    $value=[Environment]::GetEnvironmentVariable($hashName,$scope)
    Write-Output ("LHM_DIAG helper_hash_scope={0} present={1} valid_format={2}" -f $scope,[bool]$value,([bool]$value -and $value -match '^[0-9a-fA-F]{64}$'))
}
foreach ($name in @('CL','_CL_','CFLAGS','CXXFLAGS')) {
    $value=[Environment]::GetEnvironmentVariable($name,'Process')
    Write-Output ("LHM_DIAG compiler_env={0} defines_test_hooks={1}" -f $name,([bool]$value -and $value.Contains('MACE_LHM_TEST_HOOKS')))
}
$scenario=[Environment]::GetEnvironmentVariable('MACE_SIM_SCENARIO','Process')
Write-Output ('LHM_DIAG scenario_is_permanent_containment='+($scenario -ceq 'permanent-containment'))
if (-not $SkipBuild) {
    # Dedicated diagnostic build. Never reconfigure/overwrite the user's 4A/4B folders.
    New-Item -ItemType Directory -Path $BuildDirectory -Force | Out-Null
    $configure=@(& $tc.CMake -S (Get-MaceNativeRoot) -B $BuildDirectory -G $tc.Generator -A x64 -DBUILD_TESTING=ON -DMACE_LHM_DEVELOPMENT=ON 2>&1)
    if ($LASTEXITCODE) { Write-Output 'LHM_DIAG configure_failed=1'; throw 'Fallo la configuracion; no se imprimen rutas.' }
    $compile=@(& $tc.CMake --build $BuildDirectory --config Release --target test_lhm_job lhm_fake_helper --parallel 4 2>&1)
    if ($LASTEXITCODE) {
        $compile | ForEach-Object { if ("$_" -match 'error ([A-Z]+\d+):') { Write-Output ('LHM_DIAG compiler_error='+$Matches[1]) } }
        throw 'Fallo la compilacion del diagnostico.'
    }
    Write-Output 'LHM_DIAG dedicated_build_completed=1'
}
$cache=Join-Path $BuildDirectory 'CMakeCache.txt'
$sourceLine=(Select-String -LiteralPath $cache -Pattern '^CMAKE_HOME_DIRECTORY:INTERNAL=').Line
$source=$sourceLine.Substring($sourceLine.IndexOf('=')+1)
Write-Output ('LHM_DIAG source_is_current_checkout='+($source.TrimEnd('/','\') -ieq (Get-MaceNativeRoot).Replace('\','/').TrimEnd('/')))
$fake=Join-Path $FixtureBuildDirectory 'Release\lhm_fake_helper.exe'
if (-not (Test-Path -LiteralPath $fake)) { throw 'No existe el helper simulado de la compilacion original; indica -FixtureBuildDirectory.' }
$fixturePin=(Get-FileHash -LiteralPath $fake -Algorithm SHA256).Hash
Write-Output 'LHM_DIAG fixture_source=original-build-unmodified'
Write-Output ("LHM_DIAG inherited_hash_present={0} inherited_matches_fixture={1}" -f [bool]$inherited,([bool]$inherited -and $inherited -ieq $fixturePin))
$dll=Join-Path $BuildDirectory 'Release\mace_native_lhm_test.dll'
Write-Output ('LHM_DIAG fixture_dll_version='+[Diagnostics.FileVersionInfo]::GetVersionInfo($dll).FileVersion)
$results=[Collections.Generic.List[object]]::new()
# Use the EXACT original fake executable: a fresh link can change its SHA-256.
# Only the diagnostic job executable/DLL are rebuilt, in the isolated directory.
$run=Join-Path $BuildDirectory 'run-original-fixture'
New-Item -ItemType Directory -Path $run -Force | Out-Null
$job=(Join-Path $BuildDirectory 'Release\test_lhm_job.exe').Replace('\','/')
$fakeForCmake=$fake.Replace('\','/')
if ($job.Contains(']==]') -or $fakeForCmake.Contains(']==]')) { throw 'Ruta no admitida por el archivo de diagnostico.' }
$cases=@('contained','forced-close','descendants','abrupt-parent','restrictive-job','control-close','eof-close','foreign-homonym')
$testFile=@($cases | ForEach-Object { 'add_test(lhm_job_'+$_+' [==['+$job+']==] [==['+$fakeForCmake+']==] '+$_+')' })
[IO.File]::WriteAllLines((Join-Path $run 'CTestTestfile.cmake'),$testFile,[Text.UTF8Encoding]::new($false))
try {
    foreach ($label in @('inherited','pinned-fixture-control','intentional-mismatch')) {
        if ($label -eq 'inherited') { $selected=$inherited }
        elseif ($label -eq 'pinned-fixture-control') { $selected=$fixturePin }
        else { $selected='0'*64 }
        [Environment]::SetEnvironmentVariable($hashName,$selected,'Process')
        Write-Output "LHM_DIAG case=$label BEGIN"
        # Windows PowerShell 5 turns native stderr into ErrorRecords. The negative
        # control must be captured, not terminate the diagnostic prematurely.
        $savedPreference=$ErrorActionPreference
        try {
            $ErrorActionPreference='Continue'
            $output=@(& $tc.CTest --test-dir $run -C Release -R '^lhm_job_' --output-on-failure -j 4 2>&1)
            $code=$LASTEXITCODE
        } finally { $ErrorActionPreference=$savedPreference }
        foreach ($line in $output) {
            $text="$line"
            # Deliberately omit CTest's absolute working directories/command lines.
            if ($text -match '^LHM_(OPEN_FAILED|PARENT_OPEN_FAILED|JOB_CONTEXT)' -or
                $text -match '^\s*\d+/\d+ Test\s+#\d+: lhm_job_' -or
                $text -match '^\s*\d+% tests passed' -or
                $text -match '^Total Test time') { Write-Output $text }
        }
        Write-Output "LHM_DIAG case=$label ctest_exit=$code END"
        $results.Add([pscustomobject]@{case=$label;ctest_exit=$code})
    }
} finally {
    [Environment]::SetEnvironmentVariable($hashName,$inherited,'Process')
    Write-Output 'LHM_DIAG process_environment_restored=1'
}
Write-Output 'LHM_DIAG COMPLETE: intentional-mismatch debe fallar; estos son controles de diagnostico, no una regresion aprobada.'
$results | Format-Table -AutoSize
