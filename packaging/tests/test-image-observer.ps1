param([string]$JavaHome='C:\Program Files\Java\jdk-22')
$ErrorActionPreference='Stop'
$admin=([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
Write-Host "TEST_CONTEXT Administrator=$admin"
. (Join-Path $PSScriptRoot 'image-process-observer.ps1')
$repo=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$run=Join-Path $repo ('native\build\fase2-paso5.1\observer-test-'+[Guid]::NewGuid().ToString('N'))
$inputDirectory=Join-Path $run 'input'
$classes=Join-Path $run 'classes'
New-Item -ItemType Directory -Path $inputDirectory,$classes -Force | Out-Null
$code=@'
public class ObserverFixture {
    public static void main(String[] args) throws Exception {
        if (args.length != 0 && args[0].equals("exit17")) System.exit(17);
        if (args.length != 0 && args[0].equals("leaf")) { Thread.sleep(4500); return; }
        Process leaf = new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-cp", System.getProperty("java.class.path"), ObserverFixture.class.getName(), "leaf").start();
        System.out.println("[MACE] Modo NATIVO - disposable fixture");
        System.err.println("java.lang.ClassNotFoundException: discarded test input");
        Thread.sleep(6500);
        leaf.waitFor();
    }
}
'@
$source=Join-Path $run 'ObserverFixture.java'
[IO.File]::WriteAllText($source,$code,[Text.Encoding]::ASCII)
& (Join-Path $JavaHome 'bin\javac.exe') -d $classes $source
if ($LASTEXITCODE) { throw 'Fixture compilation failed' }
& (Join-Path $JavaHome 'bin\jar.exe') --create --file (Join-Path $inputDirectory 'observer.jar') -C $classes .
if ($LASTEXITCODE) { throw 'Fixture JAR failed' }
& (Join-Path $JavaHome 'bin\jpackage.exe') --type app-image --name ObserverFixture --input $inputDirectory --main-jar observer.jar --main-class ObserverFixture --runtime-image (Join-Path $repo 'packaging\dist\MACE\runtime') --dest $run *> (Join-Path $run 'jpackage.log')
if ($LASTEXITCODE) { throw 'Fixture packaging failed' }
$image=Join-Path $run 'ObserverFixture'
$exe=Join-Path $image 'ObserverFixture.exe'
$jvmLibrary=Join-Path $image 'runtime\bin\server\jvm.dll'
function Start-Fixture([bool]$Direct,[string]$Arguments='') {
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName=$exe
    $start.Arguments=$Arguments
    $start.UseShellExecute=$false
    $start.CreateNoWindow=$true
    $start.WindowStyle=[Diagnostics.ProcessWindowStyle]::Hidden
    $start.RedirectStandardOutput=$true
    $start.RedirectStandardError=$true
    # Deliberate test input only: production harness never edits PATH to avoid restart.
    if ($Direct) { $start.EnvironmentVariables['PATH']=(Join-Path $image 'app')+';'+$start.EnvironmentVariables['PATH'] }
    foreach ($key in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS')) { $start.EnvironmentVariables.Remove($key) }
    $capture=New-ImageOutputSummary
    $process=[Diagnostics.Process]::Start($start)
    $stamp=$process.StartTime.ToUniversalTime()
    $capture.Attach($process)
    return [pscustomobject]@{Process=$process;Stamp=$stamp;Capture=$capture;Direct=$Direct;Verified=$false;JvmId=0}
}
$direct=Start-Fixture $true
$nested=Start-Fixture $false
$fixtures=@($direct,$nested)
$deadline=[DateTime]::UtcNow.AddSeconds(6)
while ([DateTime]::UtcNow -lt $deadline -and @($fixtures | Where-Object { -not $_.Verified }).Count) {
    foreach ($fixture in @($fixtures | Where-Object { -not $_.Verified })) {
        $state=Find-ImageJvmProcess -Launcher $fixture.Process -Executable $exe -JvmLibrary $jvmLibrary -StartedUtc $fixture.Stamp
        if ($state.Process) {
            $isDirect=$state.Process.Id -eq $fixture.Process.Id
            if ($isDirect -ne $fixture.Direct -or $state.JvmProcesses -ne 1) { throw 'Wrong process selected' }
            $expectedImages=2
            if ($fixture.Direct) { $expectedImages=1 }
            if ($state.ImageProcesses -ne $expectedImages) { throw 'Process from another tree included' }
            $leaf=@($state.Rows | Where-Object { $_.ParentProcessId -eq $state.Process.Id -and $_.Name -ieq 'java.exe' })
            if ($leaf.Count -eq 1) { $fixture.Verified=$true; $fixture.JvmId=$state.Process.Id }
        }
    }
    Start-Sleep -Milliseconds 100
}
foreach ($fixture in $fixtures) {
    if (-not $fixture.Process.WaitForExit(15000)) { throw 'Fixture did not finish; no process forcibly terminated' }
    if (-not $fixture.Verified -or $fixture.Process.ExitCode -ne 0) { throw 'Fixture observation failed' }
    if ($fixture.Capture.NativeMode -ne 1 -or $fixture.Capture.MissingClass -ne 1) { throw 'Sanitized capture failed' }
}
if ($direct.JvmId -eq $nested.JvmId) { throw 'An unrelated JVM was selected for both roots' }
Write-Host 'PASS: direct JVM and nested launcher/JVM with same-name unrelated JVM; helper parent is actual JVM; sanitized capture'
$early=Start-Fixture $false 'exit17'
if (-not $early.Process.WaitForExit(15000)) { throw 'Early-exit fixture did not finish; no process forcibly terminated' }
if ($early.Process.ExitCode -ne 17) { throw 'Actual exit code was not preserved' }
Write-Host 'PASS: actual launcher exit code 17 preserved; fixtures exited naturally; no real MACE/helper/UAC or services'
