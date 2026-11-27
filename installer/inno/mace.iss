; Instalador de MACE para Windows x64 (Inno Setup 6). Decisiones D1/D2/D3/D6
; de helper/FASE3-DISENO.md y helper/FASE3-INSTALADOR-PASO1-COORDINADOR.md.
;
; Compilar (desde la raiz del repositorio):
;   ISCC.exe /DAppVersion=1.1.0 /DAppImage=packaging\dist\MACE
;            /DCoordinator=installer\coordinator\build\Release\mace-pawnio-setup.exe
;            /O<carpeta-salida> installer\inno\mace.iss
;
; Flujo: instala MACE en Program Files y DESPUES, si el usuario lo deja marcado
; (o en silencioso con /ACCEPT_PAWNIO=1), llama al coordinador, que verifica e
; instala PawnIO. Un fallo de PawnIO nunca deshace MACE. Al desinstalar se
; conservan PawnIO y los datos de cada usuario.

#ifndef AppVersion
  #error Falta /DAppVersion=<x.y.z>
#endif
#ifndef AppImage
  #error Falta /DAppImage=<carpeta de la app-image verificada>
#endif
#ifndef Coordinator
  #error Falta /DCoordinator=<mace-pawnio-setup.exe de producto>
#endif

[Setup]
; Misma familia que APP_UPGRADE_UUID de packaging/package-windows.bat.
AppId={{7F3C2A1E-9B4D-4C6E-8A1F-2D5E6B7C8D90}
AppName=MACE
AppVersion={#AppVersion}
AppVerName=MACE {#AppVersion}
AppPublisher=DanielDdeY
AppPublisherURL=https://github.com/DanielDdeY/MACE
AppSupportURL=https://github.com/DanielDdeY/MACE/issues
AppUpdatesURL=https://github.com/DanielDdeY/MACE/releases
VersionInfoVersion={#AppVersion}
; D6: Program Files x64 fijo, por maquina, sin selector de carpeta.
DefaultDirName={autopf}\MACE
DisableDirPage=yes
DefaultGroupName=MACE
DisableProgramGroupPage=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
MinVersion=10.0
; D6: no cerrar MACE automaticamente; se pide cerrarlo a mano.
CloseApplications=no
RestartApplications=no
UninstallDisplayIcon={app}\MACE.exe
UninstallDisplayName=MACE
OutputBaseFilename=MACE-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
SetupLogging=no

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "Crear un acceso directo en el escritorio"; GroupDescription: "Accesos directos:"

[Dirs]
Name: "{commonappdata}\MACE\InstallerLogs"

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#Coordinator}"; DestDir: "{app}\installer"; Flags: ignoreversion
; Copia temporal para detectar PawnIO antes de mostrar la pagina.
Source: "{#Coordinator}"; Flags: dontcopy

[Icons]
Name: "{group}\MACE"; Filename: "{app}\MACE.exe"
Name: "{group}\MACE Preview (datos simulados)"; Filename: "{app}\MACE-Preview.exe"
Name: "{autodesktop}\MACE"; Filename: "{app}\MACE.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\MACE.exe"; Description: "Abrir MACE"; Flags: nowait postinstall skipifsilent shellexec

[Code]
const
  RC_ABSENT = 14;
  COORDINATOR = 'mace-pawnio-setup.exe';

var
  PawnIoPage: TInputOptionWizardPage;
  PawnIoAbsent: Boolean;
  PawnIoResult: Integer;
  PawnIoRan: Boolean;
  RestartNeeded: Boolean;

function SilentConsent: Boolean;
begin
  Result := ExpandConstant('{param:ACCEPT_PAWNIO|0}') = '1';
end;

procedure DetectPawnIo;
var
  Code: Integer;
begin
  PawnIoAbsent := False;
  ExtractTemporaryFile(COORDINATOR);
  if Exec(ExpandConstant('{tmp}\' + COORDINATOR), '--detect-only', '', SW_HIDE,
          ewWaitUntilTerminated, Code) then
    PawnIoAbsent := (Code = RC_ABSENT);
end;

procedure InitializeWizard;
begin
  DetectPawnIo;
  PawnIoPage := CreateInputOptionPage(wpSelectTasks,
    'Sensores de temperatura y potencia',
    'Instalar el controlador PawnIO',
    'MACE lee la temperatura y la potencia de la CPU (Intel y AMD) y varios ' +
    'sensores de GPU Radeon mediante el controlador PawnIO. Sin el, esas ' +
    'lecturas aparecen como N/D; el uso de CPU, RAM, procesos y GPU NVIDIA/Intel ' +
    'funcionan igual.' + #13#10#13#10 +
    'Se instalara el setup oficial de PawnIO 2.2.0 sin modificar, tras ' +
    'comprobar su hash y su firma digital. Es un controlador del sistema: ' +
    'se conserva al desinstalar MACE y puede requerir reiniciar Windows.',
    False, False);
  PawnIoPage.Add('Instalar PawnIO (recomendado)');
  PawnIoPage.Values[0] := True;
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := (PageID = PawnIoPage.ID) and not PawnIoAbsent;
end;

function WantsPawnIo: Boolean;
begin
  if not PawnIoAbsent then
    Result := False
  else if WizardSilent then
    Result := SilentConsent
  else
    Result := PawnIoPage.Values[0];
end;

{ D6: si MACE o su helper estan abiertos desde esta instalacion, pedir cerrarlos. }
function MaceRunningFrom(Dir: String): Boolean;
var
  Locator, Service, Items: Variant;
  Query: String;
begin
  Result := False;
  try
    Locator := CreateOleObject('WbemScripting.SWbemLocator');
    Service := Locator.ConnectServer('.', 'root\CIMV2');
    StringChangeEx(Dir, '\', '\\', True);
    StringChangeEx(Dir, '''', '\''', True);
    Query := 'SELECT ProcessId FROM Win32_Process WHERE ExecutablePath LIKE ''' + Dir + '\\%''';
    Items := Service.ExecQuery(Query);
    Result := Items.Count > 0;
  except
    Result := False;
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  Result := '';
  if MaceRunningFrom(ExpandConstant('{app}')) then
    Result := 'MACE esta abierto. Cierralo (incluida la ventana de vista previa) ' +
              'y vuelve a ejecutar el instalador. No se ha modificado nada.';
end;

function PawnIoMessage(Code: Integer): String;
begin
  case Code of
    0:    Result := 'PawnIO se instalo correctamente.';
    3010: Result := 'PawnIO se instalo. Reinicia Windows para habilitar los sensores de CPU.';
    10, 11: Result := 'PawnIO ya estaba instalado; no se modifico.';
    13:   Result := 'PawnIO no se instalo: faltan permisos de administrador.';
    20, 21: Result := 'PawnIO no se instalo: el instalador incluido no paso la verificacion de integridad o de firma.';
    22:   Result := 'PawnIO no se instalo: no se pudo comprobar su firma (sin conexion). Puedes instalarlo despues desde MACE.';
    40:   Result := 'La instalacion de PawnIO sigue en curso o no respondio a tiempo. Reinicia Windows antes de abrir MACE si no ves la temperatura de la CPU.';
  else
    Result := 'PawnIO no se pudo instalar (codigo ' + IntToStr(Code) + '). MACE funciona igualmente; puedes instalarlo despues desde MACE.';
  end;
end;

procedure RunPawnIo;
var
  Params: String;
begin
  PawnIoRan := True;
  Params := '--setup "' + ExpandConstant('{app}\app\prerequisites\PawnIO_setup.exe') + '"' +
            ' --log "' + ExpandConstant('{commonappdata}\MACE\InstallerLogs\pawnio.log') + '"';
  WizardForm.StatusLabel.Caption := 'Instalando el controlador PawnIO...';
  { El coordinador termina en <= 180 s y nunca mata el setup de PawnIO (D3). }
  if not Exec(ExpandConstant('{app}\installer\' + COORDINATOR), Params, '', SW_HIDE,
              ewWaitUntilTerminated, PawnIoResult) then
    PawnIoResult := -1;
  RestartNeeded := (PawnIoResult = 3010);
  Log('PawnIO coordinador: ' + IntToStr(PawnIoResult));
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  { D2: MACE ya esta instalado; PawnIO va despues y su fallo no lo revierte. }
  if (CurStep = ssPostInstall) and WantsPawnIo then
    RunPawnIo;
end;

function NeedRestart: Boolean;
begin
  Result := RestartNeeded;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if (CurPageID = wpFinished) and PawnIoRan then
    WizardForm.FinishedLabel.Caption := WizardForm.FinishedLabel.Caption + #13#10#13#10 +
      PawnIoMessage(PawnIoResult);
end;
