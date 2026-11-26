"""Read final local evidence after offline stop; no builds/tests/downloads or kills.
Detailed identities/digests remain ignored and unversioned under native/build.
"""
import csv
from datetime import datetime,timezone
import re
import zipfile
from candidata_contexto import *
import procesos_candidata as processes

results=[];allrows=[]
for path in sorted(EVIDENCE.glob('*-result.json')):
    item=json.loads(path.read_text())
    if 'processes' not in item:continue
    results.append(item);allrows+=item['processes']
identities={(r['pid'],r['birth_ms']) for r in allrows}
# A historical PID can now belong to a protected unrelated process. Do not infer
# survival from OpenProcess access denied, nor touch that process. CIM provides a
# read-only second view of current creation dates; unresolved dates remain NV.
script='''$ErrorActionPreference='Stop'; $rows=@(Get-CimInstance Win32_Process | ForEach-Object { @{pid=[int]$_.ProcessId; birth_ms=if($_.CreationDate){([DateTimeOffset]$_.CreationDate).ToUnixTimeMilliseconds()}else{$null}} }); ConvertTo-Json -InputObject $rows -Compress'''
query=subprocess.run(['powershell.exe','-NoProfile','-Command',script],capture_output=True,text=True,check=True)
current={row['pid']:row['birth_ms'] for row in json.loads(query.stdout)}
unknown=[{'pid':pid,'birth_ms':created} for pid,created in identities if pid in current and current[pid] is None]
alive=[{'pid':pid,'birth_ms':created} for pid,created in identities if current.get(pid)==created]
save('final-process-check.json',{'unique_identities':len(identities),'survivors':alive,'unknown_current_identity':unknown,'processes_terminated_by_runner':0,'per_case_survivor_checks_all_zero':all(not r['survivors'] for r in results)})
with (EVIDENCE/'procesos-saneado.csv').open('w',encoding='utf8',newline='') as file:
    writer=csv.writer(file);writer.writerow(['caso','pid','creacion_utc','parent_pid','eventos','peticion_terminacion','vivo_al_cierre','salida_caso'])
    for item in results:
        case_identities=sorted({(row['pid'],row['birth_ms']) for row in item['processes']})
        for pid,created in case_identities:
            rows=[r for r in item['processes'] if r['pid']==pid and r['birth_ms']==created]
            events=sorted({r['event'] for r in rows});parent=next((r['parent'] for r in rows if r['parent']),0)
            writer.writerow([item['case'],pid,datetime.fromtimestamp(created/1000,timezone.utc).isoformat(),parent,','.join(events),'KILL_REQUEST' in events,any(r['pid']==pid and r['birth_ms']==created for r in alive),item['exit']])
snapshot_reference=snapshot(REPO/'packaging/dist/MACE')
expected=json.loads((EVIDENCE/'reference-before-private.json').read_text())
if snapshot_reference!=expected:raise RuntimeError('REFERENCE_CHANGED')
published=ROOT/'helper-publish-true'
manifest=json.loads((published/'artifact-manifest.json').read_text())
runtime=(published/'helper-runtime.manifest').read_text().splitlines()[1:]
mono=[p.name for p in published.iterdir() if 'mono.posix' in p.name.lower() or 'monoposix' in p.name.lower()]
log=(EVIDENCE/'java-development-private.log').read_text()
warnings=sorted(set(re.findall(r'The POM for ([^ ]+) is missing',log)))
save('offline-stop-missing-poms.json',warnings)
prodproject=(ROOT/'native/prod/mace_native.vcxproj').read_text(encoding='utf-8-sig')
result={'reference_files':len(expected),'reference_bytes':sum(p.stat().st_size for p in (REPO/'packaging/dist/MACE').rglob('*') if p.is_file()),'reference_unchanged':True,
 'helper_published_entries':len(manifest['files']),'helper_directory_files':sum(p.is_file() for p in published.iterdir()),'helper_runtime_entries':len(runtime),'helper_published_mono_files':mono,
 'native_test_injection_in_product_project':any(x in prodproject for x in ('candidate_process_audit','MACE_AUDIT_WRAP','MACE_LHM_TEST_HOOKS')),
 'process_identities':len(identities),'explicit_termination_identities':len({(r['pid'],r['birth_ms']) for r in allrows if r['event']=='KILL_REQUEST'}),'process_survivors':len(alive),'process_identity_unknown':len(unknown),
 'java_tests_executed':0,'java_resolution_exit':1,'java_missing_pom_warnings':len(warnings),'required_missing_jar':'commons-io:commons-io:2.15.1',
 'candidate_image_exists':(SOURCE/'packaging/dist/MACE').exists(),'jpackage_invocations':0,'new_downloads':0}
save('stage4-stop-summary.json',result)
print('FINAL_EVIDENCE '+json.dumps(result),flush=True)
if alive:raise RuntimeError('OWNED_PROCESS_SURVIVED_NO_ACTION')
