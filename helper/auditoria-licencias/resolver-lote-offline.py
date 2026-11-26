"""Resolve only; no product patches, source build, package, test or online feed.

Maven uses a dedicated resolution-only POM, not an edited application POM.
NuGet restores copies of unmodified project/lock files, never the candidate.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET

REPO=Path(__file__).resolve().parents[2]
ROOT=REPO/'native/build/fase3-lote-cerrado-20261126-01'
TOOLS=REPO/'native/build/fase3-etapa3-20261125-01'

class Stop(Exception):pass

def environment():
    env=os.environ.copy()
    if env.get('JAVA_TOOL_OPTIONS') or env.get('JDK_JAVA_OPTIONS') or env.get('_JAVA_OPTIONS'):
        raise Stop('INHERITED_JAVA_OPTIONS')
    return env

def verify_tools():
    records=json.loads((TOOLS/'evidence/downloads-private.json').read_text())['artifacts']
    for record in records:
        if not record['file'].endswith('.zip'):continue
        path=TOOLS/record['file']
        if hashlib.new(record['algorithm'],path.read_bytes()).hexdigest()!=record['digest']:raise Stop('TOOL_ARCHIVE_CHANGED')
        folder=TOOLS/('tools/temurin' if 'OpenJDK' in path.name else 'tools/maven')
        with zipfile.ZipFile(path) as archive:
            for entry in archive.infolist():
                if entry.is_dir():continue
                local=folder/entry.filename
                if not local.is_file() or hashlib.sha256(local.read_bytes()).digest()!=hashlib.sha256(archive.read(entry)).digest():raise Stop('EXTRACTED_TOOL_CHANGED')
    jdks=[p.parent.parent for p in (TOOLS/'tools/temurin').rglob('bin/java.exe')]
    if len(jdks)!=1:raise Stop('JDK_NOT_UNIQUE')
    return jdks[0],TOOLS/'tools/maven/apache-maven-3.9.16'

def maven():
    state=json.loads((ROOT/'evidence/downloads-private.json').read_text())
    if state['stopped'] or len(state['artifacts'])!=220:raise Stop('BATCH_NOT_VERIFIED')
    jdk,mvn=verify_tools()
    work=ROOT/'resolution-only';work.mkdir(exist_ok=True)
    settings=work/'settings.xml'
    settings.write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"><mirrors><mirror><id>central-only</id><mirrorOf>*</mirrorOf><url>https://repo.maven.apache.org/maven2</url></mirror></mirrors></settings>',encoding='utf8')
    # Isolated metadata manifest for the approved future dependency set, not a product patch.
    pom='''<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>com.mace.audit</groupId><artifactId>resolution-only</artifactId><version>1.0</version><packaging>pom</packaging>
<dependencyManagement><dependencies><dependency><groupId>org.junit</groupId><artifactId>junit-bom</artifactId><version>5.10.2</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
<dependencies>
<dependency><groupId>org.openjfx</groupId><artifactId>javafx-controls</artifactId><version>27.0.1.1</version></dependency>
<dependency><groupId>org.openjfx</groupId><artifactId>javafx-fxml</artifactId><version>27.0.1.1</version></dependency>
<dependency><groupId>io.github.mkpaz</groupId><artifactId>atlantafx-base</artifactId><version>2.0.1</version></dependency>
<dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.mockito</groupId><artifactId>mockito-core</artifactId><version>5.20.0</version><scope>test</scope></dependency>
<dependency><groupId>org.mockito</groupId><artifactId>mockito-junit-jupiter</artifactId><version>5.20.0</version><scope>test</scope></dependency>
</dependencies></project>'''
    (work/'pom.xml').write_text(pom,encoding='utf8')
    env=environment();env['JAVA_HOME']=str(jdk);env['PATH']=str(jdk/'bin')+os.pathsep+env.get('PATH','')
    boot=list((mvn/'boot').glob('plexus-classworlds-*.jar'))
    if len(boot)!=1:raise Stop('MAVEN_BOOT_NOT_UNIQUE')
    command=[str(jdk/'bin/java.exe'),'-Duser.home='+str(ROOT/'maven-home'),'-Dmaven.home='+str(mvn),'-Dclassworlds.conf='+str(mvn/'bin/m2.conf'),'-Dmaven.multiModuleProjectDirectory='+str(work),'-cp',str(boot[0]),'org.codehaus.plexus.classworlds.launcher.Launcher','-o','-B','-s',str(settings),'-gs',str(settings),'-f',str(work/'pom.xml'),'-Dmaven.repo.local='+str(ROOT/'deps/m2')]
    results=[]
    for goal,options in [('tree',['-DoutputType=tgf','-DoutputFile='+str(ROOT/'evidence/dependency-tree-private.tgf')]),('resolve',[])]:
        completed=subprocess.run(command+['org.apache.maven.plugins:maven-dependency-plugin:3.8.1:'+goal]+options,cwd=work,env=env,capture_output=True,text=True,encoding='utf8',errors='replace')
        (ROOT/'evidence'/('maven-'+goal+'-private.log')).write_text(completed.stdout+'\n'+completed.stderr,encoding='utf8')
        results.append({'goal':goal,'exit':completed.returncode})
        (ROOT/'evidence/maven-result.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf8')
        print('MAVEN_OFFLINE goal='+goal+' exit='+str(completed.returncode),flush=True)
        if completed.returncode:raise Stop('MAVEN_OFFLINE_CACHE_MISS_OR_RESOLUTION_FAILURE')

def nuget():
    prior=json.loads((ROOT/'evidence/maven-result.json').read_text())
    if len(prior)!=2 or any(x['exit'] for x in prior):raise Stop('MAVEN_NOT_PASSED')
    work=ROOT/'restore-only/helper';work.mkdir(parents=True,exist_ok=True)
    for name in ('Directory.Build.props','global.json'):
        shutil.copyfile(REPO/'helper'/name,work/name)
    for name in ('Mace.Lhm.Helper','Mace.Lhm.Protocol','Mace.Lhm.Tests'):
        dest=work/name;dest.mkdir(exist_ok=True)
        for file in [REPO/'helper'/name/(name+'.csproj'),REPO/'helper'/name/'packages.lock.json']:
            if file.exists():shutil.copyfile(file,dest/file.name)
    # Clear both package and audit sources. No remote endpoint is configured.
    config=ROOT/'restore-only/NuGet.Config'
    root=ET.Element('configuration');sources=ET.SubElement(root,'packageSources');ET.SubElement(sources,'clear')
    ET.SubElement(sources,'add',{'key':'approved-local-feed','value':str(ROOT/'deps/nupkg-feed')})
    audit=ET.SubElement(root,'auditSources');ET.SubElement(audit,'clear')
    ET.SubElement(audit,'add',{'key':'approved-local-feed','value':str(ROOT/'deps/nupkg-feed')})
    ET.ElementTree(root).write(config,encoding='utf-8',xml_declaration=True)
    env=os.environ.copy();env.update({'NUGET_PACKAGES':str(ROOT/'deps/nuget-restored'),'DOTNET_CLI_HOME':str(ROOT/'dotnet-home'),'DOTNET_CLI_TELEMETRY_OPTOUT':'1','DOTNET_SKIP_FIRST_TIME_EXPERIENCE':'1','DOTNET_CLI_WORKLOAD_UPDATE_NOTIFY_DISABLE':'true','NUGET_CERT_REVOCATION_MODE':'offline','NUGET_HTTP_CACHE_PATH':str(ROOT/'deps/nuget-http'),'NUGET_PLUGINS_CACHE_PATH':str(ROOT/'deps/nuget-plugins'),'NUGET_SCRATCH':str(ROOT/'deps/nuget-scratch'),'DOTNET_GENERATE_ASPNET_CERTIFICATE':'false','DOTNET_ADD_GLOBAL_TOOLS_TO_PATH':'false','DOTNET_CLI_USE_MSBUILD_SERVER':'0','MSBUILDDISABLENODEREUSE':'1'})
    command=[shutil.which('dotnet'),'restore',str(work/'Mace.Lhm.Tests/Mace.Lhm.Tests.csproj'),'--locked-mode','--no-http-cache','--disable-parallel','--configfile',str(config),'--packages',str(ROOT/'deps/nuget-restored'),'-p:RestoreIgnoreFailedSources=false','-nr:false']
    completed=subprocess.run(command,cwd=work,env=env,capture_output=True,text=True,encoding='utf8',errors='replace')
    (ROOT/'evidence/nuget-restore-private.log').write_text(completed.stdout+'\n'+completed.stderr,encoding='utf8')
    unchanged=all((work/name/'packages.lock.json').read_bytes()==(REPO/'helper'/name/'packages.lock.json').read_bytes() for name in ('Mace.Lhm.Helper','Mace.Lhm.Protocol','Mace.Lhm.Tests'))
    (ROOT/'evidence/nuget-result.json').write_text(json.dumps({'exit':completed.returncode,'locks_unchanged':unchanged,'remote_sources':0})+'\n',encoding='utf8')
    print('NUGET_OFFLINE exit='+str(completed.returncode)+' locks_unchanged='+str(int(unchanged)),flush=True)
    if completed.returncode or not unchanged:raise Stop('NUGET_OFFLINE_RESTORE_FAILED')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('phase',choices=('maven','nuget'));args=parser.parse_args()
    before={k:os.environ.get(k) for k in ('JAVA_HOME','PATH')}
    try:
        globals()[args.phase]()
        if any(os.environ.get(k)!=v for k,v in before.items()):raise Stop('PARENT_ENVIRONMENT_CHANGED')
    except Exception as error:
        print('STOP reason='+(str(error) if isinstance(error,Stop) else type(error).__name__),flush=True)
        sys.exit(2)
