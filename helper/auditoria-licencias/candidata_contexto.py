"""Local-only campaign context. No downloads, global environment edits or kills."""
import ctypes
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import time

REPO=Path(__file__).resolve().parents[2]
ROOT=REPO/'native/build/candidata-20261126-02'
SOURCE=ROOT/'source'
EVIDENCE=ROOT/'evidence'
def save(name,value):
    (EVIDENCE/name).write_text(json.dumps(value,indent=2)+'\n',encoding='utf8')
def tools():
    spec=importlib.util.spec_from_file_location('verified',REPO/'helper/auditoria-licencias/resolver-lote-offline.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module.verify_tools()
def clean_environment():
    env=os.environ.copy()
    for key in list(env):
        if key.upper().startswith('MACE_'):del env[key]
    for key in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'):
        if env.get(key):raise RuntimeError('JAVA_OPTIONS_INHERITED')
    env['MSBUILDDISABLENODEREUSE']='1'
    env['PSModulePath']=r'C:\Windows\System32\WindowsPowerShell\v1.0\Modules'
    return env
def maven():
    jdk,mvn=tools();env=clean_environment()
    env['JAVA_HOME']=str(jdk);env['PATH']=str(jdk/'bin')+os.pathsep+env.get('PATH','')
    boot=list((mvn/'boot').glob('plexus-classworlds-*.jar'))[0]
    settings=ROOT/'deps/maven-settings.xml'
    command=[jdk/'bin/java.exe','--enable-native-access=ALL-UNNAMED','-Duser.home='+str(ROOT/'maven-home'),'-Dmaven.home='+str(mvn),'-Dclassworlds.conf='+str(mvn/'bin/m2.conf'),'-Dmaven.multiModuleProjectDirectory='+str(SOURCE/'app'),'-cp',boot,'org.codehaus.plexus.classworlds.launcher.Launcher','-o','-B','-s',settings,'-gs',settings,'-f',SOURCE/'app/pom.xml','-Dmaven.repo.local='+str(ROOT/'deps/m2')]
    return command,env,jdk
def run(label,args,cwd=SOURCE,env=None):
    path=EVIDENCE/(label+'-private.log')
    attempt=1
    while path.exists():
        attempt+=1;path=EVIDENCE/(label+'-attempt'+str(attempt)+'-private.log')
    with path.open('w',encoding='utf8') as log:
        child=subprocess.Popen([str(x) for x in args],cwd=cwd,env=env or clean_environment(),stdout=log,stderr=subprocess.STDOUT)
        code=child.wait() # Deliberately no timeout that might kill an unrecorded process.
    print('COMMAND '+label+' exit='+str(code),flush=True)
    if code:raise RuntimeError(label+'_FAILED_STOP')
    return code
def snapshot(folder):
    return {p.relative_to(folder).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in folder.rglob('*') if p.is_file()}
def preflight():
    script='''$ErrorActionPreference='Stop'
    $admin=([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    $found=@(Get-Process | Where-Object { $_.ProcessName -in @('MACE','MACE-Preview','Mace.Lhm.Helper') } | ForEach-Object { @{pid=$_.Id; image=$_.ProcessName} })
    @{administrator=$admin; existing=$found} | ConvertTo-Json -Compress -Depth 3'''
    result=subprocess.run(['powershell.exe','-NoProfile','-Command',script],capture_output=True,text=True,check=True)
    state=json.loads(result.stdout)
    if state['administrator']:raise RuntimeError('NORMAL_TOKEN_REQUIRED')
    if state['existing']:
        save('preflight-existing-processes.json',state)
        raise RuntimeError('MACE_OR_HELPER_ALREADY_RUNNING_NO_ACTION')
    print('PREFLIGHT administrator=0 existing_mace=0 existing_helper=0',flush=True)
    return state
