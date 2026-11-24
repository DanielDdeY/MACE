# Development harness only; never copied into the app-image.
function Test-ImageSamePath([string]$Actual,[string]$Expected) {
    if (-not $Actual -or -not $Expected) { return $false }
    if ($Actual.StartsWith('\\?\')) { $Actual=$Actual.Substring(4) }
    if ($Expected.StartsWith('\\?\')) { $Expected=$Expected.Substring(4) }
    return [IO.Path]::GetFullPath($Actual).Equals([IO.Path]::GetFullPath($Expected),[StringComparison]::OrdinalIgnoreCase)
}

function Find-ImageJvmProcess {
    param([Diagnostics.Process]$Launcher,[string]$Executable,[string]$JvmLibrary,[DateTime]$StartedUtc)
    $rows=@(Get-CimInstance Win32_Process)
    $owned=[Collections.Generic.HashSet[int]]::new()
    [void]$owned.Add($Launcher.Id)
    # Root handle must still refer to the process we started, never a reused PID.
    $Launcher.Refresh()
    if ($Launcher.HasExited) { return [pscustomobject]@{Process=$null;ImageProcesses=0;JvmProcesses=0;Rows=$rows} }
    $root=@($rows | Where-Object { $_.ProcessId -eq $Launcher.Id })
    if ($root.Count -ne 1 -or -not (Test-ImageSamePath $root[0].ExecutablePath $Executable) -or
        [math]::Abs(($root[0].CreationDate.ToUniversalTime()-$StartedUtc).TotalMilliseconds) -gt 1) {
        throw 'IMAGE_ROOT_IDENTITY_UNVERIFIED'
    }
    do {
        $changed=$false
        foreach ($row in $rows) {
            if ($row.ProcessId -ne $row.ParentProcessId -and $owned.Contains([int]$row.ParentProcessId) -and
                $row.CreationDate -and $row.CreationDate.ToUniversalTime() -ge $StartedUtc.AddMilliseconds(-1)) {
                if ($owned.Add([int]$row.ProcessId)) { $changed=$true }
            }
        }
    } while ($changed)
    $candidates=@($rows | Where-Object { $owned.Contains([int]$_.ProcessId) -and (Test-ImageSamePath $_.ExecutablePath $Executable) })
    $jvms=@()
    foreach ($row in $candidates) {
        $candidate=Get-Process -Id $row.ProcessId -ErrorAction SilentlyContinue
        if (-not $candidate) { continue }
        try {
            if ([math]::Abs(($candidate.StartTime.ToUniversalTime()-$row.CreationDate.ToUniversalTime()).TotalMilliseconds) -gt 1) { throw 'IMAGE_PROCESS_IDENTITY_UNVERIFIED' }
            $modules=@($candidate.Modules)
            $vm=@($modules | Where-Object { $_.ModuleName -ieq 'jvm.dll' })
            if ($vm.Count -gt 1 -or ($vm.Count -eq 1 -and -not (Test-ImageSamePath $vm[0].FileName $JvmLibrary))) { throw 'IMAGE_JVM_LOCATION_MISMATCH' }
            if ($vm.Count -eq 1) { $jvms+=$candidate }
        } catch {
            $candidate.Refresh()
            if (-not $candidate.HasExited) { throw }
        }
    }
    if ($jvms.Count -gt 1) { throw 'IMAGE_JVM_AMBIGUOUS' }
    $selected=$null
    if ($jvms.Count -eq 1) { $selected=$jvms[0] }
    return [pscustomobject]@{Process=$selected;ImageProcesses=$candidates.Count;JvmProcesses=$jvms.Count;Rows=$rows}
}

function New-ImageOutputSummary {
    if (-not ('Mace.PackagingTests.ImageOutputSummary' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.Diagnostics;
using System.Threading;
namespace Mace.PackagingTests {
    public sealed class ImageOutputSummary {
        public int NativeMode, MockMode, FxStartFailure, MissingClass, UnsatisfiedLink, OutOfMemory, OutputLines;
        public void Observe(string line) {
            if (line == null) return;
            Interlocked.Increment(ref OutputLines);
            if (line.IndexOf("[MACE] Modo NATIVO", StringComparison.Ordinal) >= 0) Interlocked.Exchange(ref NativeMode, 1);
            if (line.IndexOf("[MACE] Modo MOCK", StringComparison.Ordinal) >= 0) Interlocked.Exchange(ref MockMode, 1);
            if (line.IndexOf("Exception in Application start method", StringComparison.Ordinal) >= 0) Interlocked.Exchange(ref FxStartFailure, 1);
            if (line.IndexOf("java.lang.NoClassDefFoundError", StringComparison.Ordinal) >= 0 || line.IndexOf("java.lang.ClassNotFoundException", StringComparison.Ordinal) >= 0) Interlocked.Exchange(ref MissingClass, 1);
            if (line.IndexOf("java.lang.UnsatisfiedLinkError", StringComparison.Ordinal) >= 0) Interlocked.Exchange(ref UnsatisfiedLink, 1);
            if (line.IndexOf("java.lang.OutOfMemoryError", StringComparison.Ordinal) >= 0) Interlocked.Exchange(ref OutOfMemory, 1);
        }
        public void Attach(Process process) {
            process.OutputDataReceived += (s,e) => Observe(e.Data);
            process.ErrorDataReceived += (s,e) => Observe(e.Data);
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
        }
    }
}
'@
    }
    return [Mace.PackagingTests.ImageOutputSummary]::new()
}
