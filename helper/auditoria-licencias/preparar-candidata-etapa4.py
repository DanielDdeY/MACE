"""Create isolated approved source/cache and run ONLY the two offline gates.
No network, build, publish, test, process termination or product replacement.
"""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

REPO=Path(__file__).resolve().parents[2]
ROOT=REPO/'native/build/candidata-20261126-02'
SOURCE=ROOT/'source'
OLD=REPO/'native/build/fase3-lote-cerrado-20261126-01'

def write(name,body):
    (ROOT/'evidence'/name).write_text(json.dumps(body,indent=2)+'\n',encoding='utf8')
def command(label,args,cwd,env=None):
    result=subprocess.run([str(x) for x in args],cwd=cwd,env=env,capture_output=True,text=True,encoding='utf8',errors='replace')
    (ROOT/'evidence'/(label+'-private.log')).write_text(result.stdout+'\n'+result.stderr,encoding='utf8')
    print('GATE '+label+' exit='+str(result.returncode),flush=True)
    if result.returncode:raise RuntimeError(label+'_FAILED_STOP')
    return result
def snapshot():
    image=REPO/'packaging/dist/MACE'
    return {p.relative_to(image).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in image.rglob('*') if p.is_file()}

def main():
    if ROOT.exists():raise RuntimeError('CANDIDATE_ROOT_ALREADY_EXISTS')
    ROOT.mkdir();(ROOT/'evidence').mkdir()
    before={k:os.environ.get(k) for k in ('JAVA_HOME','PATH')};write('reference-before-private.json',snapshot())
    command('clone',['git','-c','core.autocrlf=false','clone','--no-local','--no-hardlinks',REPO,SOURCE],REPO)
    command('branch',['git','switch','-c','candidate/etapa4-excluir-mono'],SOURCE)
    for name in ('MONO-EXPERIMENTO-COMPLETO.patch','CANDIDATA-CONSERVAR-MONO.patch','DISABLE-TRANSITIVE-FRAMEWORK-DOWNLOADS.patch'):
        patch=REPO/'helper/pendientes'/name
        command('check-'+name,['git','apply','--check','--unidiff-zero',patch],SOURCE)
        command('apply-'+name,['git','apply','--unidiff-zero',patch],SOURCE)
    # CONSERVAR patch supplies ONLY common MIT/Java edits; Mono exclusion is applied separately.
    for p in [SOURCE/'app/pom.xml']+list((SOURCE/'helper').glob('*/*.csproj'))+[SOURCE/'helper/Directory.Build.props']:
        ET.fromstring(p.read_bytes())
    for p in (SOURCE/'helper').rglob('*.props'):
        if 'NuGetAudit' in p.read_text(encoding='utf-8-sig'):raise RuntimeError('AUDIT_PROPERTY_IN_PRODUCT')
    files=['app/pom.xml','helper/Mace.Lhm.Helper/Mace.Lhm.Helper.csproj','helper/Mace.Lhm.Protocol/Mace.Lhm.Protocol.csproj','helper/Mace.Lhm.Tests/Mace.Lhm.Tests.csproj','helper/Mace.Lhm.Helper/packages.lock.json','helper/Mace.Lhm.Tests/packages.lock.json','helper/Directory.Build.props','packaging/stage-runtime.ps1']
    command('candidate-add',['git','add','--']+files,SOURCE)
    command('candidate-diff',['git','diff','--cached','--check'],SOURCE)
    command('candidate-commit',['git','commit','-m','candidate: MIT Java25 JavaFX27 y exclusion Mono'],SOURCE)
    head=command('candidate-head',['git','rev-parse','HEAD'],SOURCE).stdout.strip()
    (ROOT/'deps').mkdir();shutil.copytree(OLD/'deps/m2',ROOT/'deps/m2')
    module_spec=importlib.util.spec_from_file_location('offline',REPO/'helper/auditoria-licencias/resolver-lote-offline.py')
    module=importlib.util.module_from_spec(module_spec);module_spec.loader.exec_module(module)
    jdk,maven=module.verify_tools()
    settings=ROOT/'deps/maven-settings.xml'
    settings.write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"><mirrors><mirror><id>central-only</id><mirrorOf>*</mirrorOf><url>https://repo.maven.apache.org/maven2</url></mirror></mirrors></settings>',encoding='utf8')
    env=module.environment();env['JAVA_HOME']=str(jdk);env['PATH']=str(jdk/'bin')+os.pathsep+env.get('PATH','')
    boot=list((maven/'boot').glob('plexus-classworlds-*.jar'))[0]
    mvn=[jdk/'bin/java.exe','--enable-native-access=ALL-UNNAMED','-Duser.home='+str(ROOT/'maven-home'),'-Dmaven.home='+str(maven),'-Dclassworlds.conf='+str(maven/'bin/m2.conf'),'-Dmaven.multiModuleProjectDirectory='+str(SOURCE/'app'),'-cp',boot,'org.codehaus.plexus.classworlds.launcher.Launcher','-o','-B','-s',settings,'-gs',settings,'-f',SOURCE/'app/pom.xml','-Dmaven.repo.local='+str(ROOT/'deps/m2')]
    command('patched-pom-tree',mvn+['org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree','-DoutputType=tgf','-DoutputFile='+str(ROOT/'evidence/patched-tree-private.tgf')],SOURCE/'app',env)
    config=ET.Element('configuration');sources=ET.SubElement(config,'packageSources');ET.SubElement(sources,'clear')
    ET.SubElement(sources,'add',{'key':'approved-local-feed','value':str(OLD/'deps/nupkg-feed')})
    ET.ElementTree(config).write(ROOT/'NuGet.Config',encoding='utf-8',xml_declaration=True)
    envnu=os.environ.copy();envnu.update({'NUGET_PACKAGES':str(ROOT/'deps/nuget'),'DOTNET_CLI_HOME':str(ROOT/'dotnet-home'),'NUGET_HTTP_CACHE_PATH':str(ROOT/'deps/nuget-http'),'NUGET_PLUGINS_CACHE_PATH':str(ROOT/'deps/nuget-plugins'),'NUGET_SCRATCH':str(ROOT/'deps/nuget-scratch'),'NUGET_CERT_REVOCATION_MODE':'offline','DOTNET_CLI_TELEMETRY_OPTOUT':'1','DOTNET_GENERATE_ASPNET_CERTIFICATE':'false','DOTNET_ADD_GLOBAL_TOOLS_TO_PATH':'false','DOTNET_CLI_WORKLOAD_UPDATE_NOTIFY_DISABLE':'true','DOTNET_CLI_USE_MSBUILD_SERVER':'0','MSBUILDDISABLENODEREUSE':'1'})
    command('patched-locked-restore',[shutil.which('dotnet'),'restore',SOURCE/'helper/Mace.Lhm.Tests/Mace.Lhm.Tests.csproj','--locked-mode','--no-http-cache','--disable-parallel','--configfile',ROOT/'NuGet.Config','--packages',ROOT/'deps/nuget','-p:NuGetAudit=false','-p:RestoreIgnoreFailedSources=false','-nr:false'],SOURCE/'helper',envnu)
    if any(os.environ.get(k)!=v for k,v in before.items()):raise RuntimeError('HOST_ENV_CHANGED')
    unchanged=json.loads((ROOT/'evidence/reference-before-private.json').read_text())==snapshot()
    write('gates.json',{'candidate_commit':head,'tree':0,'locked_restore':0,'reference_unchanged':unchanged,'build_started':False})
    print('OFFLINE_GATES_PASS tree=0 locked_restore=0 reference_unchanged='+str(int(unchanged)),flush=True)

if __name__=='__main__':
    try:main()
    except Exception as error:
        print('STOP reason='+(str(error) if isinstance(error,RuntimeError) else type(error).__name__),flush=True);sys.exit(2)
