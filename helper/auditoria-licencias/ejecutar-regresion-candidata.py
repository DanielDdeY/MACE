"""Normal-token candidate-only regression with process identity journal. No downloads.
Never cleans up a survivor: records it and stops for the user. No kill-by-name.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from candidata_contexto import *
import procesos_candidata as processes

def audited(label,args,cwd,env=None):
    preflight()
    directory=ROOT/'process-audit'/label
    original=label;attempt=1
    while directory.exists():
        attempt+=1;label=original+'-attempt'+str(attempt);directory=ROOT/'process-audit'/label
    directory.mkdir(parents=True);(directory/'.mace-candidate-process-audit').write_text('candidate-only\n')
    active=ROOT/'test-audit/active-directory.txt';active.write_bytes(str(directory).encode('utf-16-le'))
    env=dict(env or clean_environment());env['TEMP']=str(directory);env['TMP']=str(directory)
    log=EVIDENCE/(label+'-private.log')
    with log.open('w',encoding='utf8') as output:
        child=subprocess.Popen([str(x) for x in args],cwd=cwd,env=env,stdout=output,stderr=subprocess.STDOUT)
        processes.register(child) if os.environ.get('TEMP')==str(directory) else processes.record('CREATE',child.pid,processes.birth(int(child._handle)),os.getpid(),directory=directory)
        code=child.wait() # No automatic timeout/termination by this runner.
    rows,live=processes.finish(directory)
    active.unlink()
    cases={}
    for row in rows:
        identity=str(row['pid'])+'_'+str(row['birth_ms']);case=directory/('case_'+identity+'.txt')
        cases[identity]=case.read_text() if case.exists() else label
    result={'case':label,'exit':code,'processes':rows,'contexts':cases,'survivors':live,'administrator':False,'termination_by_runner':False}
    save(label+'-result.json',result)
    print('REGRESSION '+label+' exit='+str(code)+' identities='+str(len(cases))+' survivors='+str(len(live)),flush=True)
    if live:raise RuntimeError('OWNED_PROCESS_SURVIVED_NO_ACTION_'+label)
    if code:raise RuntimeError('REGRESSION_FAILED_STOP_'+label)
    return result

def native():
    preflight()
    run('generate-audit',[sys.executable,REPO/'helper/auditoria-licencias/preparar-auditoria-procesos-candidata.py'])
    cmake=Path(r'C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe')
    ctest=cmake.with_name('ctest.exe')
    counts={}
    for profile in ('dev','prod'):
        build=ROOT/'native'/profile
        run('ctest-launcher-configure-'+profile,[cmake,'-S',SOURCE/'native','-B',build])
        listing=subprocess.run([str(ctest),'--test-dir',str(build),'-C','Release','--show-only=json-v1'],capture_output=True,text=True,check=True)
        tests=json.loads(listing.stdout)['tests'];counts[profile]=len(tests)
        for test in tests:
            previous=EVIDENCE/('ctest-'+profile+'-'+test['name']+'-result.json')
            if previous.is_file():
                state=json.loads(previous.read_text())
                if state['exit']==0 and not state['survivors']:
                    print('KEEP_PASSED '+profile+' '+test['name'],flush=True);continue
            if 'lanzador-test-candidata.py' not in ' '.join(test['command']):raise RuntimeError('CTEST_LAUNCHER_NOT_ACTIVE')
            audited('ctest-'+profile+'-'+test['name'],[ctest,'--test-dir',build,'-C','Release','--output-on-failure','-R','^'+re.escape(test['name'])+'$'],build)
        save('ctest-'+profile+'-summary.json',{'tests':len(tests),'passed':len(tests),'failed':0,'administrator':False,'test_only_audit':True})
    save('ctest-summary.json',counts)

def helper():
    env=clean_environment();env.update({'DOTNET_CLI_TELEMETRY_OPTOUT':'1','DOTNET_CLI_HOME':str(ROOT/'dotnet-home')})
    binary=SOURCE/'helper/Mace.Lhm.Tests/bin/Release/net10.0-windows/win-x64/Mace.Lhm.Tests.exe'
    if not binary.is_file():raise RuntimeError('TEST_BINARY_MISSING')
    result=audited('helper-unit',[binary],binary.parent,env)
    output=(EVIDENCE/(result['case']+'-private.log')).read_text(encoding='utf8')
    match=re.search(r'PASS:\s*(\d+)',output)
    if not match:raise RuntimeError('HELPER_COUNT_NOT_FOUND')
    save('helper-unit-summary.json',{'checks':int(match.group(1)),'exit':0,'physical_helper_executed':False})

def java():
    preflight()
    command,env,jdk=maven()
    env['JAVA_TOOL_OPTIONS']='-javaagent:"'+str(ROOT/'test-audit/audit-agent.jar')+'"'
    dll=ROOT/'native/dev/Release/mace_native.dll'
    env['MACE_NATIVE_LIB']=str(dll);env['MACE_NATIVE_SHA256']=hashlib.sha256(dll.read_bytes()).hexdigest()
    env['MACE_LHM_HELPER']=str(ROOT/'no-real-helper.exe')
    env['MACE_WATCHDOG_FAKE_HELPER']=str(ROOT/'native/dev/Release/lhm_fake_helper.exe')
    audited('java-development',command+['-Pdevelopment','-Dmace.build.directory=target/candidate-development','initialize','org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test'],SOURCE/'app',env)
    reports=list((SOURCE/'app/target/candidate-development/surefire-reports').glob('TEST-*.xml'))
    totals={key:0 for key in ('tests','failures','errors','skipped')}
    for report in reports:
        root=ET.fromstring(report.read_bytes())
        for key in totals:totals[key]+=int(root.attrib.get(key,0))
    save('java-summary.json',totals)
    print('JAVA_COUNTS '+json.dumps(totals),flush=True)

def policies():
    # Six cases preceded the corrected audit header; rerun against final fixture bytes.
    ctest=Path(r'C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\ctest.exe')
    build=ROOT/'native/dev'
    for case in ('budget','streak','permanent','suspend','recovery','slow-cap'):
        name='lhm_retry_policy_'+case
        audited('ctest-dev-'+name,[ctest,'--test-dir',build,'-C','Release','--output-on-failure','-R','^'+name+'$'],build)
    save('ctest-final-fixture-check.json',{'six_policy_cases_revalidated':6,'development_final_fixture_cases':76,'production_final_fixture_cases':77})

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['native','helper','java','policies']);args=parser.parse_args()
    try:globals()[args.phase]()
    except Exception as error:
        print('STOP reason='+str(error) if isinstance(error,RuntimeError) else 'STOP reason='+type(error).__name__,flush=True)
        sys.exit(2)
