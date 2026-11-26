"""Read-only content checks plus existing candidate scratch/payload tests. No network."""
import csv
import importlib.util
import zipfile
from candidata_contexto import *

def main():
    image=SOURCE/'packaging/dist/MACE'
    before=snapshot(image)
    for label,relative,args in [
        ('verify-candidate-image','packaging/verify-image.ps1',['-ImageDirectory',image,'-ReportPath',EVIDENCE/'candidate-image-verification.json']),
        ('test-candidate-image','packaging/tests/test-image.ps1',['-ImageDirectory',image]),
        ('candidate-protected','native/scripts/check-protected-telemetry.ps1',[]),
        ('candidate-retired','native/scripts/audit-retired-providers.ps1',['-ReportPath',EVIDENCE/'retired-private.tsv'])]:
        run(label,['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',SOURCE/relative]+args)
    spec=importlib.util.spec_from_file_location('candidate_regression',REPO/'helper/auditoria-licencias/ejecutar-regresion-candidata.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    _,env,jdk=maven()
    env['JAVA_TOOL_OPTIONS']='-javaagent:"'+str(ROOT/'test-audit/audit-agent.jar')+'"'
    module.audited('candidate-payload',['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',REPO/'helper/auditoria-licencias/empaquetado-offline-candidata.ps1','-Action','Payload','-Source',SOURCE,'-Root',ROOT,'-JavaExe',jdk/'bin/java.exe','-PawnIoInstaller',REPO/'packaging/dist/MACE/app/prerequisites/PawnIO_setup.exe'],SOURCE,env)
    if before!=snapshot(image):raise RuntimeError('IMAGE_CHANGED_DURING_TESTS')
    lot=REPO/'native/build/fase3-lote-cerrado-20261126-01'
    records=json.loads((lot/'evidence/downloads-private.json').read_text())['artifacts']
    jar_records=[r for r in records if r['file'].endswith('.jar')]
    if len(jar_records)!=192:raise RuntimeError('BATCH_JAR_COUNT_CHANGED')
    approved={(Path(r['file']).name,r['sha256_private']):r for r in jar_records}
    runtime_names={Path(r['file']).name for r in jar_records if ('javafx-' in Path(r['file']).name and '27.0.1.1' in Path(r['file']).name) or Path(r['file']).name=='atlantafx-base-2.0.1.jar'}
    actual=[];copies={};jarrows=[]
    for jar in image.rglob('*.jar'):
        digest=hashlib.sha256(jar.read_bytes()).hexdigest()
        known=(jar.name,digest) in approved
        if jar.name!='mace-app-1.0.0-SNAPSHOT.jar' and (not known or jar.name not in runtime_names):raise RuntimeError('UNEXPECTED_JAR_IN_IMAGE')
        if known:actual.append(jar.name)
        with zipfile.ZipFile(jar) as archive:
            classes={n for n in archive.namelist() if n.endswith('.class')}
            if jar.name.startswith('javafx-'):copies[jar.name]=classes
            if jar.name.startswith('mace-app-'):
                if archive.read('com/mace/infrastructure/nativebridge/native-profile.properties').decode().strip()!='profile=production':raise RuntimeError('PACKAGED_JAVA_NOT_PRODUCTION')
                if any(n.startswith('com/mace/testaudit/') for n in archive.namelist()):raise RuntimeError('TEST_AUDIT_PACKAGED')
        jarrows.append({'file':jar.relative_to(image).as_posix(),'approved_runtime':known,'classes':len(classes)})
    if sorted(actual)!=sorted(runtime_names) or len(actual)!=9:raise RuntimeError('RUNTIME_JAR_SET_MISMATCH')
    javafx=[]
    for module in ('base','graphics','controls','fxml'):
        plain=f'javafx-{module}-27.0.1.1.jar';win=f'javafx-{module}-27.0.1.1-win.jar'
        duplicate=copies[plain]&copies[win]
        javafx.append({'module':module,'plain_classes':len(copies[plain]),'win_classes':len(copies[win]),'duplicate_classes':len(duplicate)})
        if duplicate:raise RuntimeError('DUPLICATED_JAVAFX_CLASSES')
    mono=[name for name in before if 'monoposix' in name.lower() or 'mono.posix' in name.lower()]
    if mono:raise RuntimeError('MONO_ASSET_IN_IMAGE')
    actual_helper=(ROOT/'helper-publish-true/artifact-manifest.json')
    published=json.loads(actual_helper.read_text())['files']
    runtime=(ROOT/'helper-publish-true/helper-runtime.manifest').read_text().splitlines()[1:]
    appmanifest=json.loads((image/'app/runtime-manifest.json').read_text(encoding='utf-8-sig'))
    expected=json.loads((EVIDENCE/'reference-before-private.json').read_text())
    if expected!=snapshot(REPO/'packaging/dist/MACE'):raise RuntimeError('REFERENCE_CHANGED')
    pawn=image/'app/prerequisites/PawnIO_setup.exe'
    if hashlib.sha256(pawn.read_bytes()).hexdigest()!=expected['app/prerequisites/PawnIO_setup.exe']:raise RuntimeError('PAWNIO_CHANGED')
    if (image/'app/third-party/MACE-LICENSE.txt').read_bytes()!=(SOURCE/'LICENSE').read_bytes():raise RuntimeError('OWN_LICENSE_CHANGED')
    dll=(image/'app/mace_native.dll').read_bytes()
    if b'MACE_LHM_PROFILE=PRODUCTION' not in dll or b'MACE_LHM_TEST_HOOKS' in dll or b'MACE_LHM_PROFILE=DEVELOPMENT' in dll:raise RuntimeError('NATIVE_PRODUCTION_PROFILE_MISMATCH')
    modules=run('candidate-runtime-modules',[image/'runtime/bin/java.exe','--list-modules'])
    lines=(EVIDENCE/'candidate-runtime-modules-private.log').read_text().splitlines()
    retired=(EVIDENCE/'retired-private.tsv').read_text(encoding='utf-8-sig').splitlines()
    result={'image_files':len(before),'image_bytes':sum(p.stat().st_size for p in image.rglob('*') if p.is_file()),'reference_files':len(expected),'published_entries':len(published),'published_files_total':len(list((ROOT/'helper-publish-true').glob('*'))),'helper_runtime_entries':len(runtime),'app_runtime_entries':len(appmanifest['files']),'image_jars':jarrows,'approved_batch_jars':192,'approved_runtime_jars_present':len(actual),'excluded_test_plugin_jars':192-len(actual),'javafx':javafx,'mono_files':len(mono),'jdk_modules':len(lines),'retired_accepted_rows':len(retired)-1,'reference_unchanged':True,'pawnio_bytes_unchanged':True,'own_license_identical':True}
    save('candidate-image-inventory-private.json',before)
    save('candidate-verification-result.json',result)
    print('IMAGE_COUNTS '+json.dumps({k:v for k,v in result.items() if not isinstance(v,list)}),flush=True)

if __name__=='__main__':
    try:main()
    except Exception as error:
        print('STOP reason='+str(error) if isinstance(error,RuntimeError) else 'STOP reason='+type(error).__name__,flush=True);raise SystemExit(2)
