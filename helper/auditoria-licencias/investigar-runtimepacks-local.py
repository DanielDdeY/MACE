"""Local-only control/variant restores and SDK item comparison. Never builds.

Copies original project files; passes properties on the command line only.
Keeps raw logs, SDK JSON and full digests in ignored isolated evidence.
"""
import hashlib
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

REPO=Path(__file__).resolve().parents[2]
ROOT=REPO/'native/build/fase3-restore-propiedad-20261126-02'
FEED=REPO/'native/build/fase3-lote-cerrado-20261126-01/deps/nupkg-feed'
PROJECTS=('Mace.Lhm.Helper','Mace.Lhm.Protocol','Mace.Lhm.Tests')

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(name,value):
    (ROOT/'evidence'/name).write_text(json.dumps(value,indent=2)+'\n',encoding='utf8')
def published():
    directory=REPO/'packaging/dist/MACE'
    return {p.relative_to(directory).as_posix():sha(p) for p in directory.rglob('*') if p.is_file()}
def env():
    result=os.environ.copy()
    result.update({'NUGET_PACKAGES':str(ROOT/'packages'),'DOTNET_CLI_HOME':str(ROOT/'dotnet-home'),
        'NUGET_HTTP_CACHE_PATH':str(ROOT/'nuget-http'),'NUGET_PLUGINS_CACHE_PATH':str(ROOT/'nuget-plugins'),
        'NUGET_SCRATCH':str(ROOT/'nuget-scratch'),'NUGET_CERT_REVOCATION_MODE':'offline',
        'DOTNET_CLI_TELEMETRY_OPTOUT':'1','DOTNET_GENERATE_ASPNET_CERTIFICATE':'false',
        'DOTNET_ADD_GLOBAL_TOOLS_TO_PATH':'false','DOTNET_SKIP_FIRST_TIME_EXPERIENCE':'1',
        'DOTNET_CLI_WORKLOAD_UPDATE_NOTIFY_DISABLE':'true','DOTNET_CLI_USE_MSBUILD_SERVER':'0',
        'MSBUILDDISABLENODEREUSE':'1'})
    return result
def run(label,args):
    completed=subprocess.run([shutil.which('dotnet')]+args,cwd=ROOT/'helper',env=env(),capture_output=True,text=True,encoding='utf8',errors='replace')
    (ROOT/'evidence'/(label+'-private.log')).write_text(completed.stdout+'\n'+completed.stderr,encoding='utf8')
    return completed
def assets(label):
    result={}
    for name in PROJECTS:
        file=ROOT/'helper'/name/'obj/project.assets.json'
        if file.exists():
            body=json.loads(file.read_text(encoding='utf-8-sig'))
            write(label+'-'+name+'-assets-private.json',body)
            result[name]=body
    return result
def restore(label,disabled):
    result=run(label,['restore',str(ROOT/'helper/Mace.Lhm.Tests/Mace.Lhm.Tests.csproj'),
        '--locked-mode','--no-http-cache','--disable-parallel','--configfile',str(ROOT/'NuGet.Config'),
        '--packages',str(ROOT/'packages'),'-p:RestoreIgnoreFailedSources=false','-p:NuGetAudit=false',
        '-p:DisableTransitiveFrameworkReferenceDownloads='+str(disabled).lower(),'-nr:false'])
    errors=sorted(set(__import__('re').findall(r'\b(?:NU|NETSDK|MSB)\d{4}\b',result.stdout+result.stderr)))
    print('RESTORE '+label+' exit='+str(result.returncode)+' codes='+','.join(errors),flush=True)
    return result.returncode,errors,assets(label)
def evaluate(label,disabled,target,items):
    result=run(label,['msbuild',str(ROOT/'helper/Mace.Lhm.Helper/Mace.Lhm.Helper.csproj'),
        '-target:'+target,'-getItem:'+','.join(items),'-getProperty:DisableTransitiveFrameworkReferenceDownloads,SelfContained,RuntimeFrameworkVersion',
        '-p:NuGetAudit=false','-p:DisableTransitiveFrameworkReferenceDownloads='+str(disabled).lower(),'-nr:false'])
    if result.returncode:
        print('SDK_ITEMS '+label+' exit='+str(result.returncode),flush=True)
        return {'exit':result.returncode}
    body=json.loads(result.stdout[result.stdout.index('{'):])
    write(label+'-private.json',body)
    return body
def runtime_files(model):
    rows=[]
    for item in model.get('Items',{}).get('RuntimePackAsset',[]):
        file=Path(item['Identity'])
        rows.append({'package_file':file.relative_to(ROOT/'packages').as_posix(),
            'destination':item.get('DestinationSubPath',''),'asset_type':item.get('AssetType',''),
            'sha256_private':sha(file)})
    return sorted(rows,key=lambda x:(x['package_file'],x['destination']))

def main():
    if ROOT.exists():raise RuntimeError('EXPERIMENT_ROOT_ALREADY_EXISTS')
    ROOT.mkdir();(ROOT/'evidence').mkdir();(ROOT/'helper').mkdir()
    before_image=published();write('published-before-private.json',before_image)
    before_sources={}
    for name in ('Directory.Build.props','global.json'):
        shutil.copyfile(REPO/'helper'/name,ROOT/'helper'/name)
    for name in PROJECTS:
        destination=ROOT/'helper'/name;destination.mkdir()
        for filename in (name+'.csproj','packages.lock.json'):
            source=REPO/'helper'/name/filename
            before_sources[name+'/'+filename]=sha(source)
            shutil.copyfile(source,destination/filename)
    config=ET.Element('configuration');packages=ET.SubElement(config,'packageSources');ET.SubElement(packages,'clear')
    ET.SubElement(packages,'add',{'key':'approved-local-feed','value':str(FEED)})
    audits=ET.SubElement(config,'auditSources');ET.SubElement(audits,'clear')
    ET.SubElement(audits,'add',{'key':'approved-local-feed','value':str(FEED)})
    ET.ElementTree(config).write(ROOT/'NuGet.Config',encoding='utf-8',xml_declaration=True)
    baseline_code,baseline_errors,baseline=restore('control-false',False)
    variant_code,variant_errors,variant=restore('variant-true',True)
    summary={'baseline_exit':baseline_code,'baseline_codes':baseline_errors,'variant_exit':variant_code,
        'variant_codes':variant_errors,'NuGetAudit':False,'sources_remote':0,'compiled':False,'published':False}
    write('result.json',summary)
    if variant_code:raise RuntimeError('VARIANT_RESTORE_FAILED_STOP')
    equal={}
    for name in PROJECTS:
        old,new=baseline[name],variant[name]
        equal[name]={'targets_equal':old['targets']==new['targets'],'libraries_equal':old['libraries']==new['libraries'],
            'frameworks_old':old['project']['frameworks'],'frameworks_new':new['project']['frameworks']}
    write('asset-comparison-private.json',equal)
    item_sets={}
    for disabled in (False,True):
        label='sdk-'+str(disabled).lower()
        item_sets[label]=evaluate(label,disabled,'ProcessFrameworkReferences',['FrameworkReference','RuntimeFramework','RuntimePack','_PackageToDownload'])
        runtime=evaluate(label+'-runtime',disabled,'ProcessFrameworkReferences;ResolveTargetingPackAssets;ResolveRuntimePackAssets',['RuntimePackAsset','FrameworkReference','RuntimeFramework'])
        item_sets[label+'-runtime']=runtime
    left=runtime_files(item_sets['sdk-false-runtime']);right=runtime_files(item_sets['sdk-true-runtime'])
    write('runtime-files-before-private.json',left);write('runtime-files-after-private.json',right)
    summary.update({'targets_equal':all(x['targets_equal'] for x in equal.values()),
        'libraries_equal':all(x['libraries_equal'] for x in equal.values()),
        'runtime_file_count_before':len(left),'runtime_file_count_after':len(right),
        'runtime_files_identical':left==right and bool(left),
        'locks_unchanged':all(sha(ROOT/'helper'/name/'packages.lock.json')==before_sources[name+'/packages.lock.json'] for name in PROJECTS),
        'source_projects_unchanged':all(sha(REPO/'helper'/key)==value for key,value in before_sources.items()),
        'reference_image_files':len(before_image),'reference_image_unchanged':before_image==published(),
        'unapproved_pack_directories':sum((ROOT/'packages'/x).exists() for x in ('microsoft.aspnetcore.app.runtime.win-x64','microsoft.windowsdesktop.app.runtime.win-x64'))})
    write('result.json',summary)
    print('LOCAL_RESULT '+json.dumps(summary),flush=True)
    if not all(summary[key] for key in ('targets_equal','libraries_equal','runtime_files_identical',
                                       'locks_unchanged','source_projects_unchanged','reference_image_unchanged')):
        raise RuntimeError('COMPARISON_GUARD_FAILED')
    if summary['unapproved_pack_directories']:
        raise RuntimeError('UNAPPROVED_PACK_PRESENT')
    return 0

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--run-name',default=ROOT.name)
    args=parser.parse_args()
    try:
        if not __import__('re').fullmatch(r'[A-Za-z0-9-]+',args.run_name):raise RuntimeError('INVALID_RUN_NAME')
        ROOT=REPO/'native/build'/args.run_name
        sys.exit(main())
    except Exception as error:
        reason=str(error) if isinstance(error,RuntimeError) else type(error).__name__
        print('STOP reason='+reason,flush=True);sys.exit(2)
