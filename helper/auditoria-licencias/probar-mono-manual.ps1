param(
    [Parameter(Mandatory=$true)][string]$HelperDirectory,
    [string]$NativeDll='native/build/fase2-paso4b/dev/Release/mace_native.dll',
    [ValidateSet('Read','Reconnect','CloseEarly')][string]$Scenario='Read',
    [switch]$RequireCpu
)
# USER EXECUTION ONLY. Launches real telemetry; Reconnect terminates only its own helper.
# Fresh Windows PowerShell x64 process for every invocation. No service operations.
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$saved=@{}; $envScoped=$false; $initialized=$false; $code=2
$diagStage='PREFLIGHT'; $diagReason='PREFLIGHT_NOT_COMPLETED'
$discovery=@{queries=0;rows=0;path_missing=0;prefix_match=0;birth_rejected=0}
function Same-MonoHelperPath([string]$actual,[string]$expected) {
    if (-not $actual -or -not $expected) { return $false }
    # GetFinalPathNameByHandleW (DLL) adds this DOS prefix; Resolve-Path does not.
    # Strip ONLY the extended DOS prefix. No basename, suffix or relative matching.
    if ($actual -match '^\\\\\?\\[A-Za-z]:\\') { $actual=$actual.Substring(4) }
    if ($expected -match '^\\\\\?\\[A-Za-z]:\\') { $expected=$expected.Substring(4) }
    return [string]::Equals($actual,$expected,[StringComparison]::OrdinalIgnoreCase)
}
try {
    if ([IntPtr]::Size -ne 8) { throw 'X64_REQUIRED' }
    $diagStage='DLL_PREFLIGHT'
    $dll=(Resolve-Path -LiteralPath $NativeDll).Path
    $diagStage='HELPER_PREFLIGHT'
    $helper=(Resolve-Path -LiteralPath (Join-Path $HelperDirectory 'Mace.Lhm.Helper.exe')).Path
    $marker=[Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes($dll))
    if (-not $marker.Contains('MACE_LHM_PROFILE=DEVELOPMENT')) { throw 'DEVELOPMENT_DLL_REQUIRED' }
    if ([Diagnostics.FileVersionInfo]::GetVersionInfo($dll).FileVersion -notmatch '^1\.5\.0') { throw 'DLL_VERSION_REQUIRED' }
    $diagStage='MANIFEST'
    & (Join-Path $repo 'helper/Verify-Artifacts.ps1') -Directory $HelperDirectory
    if (-not $?) { throw 'MANIFEST_FAILED' }
    $diagStage='TOKEN'
    $admin=([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    Write-Host ('CONTEXT administrator='+[int]$admin+' scenario='+$Scenario)
    if ($RequireCpu -and -not $admin) { throw 'REQUIRE_CPU_NEEDS_ADMIN' }
    $diagStage='CONFIRMATION'
    if ($Scenario -eq 'Reconnect' -and (Read-Host 'Escribe RECONECTAR para terminar SOLO el helper hijo de esta prueba') -cne 'RECONECTAR') { throw 'CANCELLED' }
    $diagStage='ENVIRONMENT'
    Get-ChildItem Env:MACE_* | ForEach-Object { $saved[$_.Name]=$_.Value }
    $envScoped=$true
    Get-ChildItem Env:MACE_* | Remove-Item
    $env:MACE_LHM_HELPER=$helper
    $env:MACE_LHM_HELPER_SHA256=(Get-FileHash -LiteralPath $helper -Algorithm SHA256).Hash
    $diagStage='BRIDGE_COMPILE'
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class MonoManualProbe {
 [DllImport("kernel32.dll",CharSet=CharSet.Unicode,ExactSpelling=true,SetLastError=true)] static extern IntPtr LoadLibraryExW(string p,IntPtr f,uint flags);
 [DllImport("kernel32.dll",CharSet=CharSet.Ansi,ExactSpelling=true)] static extern IntPtr GetProcAddress(IntPtr h,string n);
 [UnmanagedFunctionPointer(CallingConvention.Cdecl)] delegate int Init();
 [UnmanagedFunctionPointer(CallingConvention.Cdecl)] delegate void Close();
 [UnmanagedFunctionPointer(CallingConvention.Cdecl)] delegate int Poll(IntPtr p,uint n);
 static Init init; static Close close; static Poll poll;
 public static void Load(string p) {
  IntPtr h=LoadLibraryExW(p,IntPtr.Zero,0x1100); if(h==IntPtr.Zero) throw new InvalidOperationException("LOAD_FAILED");
  init=(Init)Marshal.GetDelegateForFunctionPointer(GetProcAddress(h,"CP_InitializeTelemetry"),typeof(Init));
  close=(Close)Marshal.GetDelegateForFunctionPointer(GetProcAddress(h,"CP_ShutdownTelemetry"),typeof(Close));
  poll=(Poll)Marshal.GetDelegateForFunctionPointer(GetProcAddress(h,"CP_PollCpuTelemetryV2"),typeof(Poll));
 }
 public static int Start(){return init();}
 public static void Shutdown(){close();}
 public static int[] Sample(){
  IntPtr p=Marshal.AllocHGlobal(56);
  try {
   byte[] z=new byte[56]; Marshal.Copy(z,0,p,56); int rc=poll(p,56);
   if(rc!=0) return new[]{rc,-1,-1,-1,-1,0};
   Marshal.Copy(p,z,0,56);
   if(BitConverter.ToInt32(z,0)!=56||BitConverter.ToInt32(z,4)!=2) throw new InvalidOperationException("LAYOUT_FAILED");
   int t=BitConverter.ToInt32(z,28), w=BitConverter.ToInt32(z,36);
   float tv=BitConverter.ToSingle(z,24),wv=BitConverter.ToSingle(z,32);
   bool coherent=!float.IsNaN(tv)&&!float.IsInfinity(tv)&&!float.IsNaN(wv)&&!float.IsInfinity(wv)&&(t==0||tv==0)&&(w==0||wv==0);
   return new[]{rc,t,w,BitConverter.ToInt32(z,44),BitConverter.ToInt32(z,20),coherent?1:0,(int)(tv*1000),(int)(wv*1000)};
  } finally {Marshal.FreeHGlobal(p);}
 }
}
'@
    $birth=[DateTime]::UtcNow
    function Own-Helpers {
        $discovery.queries++
        @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$PID" | Where-Object {
            $discovery.rows++
            if (-not $_.ExecutablePath) { $discovery.path_missing++ }
            $matches=Same-MonoHelperPath $_.ExecutablePath $helper
            if ($matches) {
                if (-not [string]::Equals($_.ExecutablePath,$helper,[StringComparison]::OrdinalIgnoreCase)) { $discovery.prefix_match++ }
                $recent=$_.CreationDate.ToUniversalTime() -ge $birth.AddSeconds(-1)
                if (-not $recent) { $discovery.birth_rejected++ }
                $recent
            } else { $false }
        })
    }
    $diagStage='BRIDGE_LOAD'
    [MonoManualProbe]::Load($dll)
    $diagStage='BRIDGE_START'
    if ([MonoManualProbe]::Start() -ne 0) { throw 'INITIALIZE_FAILED' }
    $initialized=$true; $baseline=$false; $valid=$false; $notElevated=$false; $sawHelper=$false
    $killed=$false; $oldId=0; $newId=$false; $invalidAfter=$false; $recovered=$false
    $duration=if($Scenario -eq 'CloseEarly'){1}elseif($Scenario -eq 'Reconnect'){75}else{20}
    $clock=[Diagnostics.Stopwatch]::StartNew()
    while ($clock.Elapsed.TotalSeconds -lt $duration) {
        $diagStage='BRIDGE_SAMPLE'
        $s=[MonoManualProbe]::Sample()
        if ($s[0] -ne 0 -or $s[5] -ne 1) { throw 'SAMPLE_FAILED' }
        $diagStage='OWN_HELPER_DISCOVERY'
        $children=@(Own-Helpers); $childId=0
        if($children.Count -eq 1){$childId=[int]$children[0].ProcessId}
        if($childId){$sawHelper=$true}
        Write-Host ('SAMPLE second='+[int]$clock.Elapsed.TotalSeconds+' helper_pid='+$childId+' cpu_t='+$s[1]+' cpu_p='+$s[2]+' cpu_u='+$s[3]+' provider='+$s[4]+' temp_milli_c='+$s[6]+' power_milli_w='+$s[7])
        if($s[1] -eq 0 -and $s[2] -eq 0){$valid=$true}
        if($s[1] -eq 9 -and $s[2] -eq 9){$notElevated=$true}
        if($childId -and $s[4] -eq 8 -and $s[1] -notin @(5,8,13,14,15,16)){$baseline=$true}
        if($killed){
            if($s[1] -ne 0 -and $s[2] -ne 0){$invalidAfter=$true}
            if($childId -and $childId -ne $oldId){$newId=$true}
            if($newId -and $invalidAfter -and $s[1] -eq $priorT -and $s[2] -eq $priorP){$recovered=$true}
        }
        if($Scenario -eq 'Reconnect' -and -not $killed -and $baseline -and $childId -and (-not $RequireCpu -or ($s[1] -eq 0 -and $s[2] -eq 0))){
            $diagStage='OWN_HELPER_TERMINATION'
            $target=Get-Process -Id $childId -ErrorAction Stop
            if(-not (Same-MonoHelperPath $target.MainModule.FileName $helper) -or $target.StartTime.ToUniversalTime() -lt $birth.AddSeconds(-1)){throw 'IDENTITY_CHANGED'}
            $priorT=$s[1];$priorP=$s[2];$oldId=$childId
            # Held Process object, exact executable + birth + direct parent discovery above.
            $target.Kill(); $killed=$true
            Write-Host 'ACTION own_helper_terminated=1'
        }
        Start-Sleep -Milliseconds 100
    }
    $diagStage='SHUTDOWN'
    [MonoManualProbe]::Shutdown(); $initialized=$false
    $diagStage='CLOSE_DISCOVERY'
    $until=[DateTime]::UtcNow.AddSeconds(5)
    do {$left=@(Own-Helpers);if(-not $left.Count){break};Start-Sleep -Milliseconds 100}while([DateTime]::UtcNow -lt $until)
    Write-Host ('CLOSE own_helpers_remaining='+$left.Count)
    $diagStage='CONDITIONS'
    $ok=$left.Count -eq 0
    if($Scenario -eq 'Read'){$ok=$ok -and $baseline -and (($admin -and (-not $RequireCpu -or $valid)) -or (-not $admin -and $notElevated))}
    if($Scenario -eq 'Reconnect'){$ok=$ok -and $killed -and $invalidAfter -and $newId -and $recovered}
    if($Scenario -eq 'CloseEarly'){$ok=$ok -and $sawHelper}
    if($ok){Write-Host 'PASS scope=native_probe_only ui_not_asserted=1';$code=0}else{
        $diagReason='MISSING_CONDITION'
        if($left.Count -ne 0){$diagReason='OWN_HELPERS_REMAIN'}
        elseif($Scenario -eq 'Read' -and -not $baseline){$diagReason='BASELINE_NOT_OBSERVED'}
        elseif($Scenario -eq 'Read' -and $admin -and $RequireCpu -and -not $valid){$diagReason='CPU_VALID_NOT_OBSERVED'}
        elseif($Scenario -eq 'Read' -and -not $admin -and -not $notElevated){$diagReason='NOT_ELEVATED_NOT_OBSERVED'}
        elseif($Scenario -eq 'Reconnect' -and -not $killed){$diagReason='OWN_HELPER_NOT_TERMINATED'}
        elseif($Scenario -eq 'Reconnect' -and -not $invalidAfter){$diagReason='INVALID_INTERVAL_NOT_OBSERVED'}
        elseif($Scenario -eq 'Reconnect' -and -not $newId){$diagReason='NEW_PID_NOT_OBSERVED'}
        elseif($Scenario -eq 'Reconnect' -and -not $recovered){$diagReason='RECOVERY_NOT_OBSERVED'}
        elseif($Scenario -eq 'CloseEarly' -and -not $sawHelper){$diagReason='OWN_HELPER_NOT_OBSERVED'}
        Write-Host 'NO_VERIFICADO reason=EXPECTED_OBSERVATION_MISSING'
    }
} catch {
    $diagReason='EXCEPTION_AT_STAGE'
    $allowed=@('X64_REQUIRED','DEVELOPMENT_DLL_REQUIRED','DLL_VERSION_REQUIRED','MANIFEST_FAILED','REQUIRE_CPU_NEEDS_ADMIN','CANCELLED','LOAD_FAILED','INITIALIZE_FAILED','SAMPLE_FAILED','LAYOUT_FAILED','IDENTITY_CHANGED')
    $exception=$_.Exception
    while($exception){
        if($exception.Message -cin $allowed){$diagReason=$exception.Message;break}
        $exception=$exception.InnerException
    }
    Write-Host ('NO_VERIFICADO reason=MANUAL_PROBE_INCOMPLETE hresult='+$_.Exception.HResult)
} finally {
    if($initialized){try{[MonoManualProbe]::Shutdown()}catch{Write-Host 'NO_VERIFICADO reason=SHUTDOWN_FAILED'}}
    if($envScoped){
        Get-ChildItem Env:MACE_* | Remove-Item
        foreach($key in $saved.Keys){Set-Item -LiteralPath ('Env:'+$key) -Value $saved[$key]}
    }
}
Write-Host ('DISCOVERY queries='+$discovery.queries+' rows='+$discovery.rows+' path_missing='+$discovery.path_missing+' prefix_match='+$discovery.prefix_match+' birth_rejected='+$discovery.birth_rejected)
if($code -eq 2){
    Write-Host ('DETAIL reason='+$diagReason+' stage='+$diagStage+' baseline='+[int][bool]$baseline+' cpu_valid='+[int][bool]$valid+' not_elevated='+[int][bool]$notElevated+' saw_helper='+[int][bool]$sawHelper+' killed='+[int][bool]$killed+' invalid_after='+[int][bool]$invalidAfter+' new_pid='+[int][bool]$newId+' recovered='+[int][bool]$recovered+' close_empty='+[int]($null -ne $left -and $left.Count -eq 0))
}
exit $code
