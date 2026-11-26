param([ValidateSet('Stage','Payload')][string]$Action,[Parameter(Mandatory=$true)][string]$Source,[Parameter(Mandatory=$true)][string]$Root,[Parameter(Mandatory=$true)][string]$JavaExe,[Parameter(Mandatory=$true)][string]$PawnIoInstaller)
# Windows PowerShell 5.1. Scope-local Authenticode adapter forbids URL retrieval.
# Does not execute PawnIO, start the real helper or change any product script.
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -ne 5){throw 'POWERSHELL_51_REQUIRED'}
Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Utility\Microsoft.PowerShell.Utility.psd1')
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class CandidateCachedTrust {
 [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)] struct FileInfo { public UInt32 size; public IntPtr path; public IntPtr file; public IntPtr subject; }
 [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)] struct Data { public UInt32 size; public IntPtr policy; public IntPtr sip; public UInt32 ui; public UInt32 revocation; public UInt32 choice; public IntPtr file; public UInt32 action; public IntPtr state; public IntPtr url; public UInt32 flags; public UInt32 context; public IntPtr signatures; }
 [DllImport("wintrust.dll",ExactSpelling=true,CharSet=CharSet.Unicode)] static extern int WinVerifyTrust(IntPtr window,ref Guid action,ref Data data);
 public static int Verify(string path) {
  Guid action=new Guid("00AAC56B-CD44-11D0-8CC2-00C04FC295EE");
  FileInfo file=new FileInfo();file.size=(UInt32)Marshal.SizeOf(typeof(FileInfo));file.path=Marshal.StringToCoTaskMemUni(path);
  IntPtr pointer=Marshal.AllocHGlobal(Marshal.SizeOf(typeof(FileInfo)));Marshal.StructureToPtr(file,pointer,false);
  Data data=new Data();data.size=(UInt32)Marshal.SizeOf(typeof(Data));data.ui=2;data.choice=1;data.file=pointer;data.action=1;data.flags=0x1000|0x10;
  try {return WinVerifyTrust(new IntPtr(-1),ref action,ref data);}
  finally {data.action=2;WinVerifyTrust(new IntPtr(-1),ref action,ref data);Marshal.FreeHGlobal(pointer);Marshal.FreeCoTaskMem(file.path);}
 }
}
'@
function Get-AuthenticodeSignature {
 param([string]$LiteralPath)
 $status=[CandidateCachedTrust]::Verify($LiteralPath)
 if($status -ne 0){throw ('CACHED_AUTHENTICODE_FAILED hresult='+$status)}
 [pscustomobject]@{Status='Valid'}
}
$prod=Join-Path $Root 'native/prod/Release/mace_native.dll'
$helper=Join-Path $Root 'helper-publish-true'
if($Action -eq 'Stage') {
 $stage=Join-Path $Source 'packaging/output/input'
 & (Join-Path $Source 'packaging/stage-runtime.ps1') -StageDirectory $stage -AppJar (Join-Path $Source 'app/target/mace-app-1.0.0-SNAPSHOT.jar') -NativeDll $prod -NativeSha256 (Get-FileHash -LiteralPath $prod -Algorithm SHA256).Hash -HelperDirectory $helper -PawnIoInstaller $PawnIoInstaller -SigningScript ''
} else {
 & (Join-Path $Source 'packaging/tests/test-runtime-payload.ps1') -NativeDll $prod -HelperDirectory $helper -PawnIoInstaller $PawnIoInstaller -JavaExe $JavaExe -DevelopmentDll (Join-Path $Root 'native/dev/Release/mace_native.dll')
}
Write-Host 'CACHED_TRUST network_retrieval=0 revocation_online=0 product_script_modified=0'
