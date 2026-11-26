param([Parameter(Mandatory=$true)][string]$Source,[Parameter(Mandatory=$true)][string]$Directory,[ValidateSet('Write','Verify')][string]$Action)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -ne 5){throw 'WINDOWS_POWERSHELL_51_REQUIRED'}
# Resolve the built-in module from this engine, not an inherited module search path.
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Utility/Microsoft.PowerShell.Utility.psd1') -Force
[void](Get-Command Get-FileHash -ErrorAction Stop)
$script=if($Action -eq 'Write'){'Write-RuntimeManifest.ps1'}else{'Verify-Artifacts.ps1'}
& (Join-Path $Source ('helper/'+$script)) -Directory $Directory
if(-not $?){exit 1}
