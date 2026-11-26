param([string]$CanonicalFile)
# Read-only/synthetic checks. Never loads the product DLL, starts a helper or kills a process.
$ErrorActionPreference='Stop'
$scriptFile=Join-Path $PSScriptRoot 'probar-mono-manual.ps1'
$tokens=$null; $errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($scriptFile,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'SCRIPT_SYNTAX_FAILED'}
$passed=0
function Assert-Case([bool]$condition,[string]$label){
    if(-not $condition){throw ('TEST_FAILED '+$label)}
    $script:passed++
}
foreach($name in @('Same-MonoHelperPath','Own-Helpers')){
    $node=$ast.Find({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq $name},$true)
    if(-not $node){throw 'FUNCTION_MISSING'}
    # Execute definitions only, not the surrounding manual probe.
    Invoke-Expression $node.Extent.Text
}
$helper='C:\fixture with spaces\Mace.Lhm.Helper.exe'
$extended='\\?\'+$helper
Assert-Case (Same-MonoHelperPath $helper $helper) 'same'
Assert-Case (Same-MonoHelperPath $extended $helper) 'extended_actual'
Assert-Case (Same-MonoHelperPath $helper $extended) 'extended_expected'
Assert-Case (Same-MonoHelperPath $helper.ToUpperInvariant() $helper) 'case'
Assert-Case (-not (Same-MonoHelperPath $null $helper)) 'missing_path'
Assert-Case (-not (Same-MonoHelperPath 'Mace.Lhm.Helper.exe' $helper)) 'basename_rejected'
Assert-Case (-not (Same-MonoHelperPath ('D'+$helper.Substring(1)) $helper)) 'different_volume'
Assert-Case (-not (Same-MonoHelperPath ($helper+'.other') $helper)) 'different_file'
Assert-Case (-not (Same-MonoHelperPath ('\\?\UNC\server\share\Mace.Lhm.Helper.exe') $helper)) 'unc_not_dos'
Assert-Case (-not [string]::Equals($extended,$helper,[StringComparison]::OrdinalIgnoreCase)) 'old_comparison_fails'

$birth=[DateTime]::UtcNow
$discovery=@{queries=0;rows=0;path_missing=0;prefix_match=0;birth_rejected=0}
$rows=@()
function Get-CimInstance {
    param([string]$ClassName,[string]$Filter)
    if($ClassName -ne 'Win32_Process' -or $Filter -cne "ParentProcessId=$PID"){throw 'PARENT_FILTER_CHANGED'}
    # Simulate the provider enforcing its exact direct-parent filter.
    @($rows | Where-Object {$_.ParentProcessId -eq $PID})
}
function Row([int]$id,[string]$path,[DateTime]$created,[int]$parent=$PID){
    [pscustomobject]@{ProcessId=$id;ExecutablePath=$path;CreationDate=$created;ParentProcessId=$parent}
}
$rows=@(Row 101 $extended $birth)
$found=@(Own-Helpers)
Assert-Case ($found.Count -eq 1 -and $found[0].ProcessId -eq 101) 'extended_child_found'
Assert-Case ($discovery.prefix_match -eq 1) 'safe_prefix_counter'
$rows=@(Row 102 $helper $birth.AddSeconds(-2))
Assert-Case (@(Own-Helpers).Count -eq 0) 'old_child_rejected'
Assert-Case ($discovery.birth_rejected -eq 1) 'safe_birth_counter'
$rows=@(Row 103 $helper $birth.AddSeconds(-1))
Assert-Case (@(Own-Helpers).Count -eq 1) 'birth_boundary_preserved'
$rows=@(Row 104 $helper $birth ($PID+1))
Assert-Case (@(Own-Helpers).Count -eq 0) 'foreign_parent_rejected'
$rows=@(Row 105 '' $birth)
Assert-Case (@(Own-Helpers).Count -eq 0) 'invisible_path_rejected'
Assert-Case ($discovery.path_missing -eq 1) 'safe_visibility_counter'
$rows=@(Row 106 ($helper+'.other') $birth)
Assert-Case (@(Own-Helpers).Count -eq 0) 'foreign_executable_rejected'
$rows=@((Row 107 $helper $birth),(Row 108 $extended $birth))
$children=@(Own-Helpers);$childId=0
if($children.Count -eq 1){$childId=[int]$children[0].ProcessId}
Assert-Case ($children.Count -eq 2 -and $childId -eq 0) 'ambiguous_children_rejected'

# Compare approval flags, durations and sleeps to the separate approved-motives commit.
$repo=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$reference=(& git -C $repo show '808d3bc:helper/auditoria-licencias/probar-mono-manual.ps1') -join "`n"
if($LASTEXITCODE -ne 0){throw 'REFERENCE_UNAVAILABLE'}
$referenceAst=[Management.Automation.Language.Parser]::ParseInput($reference,[ref]$tokens,[ref]$errors)
$guarded=@('$duration','$ok','$baseline','$valid','$notElevated','$sawHelper','$killed','$invalidAfter','$newId','$recovered')
function Approval-Text($tree){
    @($tree.FindAll({param($n) $n -is [Management.Automation.Language.AssignmentStatementAst] -and $n.Left.Extent.Text -cin $guarded},$true) | ForEach-Object {$_.Extent.Text.Trim()}) -join "`n"
}
Assert-Case ((Approval-Text $ast) -ceq (Approval-Text $referenceAst)) 'criteria_and_windows_unchanged'
function Sleep-Text($tree){
    @($tree.FindAll({param($n) $n -is [Management.Automation.Language.CommandAst] -and $n.GetCommandName() -eq 'Start-Sleep'},$true) | ForEach-Object {$_.Extent.Text.Trim()}) -join "`n"
}
Assert-Case ((Sleep-Text $ast) -ceq (Sleep-Text $referenceAst)) 'sleep_intervals_unchanged'

# Execute only the final diagnostic branch with synthetic observations.
$decision=$ast.Find({param($n)
    $n -is [Management.Automation.Language.IfStatementAst] -and $n.Clauses[0].Item1.Extent.Text -ceq '$ok'
},$true)
if(-not $decision){throw 'DECISION_MISSING'}
$decisionBlock=[scriptblock]::Create($decision.Extent.Text)
$reasonCases=@(
    @('Read','left','OWN_HELPERS_REMAIN'),
    @('Read','baseline','BASELINE_NOT_OBSERVED'),
    @('Read','valid','CPU_VALID_NOT_OBSERVED'),
    @('Read','notElevated','NOT_ELEVATED_NOT_OBSERVED'),
    @('Reconnect','killed','OWN_HELPER_NOT_TERMINATED'),
    @('Reconnect','invalidAfter','INVALID_INTERVAL_NOT_OBSERVED'),
    @('Reconnect','newId','NEW_PID_NOT_OBSERVED'),
    @('Reconnect','recovered','RECOVERY_NOT_OBSERVED'),
    @('CloseEarly','sawHelper','OWN_HELPER_NOT_OBSERVED')
)
foreach($case in $reasonCases){
    $Scenario=$case[0];$left=@();$baseline=$true;$valid=$true;$notElevated=$true
    $admin=$true;$RequireCpu=$true;$killed=$true;$invalidAfter=$true;$newId=$true
    $recovered=$true;$sawHelper=$true;$ok=$false;$diagReason='';$code=2
    if($case[1] -eq 'left'){$left=@(101)}else{Set-Variable -Name $case[1] -Value $false}
    if($case[1] -eq 'notElevated'){$admin=$false;$RequireCpu=$false}
    # Dot-source to observe assignments; discard fixed Write-Host text from fixtures.
    . $decisionBlock 6>$null
    Assert-Case ($diagReason -ceq $case[2] -and $code -eq 2) ('reason_'+$case[1])
}

# Real subprocess, but deliberate preflight failure BEFORE any bridge/native loading.
$output=@(& "$PSHOME\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File $scriptFile -HelperDirectory '.' -NativeDll 'native/build/__mono_missing__/absent.dll' 2>&1)
$exitCode=$LASTEXITCODE
$textOutput=$output -join "`n"
Assert-Case ($exitCode -eq 2 -and $textOutput -match 'DETAIL reason=EXCEPTION_AT_STAGE stage=DLL_PREFLIGHT') 'exit2_reason_preflight'
Assert-Case ($textOutput -notmatch '[A-Za-z]:[\\/]|S-1-\d-|(?i)(?<![0-9a-f])[0-9a-f]{64}(?![0-9a-f])') 'preflight_output_sanitized'

if($CanonicalFile){
    # Read a file handle, not a process; no DLL from the project is loaded.
    Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;
public static class MonoCanonicalReadOnly {
 [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)]
 public static extern uint GetFinalPathNameByHandle(SafeFileHandle file,StringBuilder name,uint length,uint flags);
}
'@
    $resolved=(Resolve-Path -LiteralPath $CanonicalFile).Path
    $stream=[IO.File]::Open($resolved,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
    try {
        $buffer=New-Object Text.StringBuilder 32768
        $length=[MonoCanonicalReadOnly]::GetFinalPathNameByHandle($stream.SafeFileHandle,$buffer,32768,0)
        Assert-Case ($length -gt 0 -and $length -lt 32768) 'real_canonical_name_read'
        Assert-Case (Same-MonoHelperPath $buffer.ToString() $resolved) 'real_canonical_equivalence'
        Write-Host ('CANONICAL_READONLY old_equal='+[int][string]::Equals($buffer.ToString(),$resolved,[StringComparison]::OrdinalIgnoreCase)+' corrected_equal=1')
    } finally {$stream.Dispose()}
}
Write-Host ('PASS checks='+$passed+' synthetic_discovery=1 product_executed=0 process_termination=0')
