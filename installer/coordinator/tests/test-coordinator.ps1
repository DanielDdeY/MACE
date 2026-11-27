param(
    [Parameter(Mandatory)][string]$BuildDirectory,
    # Setup oficial de PawnIO ya descargado y con hash comprobado (opcional).
    [string]$RealPawnIoSetup
)
# Pruebas del coordinador con un setup SIMULADO. No instala drivers, no usa el
# setup real salvo en modo --verify-only y nunca termina procesos: el caso de
# espera vencida deja terminar al simulado por si solo.
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -ne 5) { throw 'Ejecutar con Windows PowerShell 5.1.' }

$release = Join-Path (Resolve-Path -LiteralPath $BuildDirectory).Path 'Release'
$prod = Join-Path $release 'mace-pawnio-setup.exe'
$test = Join-Path $release 'mace-pawnio-setup-test.exe'
$stubSource = Join-Path $release 'stub_setup.exe'
foreach ($f in $prod, $test, $stubSource) { if (-not (Test-Path -LiteralPath $f)) { throw "Falta $f" } }

# Ruta con espacios para comprobar el entrecomillado del comando.
$work = Join-Path $env:RUNNER_TEMP 'coordinador pruebas'
if (-not $env:RUNNER_TEMP) { $work = Join-Path ([IO.Path]::GetTempPath()) ('mace coordinador ' + [guid]::NewGuid().ToString('N')) }
New-Item -ItemType Directory -Force -Path $work | Out-Null
$stub = Join-Path $work 'PawnIO_setup.exe'
Copy-Item -LiteralPath $stubSource -Destination $stub -Force
$stubSha = (Get-FileHash -LiteralPath $stub -Algorithm SHA256).Hash.ToLowerInvariant()
$marker = Join-Path $work 'ejecutado.txt'
$absentService = 'MACE-PawnIO-Prueba-' + [guid]::NewGuid().ToString('N')

$script:passed = 0
$script:failed = @()
# Se ejecutan todos los casos; cada fallo se publica como anotacion de Actions.
function Assert-Case([bool]$condition, [string]$label, [string]$detail) {
    if ($condition) { $script:passed++; return }
    $message = ("TEST_FAILED $label :: $detail" -replace "`r?`n", ' | ')
    Write-Host "::error title=coordinador-pawnio::$message"
    $script:failed += $label
}
trap {
    Write-Host ("::error title=coordinador-pawnio::EXCEPTION " + ("$_" -replace "`r?`n", ' | '))
    exit 1
}

function Invoke-Coordinator([string]$exe, [string[]]$arguments, [hashtable]$stubEnv = @{}) {
    foreach ($k in 'MACE_STUB_EXIT', 'MACE_STUB_SLEEP_MS', 'MACE_STUB_MARKER') { Remove-Item "Env:$k" -ErrorAction SilentlyContinue }
    foreach ($k in $stubEnv.Keys) { Set-Item "Env:$k" -Value ([string]$stubEnv[$k]) }
    Remove-Item -LiteralPath $marker -ErrorAction SilentlyContinue
    # En 5.1, stderr redirigido con 'Stop' se convierte en error terminante.
    $ErrorActionPreference = 'Continue'
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $out = @(& $exe @arguments 2>&1 | ForEach-Object { "$_" }) -join "`n"
    $code = $LASTEXITCODE
    $watch.Stop()
    $ErrorActionPreference = 'Stop'
    foreach ($k in $stubEnv.Keys) { Remove-Item "Env:$k" -ErrorAction SilentlyContinue }
    [pscustomobject]@{ Code = $code; Out = $out; Ms = $watch.ElapsedMilliseconds; Ran = (Test-Path -LiteralPath $marker) }
}

# Ganchos comunes: servicio inexistente y sin exigir token elevado (portabilidad).
$common = @('--test-skip-elevation', '--test-service', $absentService)
$base = @('--setup', $stub, '--test-sha256', $stubSha, '--test-skip-signature') + $common
$markerEnv = @{ MACE_STUB_MARKER = $marker }

# 1. Exito: el hijo devuelve 0 y la instalacion se da por corroborada.
$r = Invoke-Coordinator $test ($base + '--test-assume-installed') ($markerEnv + @{ MACE_STUB_EXIT = 0 })
Assert-Case ($r.Code -eq 0 -and $r.Ran -and $r.Out -match 'result=INSTALLED code=0' -and $r.Out -match 'child=0') 'instalado' $r.Out

# 2. 3010 del hijo se propaga como reinicio requerido.
$r = Invoke-Coordinator $test ($base + '--test-assume-installed') ($markerEnv + @{ MACE_STUB_EXIT = 3010 })
Assert-Case ($r.Code -eq 3010 -and $r.Out -match 'INSTALLED_REBOOT_REQUIRED') 'reinicio_3010' $r.Out

# 3. 0 sin servicio registrado despues no acredita instalacion.
$r = Invoke-Coordinator $test $base ($markerEnv + @{ MACE_STUB_EXIT = 0 })
Assert-Case ($r.Code -eq 31 -and $r.Out -match 'NOT_CORROBORATED') 'no_corroborado' $r.Out

# 4. Error del hijo: codigo conservado en el registro, sin reinterpretarlo.
$r = Invoke-Coordinator $test ($base + '--test-assume-installed') ($markerEnv + @{ MACE_STUB_EXIT = 1603 })
Assert-Case ($r.Code -eq 30 -and $r.Out -match 'child=1603' -and $r.Out -match 'SETUP_FAILED') 'fallo_hijo' $r.Out

# 5. Hash distinto: no se ejecuta.
$wrong = ('0' * 64)
$r = Invoke-Coordinator $test (@('--setup', $stub, '--test-sha256', $wrong, '--test-skip-signature') + $common) $markerEnv
Assert-Case ($r.Code -eq 20 -and -not $r.Ran -and $r.Out -match 'hash=mismatch') 'hash_distinto' $r.Out

# 6. Setup inexistente.
$r = Invoke-Coordinator $test (@('--setup', (Join-Path $work 'no-existe.exe'), '--test-sha256', $stubSha, '--test-skip-signature') + $common) $markerEnv
Assert-Case ($r.Code -eq 20 -and -not $r.Ran) 'setup_ausente' $r.Out

# 7. Firma: el simulado no esta firmado por namazso.eu y se rechaza.
$r = Invoke-Coordinator $test (@('--setup', $stub, '--test-sha256', $stubSha) + $common) $markerEnv
Assert-Case ($r.Code -eq 21 -and -not $r.Ran -and $r.Out -match 'signature=invalid') 'firma_invalida' $r.Out

# 8. Servicio ya presente (otra version): no se ejecuta ni se toca.
$r = Invoke-Coordinator $test @('--setup', $stub, '--test-sha256', $stubSha, '--test-skip-signature', '--test-skip-elevation', '--test-service', 'EventLog') $markerEnv
Assert-Case ($r.Code -eq 11 -and -not $r.Ran -and $r.Out -match 'detect=OTHER') 'presente_no_se_toca' $r.Out

# 9. Espera vencida: devuelve TIMEOUT sin matar al hijo, que termina solo.
$r = Invoke-Coordinator $test ($base + @('--test-timeout-ms', '1500')) ($markerEnv + @{ MACE_STUB_SLEEP_MS = 8000 })
Assert-Case ($r.Code -eq 40 -and $r.Out -match 'child=running' -and $r.Ms -lt 6000) 'timeout' "$($r.Out) ms=$($r.Ms)"
$childPid = if ($r.Out -match 'child_pid=(\d+)') { [int]$Matches[1] } else { 0 }
if ($childPid -gt 0) {
    $alive = Get-Process -Id $childPid -ErrorAction SilentlyContinue
    Assert-Case ($null -ne $alive) 'timeout_no_mata' "pid=$childPid"
    Wait-Process -Id $childPid -Timeout 30 -ErrorAction SilentlyContinue
    Assert-Case ($null -eq (Get-Process -Id $childPid -ErrorAction SilentlyContinue)) 'timeout_hijo_termina_solo' "pid=$childPid"
} else {
    Assert-Case $false 'timeout_pid' $r.Out
}

# 10. El registro no incluye rutas ni el hash completo.
$log = Join-Path $work 'pawnio.log'
$r = Invoke-Coordinator $test ($base + @('--test-assume-installed', '--log', $log)) ($markerEnv + @{ MACE_STUB_EXIT = 0 })
$logText = Get-Content -LiteralPath $log -Raw
Assert-Case ($r.Code -eq 0 -and $logText -match 'result=INSTALLED') 'registro_escrito' $logText
Assert-Case ($logText -notmatch '[A-Za-z]:\\' -and $logText -notmatch $stubSha) 'registro_saneado' $logText

# 11. Binario de producto: sin ganchos de prueba.
$r = Invoke-Coordinator $prod @()
Assert-Case ($r.Code -eq 2) 'producto_sin_args' $r.Out
$r = Invoke-Coordinator $prod @('--setup', $stub, '--test-skip-signature')
Assert-Case ($r.Code -eq 2) 'producto_rechaza_ganchos' $r.Out
$r = Invoke-Coordinator $prod @('--verify-only', '--setup', $stub) $markerEnv
Assert-Case ($r.Code -eq 20 -and -not $r.Ran) 'producto_pin_fijo' $r.Out
$r = Invoke-Coordinator $prod @('--detect-only')
Assert-Case ($r.Code -in 10, 11, 12, 14 -and $r.Out -match 'mode=detect') 'producto_detecta' $r.Out

# 12. Setup oficial real: solo verificacion de hash y firma, NUNCA se ejecuta.
if ($RealPawnIoSetup) {
    $r = Invoke-Coordinator $prod @('--verify-only', '--setup', $RealPawnIoSetup)
    Assert-Case ($r.Code -eq 0 -and $r.Out -match 'hash=ok signature=ok') 'pawnio_real_verificado' $r.Out
    $tampered = Join-Path $work 'PawnIO_alterado.exe'
    Copy-Item -LiteralPath $RealPawnIoSetup -Destination $tampered -Force
    [IO.File]::AppendAllText($tampered, 'x')
    $r = Invoke-Coordinator $prod @('--verify-only', '--setup', $tampered)
    Assert-Case ($r.Code -eq 20 -and $r.Out -match 'hash=mismatch') 'pawnio_alterado_rechazado' $r.Out
}

if ($script:failed.Count) {
    Write-Host "FAIL failed=$($script:failed.Count) passed=$script:passed cases=$($script:failed -join ',')"
    exit 1
}
Write-Host "PASS checks=$script:passed real_pawnio=$([int][bool]$RealPawnIoSetup) drivers_installed=0 process_termination=0"
