"""One helper build, two no-build publishes for the approved property comparison.
No restore/network, tests, real helper execution, signing or service operation.
"""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

REPO=Path(__file__).resolve().parents[2]
ROOT=REPO/'native/build/candidata-20261126-02'
SOURCE=ROOT/'source'
def run(label,args,env):
    result=subprocess.run([str(x) for x in args],cwd=SOURCE/'helper',env=env,capture_output=True,text=True,encoding='utf8',errors='replace')
    (ROOT/'evidence'/(label+'-private.log')).write_text(result.stdout+'\n'+result.stderr,encoding='utf8')
    print('HELPER '+label+' exit='+str(result.returncode),flush=True)
    if result.returncode:raise RuntimeError(label+'_FAILED_STOP')
def inventory(folder):return {p.relative_to(folder).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in folder.rglob('*') if p.is_file()}

def main():
    gates=json.loads((ROOT/'evidence/gates.json').read_text())
    if gates['tree'] or gates['locked_restore']:raise RuntimeError('OFFLINE_GATES_NOT_PASSED')
    if (ROOT/'helper-publish-true').exists():raise RuntimeError('PUBLISH_ALREADY_EXISTS')
    env=os.environ.copy()
    env.update({'NUGET_PACKAGES':str(ROOT/'deps/nuget'),'DOTNET_CLI_HOME':str(ROOT/'dotnet-home'),
        'DOTNET_CLI_TELEMETRY_OPTOUT':'1','DOTNET_GENERATE_ASPNET_CERTIFICATE':'false',
        'DOTNET_ADD_GLOBAL_TOOLS_TO_PATH':'false','DOTNET_CLI_USE_MSBUILD_SERVER':'0','MSBUILDDISABLENODEREUSE':'1',
        'NUGET_CERT_REVOCATION_MODE':'offline','NUGET_SCRATCH':str(ROOT/'deps/nuget-scratch')})
    run('build-once',[shutil.which('dotnet'),'build',SOURCE/'helper/Mace.Lhm.Tests/Mace.Lhm.Tests.csproj','-c','Release','--no-restore','-p:UseSharedCompilation=false','-nr:false'],env)
    outputs=[]
    for enabled in (False,True):
        label='publish-'+str(enabled).lower();folder=ROOT/('helper-'+label)
        run(label,[shutil.which('dotnet'),'publish',SOURCE/'helper/Mace.Lhm.Helper/Mace.Lhm.Helper.csproj','-c','Release','--no-build','--no-restore','--self-contained','true','-p:DisableTransitiveFrameworkReferenceDownloads='+str(enabled).lower(),'-o',folder,'-nr:false'],env)
        rows=[{'file':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(folder.iterdir(),key=lambda p:p.name.lower()) if p.is_file()]
        (folder/'artifact-manifest.json').write_text(json.dumps({'protocol':2,'sdk':'10.0.401','runtime':'10.0.12','lhm':'0.9.6','signed':False,'files':rows},indent=2)+'\n',encoding='utf8')
        wrapper=REPO/'helper/auditoria-licencias/invocar-manifiesto-ps51.ps1'
        run(label+'-manifest',['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',wrapper,'-Source',SOURCE,'-Directory',folder,'-Action','Write'],env)
        run(label+'-verify',['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',wrapper,'-Source',SOURCE,'-Directory',folder,'-Action','Verify'],env)
        outputs.append(inventory(folder))
    differences=[name for name in sorted(set(outputs[0])|set(outputs[1])) if outputs[0].get(name)!=outputs[1].get(name)]
    result={'files_false':len(outputs[0]),'files_true':len(outputs[1]),'differences':differences,
        'identical':outputs[0]==outputs[1],'helper_builds':1,'publish_invocations':2,'reference_replaced':False}
    (ROOT/'evidence/helper-publish-comparison.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    print('PUBLISH_COMPARISON '+json.dumps(result),flush=True)
    if differences:raise RuntimeError('PUBLISH_DIFFERENCES_REQUIRE_REVIEW')

if __name__=='__main__':
    try:main()
    except Exception as error:
        print('STOP reason='+(str(error) if isinstance(error,RuntimeError) else type(error).__name__),flush=True);sys.exit(2)
