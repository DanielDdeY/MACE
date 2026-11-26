"""Exactly the approved 192 JAR + 28 nupkg, checksums and nine product signatures.

No Maven, NuGet CLI, product, installation, key import or global environment edit.
Full digests/requests remain private under ignored native/build.
"""
from __future__ import annotations
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request

REPO=Path(__file__).resolve().parents[2]
ROOT=REPO/'native/build/fase3-lote-cerrado-20261126-01'
PLAN=REPO/'helper/FASE3-LOTE-BINARIO-PROPUESTO.json'
BASE='https://repo.maven.apache.org/maven2/'
stop_event=threading.Event()

class Stop(Exception):pass

def digest(data,algorithm):return hashlib.new(algorithm,data).hexdigest()

def allowlist(plan):
    jars={x['url'] for x in plan['maven']};packages={x['url'] for x in plan['nuget']}
    if len(jars)!=192 or len(packages)!=28:raise Stop('PLAN_COUNT_CHANGED')
    if any(not u.startswith(BASE) or not u.endswith('.jar') for u in jars):raise Stop('PLAN_MAVEN_REJECTED')
    if any(urllib.parse.urlsplit(u).netloc!='api.nuget.org' or not u.endswith('.nupkg') for u in packages):raise Stop('PLAN_NUGET_REJECTED')
    products={x['url'] for x in plan['maven'] if (x['groupId']=='org.openjfx' and x['artifactId'] in ('javafx-base','javafx-graphics','javafx-controls','javafx-fxml') and x['version']=='27.0.1.1') or (x['groupId']=='io.github.mkpaz' and x['artifactId']=='atlantafx-base' and x['version']=='2.0.1')}
    if len(products)!=9:raise Stop('PRODUCT_SIGNATURE_COUNT_CHANGED')
    allowed=jars|packages|{u+'.'+a for u in jars for a in ('sha512','sha256','sha1','md5')}|{u+'.asc' for u in products}
    return allowed,products

class Downloader:
    def __init__(self,allowed):self.allowed=allowed;self.records={};self.requests=[];self.lock=threading.Lock()
    def guard(self,url):
        if url not in self.allowed:stop_event.set();raise Stop('UNLISTED_URL_OR_REDIRECT')
        if urllib.parse.urlsplit(url).scheme!='https':raise Stop('HTTPS_REQUIRED')
    def get(self,url,relative):
        self.guard(url)
        if stop_event.is_set():raise Stop('BATCH_STOPPED')
        target=ROOT/relative
        if not target.resolve().is_relative_to(ROOT.resolve()):raise Stop('TARGET_ESCAPE')
        if target.exists():return target.read_bytes()
        downloader=self
        class Redirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self,req,fp,code,msg,headers,newurl):
                downloader.guard(newurl)
                return super().redirect_request(req,fp,code,msg,headers,newurl)
        try:
            with urllib.request.build_opener(Redirect()).open(urllib.request.Request(url,headers={'User-Agent':'MACE-approved-closed-batch'}),timeout=45) as response:body=response.read()
            target.parent.mkdir(parents=True,exist_ok=True)
            partial=target.with_name(target.name+'.partial')
            partial.write_bytes(body);partial.replace(target)
            with self.lock:self.requests.append({'url':url,'status':200,'bytes':len(body)})
            return body
        except urllib.error.HTTPError as error:
            with self.lock:self.requests.append({'url':url,'status':error.code,'bytes':0})
            if error.code==404:return None
            raise
    def jar(self,item):
        url=item['url'];relative='deps/m2/'+url.removeprefix(BASE)
        body=self.get(url,relative)
        if body is None:raise Stop('LISTED_JAR_NOT_FOUND')
        checks=[]
        for algorithm in ('sha512','sha256','sha1','md5'):
            side=self.get(url+'.'+algorithm,relative+'.'+algorithm)
            if side is None:continue
            values=re.findall(r'(?i)(?<![0-9a-f])[0-9a-f]{'+str(hashlib.new(algorithm).digest_size*2)+r'}(?![0-9a-f])',side.decode('ascii'))
            if len(values)!=1 or digest(body,algorithm).lower()!=values[0].lower():stop_event.set();raise Stop('JAR_CHECKSUM_MISMATCH')
            checks.append(algorithm)
        if not checks:stop_event.set();raise Stop('JAR_CHECKSUM_MISSING')
        record={'url':url,'file':relative,'bytes':len(body),'sha256_private':digest(body,'sha256'),'methods':checks,'weak_only':not any(x in checks for x in ('sha512','sha256')),'kind':'jar','verified':True}
        with self.lock:self.records[url]=record
        print('JAR_PASS '+item['artifactId']+':'+item['version']+' checks='+','.join(checks),flush=True)
        self.save()
    def save(self):
        with self.lock:
            data={'date':'2026-11-26','artifacts':list(self.records.values()),'requests':list(self.requests),'stopped':stop_event.is_set()}
            path=ROOT/'evidence/downloads-private.json';temp=path.with_suffix('.tmp')
            temp.write_text(json.dumps(data,indent=2)+'\n',encoding='utf8');temp.replace(path)

def lock_hashes():
    result={}
    for name in ('Mace.Lhm.Helper','Mace.Lhm.Protocol','Mace.Lhm.Tests'):
        file=REPO/'helper'/name/'packages.lock.json'
        if not file.exists():continue
        for deps in json.loads(file.read_text(encoding='utf-8-sig'))['dependencies'].values():
            for component,row in deps.items():
                if not row.get('contentHash'):continue
                key=(component.lower(),row['resolved'])
                if key in result and result[key]!=row['contentHash']:raise Stop('CONFLICTING_LOCK_HASHES')
                result[key]=row['contentHash']
    return result

def main():
    before={k:os.environ.get(k) for k in ('JAVA_HOME','PATH')}
    ROOT.mkdir(parents=True,exist_ok=True);(ROOT/'evidence').mkdir(exist_ok=True)
    plan=json.loads(PLAN.read_text());allowed,products=allowlist(plan);down=Downloader(allowed)
    try:
        # A failed worker closes the gate; no unlisted request can be issued by a redirect.
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            futures=[pool.submit(down.jar,item) for item in plan['maven']]
            for future in concurrent.futures.as_completed(futures):
                try:future.result()
                except Exception:stop_event.set();raise
        expected=lock_hashes()
        for index,item in enumerate(plan['nuget']):
            url=item['url'];relative='deps/nupkg-feed/'+url.rsplit('/',1)[-1]
            body=down.get(url,relative)
            if body is None:raise Stop('LISTED_NUPKG_NOT_FOUND')
            request=ROOT/'evidence/nupkg-request-private.json';answer=ROOT/'evidence/nupkg-answer-private.json'
            request.write_text(json.dumps({'file':str(ROOT/relative),'expected':expected.get((item['component'].lower(),item['version']))}),encoding='utf8')
            completed=subprocess.run(['pwsh','-NoProfile','-File',str(Path(__file__).with_name('verificar-nupkg-local.ps1')),'-RequestFile',str(request),'-ResultFile',str(answer)],capture_output=True,text=True)
            result=json.loads(answer.read_text(encoding='utf-8-sig')) if answer.exists() else {'ok':False,'reason':'NUPKG_VERIFIER_NO_RESULT'}
            if completed.returncode or not result.get('ok'):raise Stop(result.get('reason','NUPKG_VERIFIER_FAILED'))
            record={'url':url,'file':relative,'bytes':len(body),'sha256_private':digest(body,'sha256'),'kind':'nupkg','verified':True,'nuget':result}
            down.records[url]=record;down.save()
            print('NUPKG_PASS '+item['component']+':'+item['version']+' lock='+result['lockComparison'],flush=True)
        gpg=Path('C:/Program Files/Git/usr/bin/gpg.exe')
        signatures=[]
        for url in sorted(products):
            relative='signatures/'+url.rsplit('/',1)[-1]+'.asc'
            sig=down.get(url+'.asc',relative)
            row={'url':url+'.asc','bytes':len(sig) if sig else 0,'status':'NO_VERIFICADO','reason':'SIGNATURE_NOT_PUBLISHED' if sig is None else 'NO_AUTHENTICATED_KEY'}
            if sig is not None and gpg.exists():
                home=ROOT/'gpg-isolated';home.mkdir(exist_ok=True)
                jar=ROOT/down.records[url]['file']
                command=[str(gpg),'--no-options','--homedir',str(home),'--batch','--no-auto-key-retrieve','--auto-key-locate','clear','--status-fd','1','--verify',str(ROOT/relative),str(jar)]
                verification=subprocess.run(command,capture_output=True,text=True)
                statuses=[x for x in verification.stdout.splitlines() if x.startswith('[GNUPG:]')]
                row['gpg_exit']=verification.returncode
                row['issuer_fingerprints_declared']=sorted(set(re.findall(r'(?<![A-Fa-f0-9])[A-Fa-f0-9]{40}(?![A-Fa-f0-9])','\n'.join(statuses))))
                row['key_ids_declared']=sorted(set(re.findall(r'NO_PUBKEY ([A-Fa-f0-9]{16})','\n'.join(statuses))))
                row['authenticated_publisher']=False
            signatures.append(row)
        (ROOT/'evidence/signatures.json').write_text(json.dumps(signatures,indent=2)+'\n',encoding='utf8')
        # Seed only our already downloaded POM metadata, never a user cache.
        source=REPO/'native/build/fase3-etapa3-metadata-20261125-01/metadata'
        for path in source.rglob('*'):
            if path.is_file():
                destination=ROOT/'deps/m2'/path.relative_to(source)
                destination.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,destination)
        if any(os.environ.get(k)!=v for k,v in before.items()):raise Stop('ENVIRONMENT_CHANGED')
        print('BATCH_PASS jars=192 nupkg=28 binary_keys_imported=0',flush=True)
        return 0
    except Exception as error:
        stop_event.set();reason=str(error) if isinstance(error,Stop) else type(error).__name__
        (ROOT/'evidence/stop.json').write_text(json.dumps({'reason':reason})+'\n',encoding='utf8')
        print('STOP reason='+reason,flush=True);return 2
    finally:down.save()

if __name__=='__main__':sys.exit(main())
