"""One candidate image, offline, after all three normal-token regression summaries.
Never executes a launcher/helper/PawnIO installer or replaces the reference.
"""
import stat
from candidata_contexto import *

def main():
    for name in ('helper-unit-summary.json','ctest-dev-summary.json','ctest-prod-summary.json','java-summary.json'):
        value=json.loads((EVIDENCE/name).read_text())
        if any(value.get(key,0) for key in ('exit','failed','failures','errors')):raise RuntimeError('REGRESSION_NOT_PASSED')
    stage=SOURCE/'packaging/output/input';runtime=SOURCE/'packaging/runtime';dist=SOURCE/'packaging/dist'
    if any(p.exists() for p in (stage,runtime,dist)):raise RuntimeError('IMAGE_OUTPUT_NOT_FRESH')
    command,env,jdk=maven()
    run('java-production-compile',command+['process-resources','org.apache.maven.plugins:maven-compiler-plugin:3.13.0:compile'],SOURCE/'app',env)
    profile=SOURCE/'app/target/classes/com/mace/infrastructure/nativebridge/native-profile.properties'
    if profile.read_text().strip()!='profile=production':raise RuntimeError('JAVA_PRODUCTION_PROFILE_MISSING')
    stage.mkdir(parents=True);dist.mkdir(parents=True)
    run('java-production-jar-runtime',command+['org.apache.maven.plugins:maven-jar-plugin:3.5.0:jar','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies','-DincludeScope=runtime','-DoutputDirectory='+str(stage)],SOURCE/'app',env)
    pawn=REPO/'packaging/dist/MACE/app/prerequisites/PawnIO_setup.exe'
    wrapper=REPO/'helper/auditoria-licencias/empaquetado-offline-candidata.ps1'
    run('candidate-stage',['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',wrapper,'-Action','Stage','-Source',SOURCE,'-Root',ROOT,'-JavaExe',jdk/'bin/java.exe','-PawnIoInstaller',pawn])
    run('candidate-jlink',[jdk/'bin/jlink.exe','--module-path',jdk/'jmods','--add-modules','java.base,java.desktop,java.logging,java.xml,java.naming,java.scripting,java.net.http,jdk.unsupported','--strip-debug','--no-header-files','--no-man-pages','--exclude-files','**.lib','--compress','zip-6','--output',runtime],env=env)
    dll=ROOT/'native/prod/Release/mace_native.dll';digest=hashlib.sha256(dll.read_bytes()).hexdigest().upper()
    args=[jdk/'bin/jpackage.exe','--type','app-image','--name','MACE','--app-version','1.0.0','--vendor','MACE Team','--description','MACE - Telemetria de hardware y gestor de procesos','--input',stage,'--main-jar','mace-app-1.0.0-SNAPSHOT.jar','--main-class','com.mace.presentation.MaceLauncher','--dest',dist,'--runtime-image',runtime,'--java-options','--enable-native-access=ALL-UNNAMED','--java-options','-Dmace.infra.mode=native','--java-options','-Dmace.native.lib=$APPDIR\\mace_native.dll','--java-options','-Dmace.native.sha256='+digest,'--java-options','-Djava.library.path=$APPDIR','--add-launcher','MACE-Preview='+str(SOURCE/'packaging/preview-launcher.properties')]
    icon=SOURCE/'packaging/icon.ico'
    if icon.is_file():args+=['--icon',icon]
    run('candidate-jpackage',args,env=env)
    image=dist/'MACE';launcher=image/'MACE.exe';launcher.chmod(launcher.stat().st_mode|stat.S_IWRITE)
    mt=Path(r'C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64\mt.exe')
    if not mt.is_file():raise RuntimeError('INSTALLED_MT_MISSING')
    run('candidate-uac',[mt,'-nologo','-manifest',SOURCE/'packaging/app.manifest','-outputresource:'+str(launcher)+';#1'])
    run('candidate-uac-extract',[mt,'-nologo','-inputresource:'+str(launcher)+';#1','-out:'+str(EVIDENCE/'candidate-uac-private.xml')])
    if 'requireAdministrator' not in (EVIDENCE/'candidate-uac-private.xml').read_text(encoding='utf-8-sig'):raise RuntimeError('UAC_REQUIRED_MISSING')
    save('image-build-result.json',{'jpackage_invocations':1,'candidate_only':True,'uac':'requireAdministrator','unsigned_mace':True})
    print('CANDIDATE_IMAGE_BUILT reference_replaced=0 launcher_executed=0',flush=True)

if __name__=='__main__':
    try:main()
    except Exception as error:
        print('STOP reason='+str(error) if isinstance(error,RuntimeError) else 'STOP reason='+type(error).__name__,flush=True);raise SystemExit(2)
