"""Approved Stage 3 tools + first Maven parent gate. No build, install or product run.

Writes only to a new ignored native/build root. Full digests stay in its private
operational index, never stdout. Does not delegate networking to Maven/NuGet.
"""
from __future__ import annotations
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import urllib.request
import urllib.parse
import urllib.error
import xml.etree.ElementTree as ET
import zipfile

REPO = Path(__file__).resolve().parents[2]
ROOT = REPO / 'native/build/fase3-etapa3-20261125-01'
JDK = 'OpenJDK25U-jdk_x64_windows_hotspot_25.0.5.1_2.zip'
JDK_URL = 'https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5.1%2B2/' + JDK
MAVEN = 'apache-maven-3.9.16-bin.zip'
MAVEN_URL = 'https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/' + MAVEN
MAVEN_SUM = 'https://downloads.apache.org/maven/maven-3/3.9.16/binaries/' + MAVEN + '.sha512'
POM_URL = 'https://repo.maven.apache.org/maven2/org/apache/maven/plugins/maven-dependency-plugin/3.8.1/maven-dependency-plugin-3.8.1.pom'
APPROVED_GAV = {
    *[('org.openjfx', a, '27.0.1.1') for a in ('javafx','javafx-base','javafx-graphics','javafx-controls','javafx-fxml')],
    *[('org.mockito', a, '5.20.0') for a in ('mockito-core','mockito-junit-jupiter')],
    *[('net.bytebuddy', a, '1.17.7') for a in ('byte-buddy','byte-buddy-agent')],
    ('org.objenesis','objenesis','3.3'), ('io.github.mkpaz','atlantafx-base','2.0.1'),
    ('org.junit','junit-bom','5.10.2'),
    *[('org.junit.jupiter', a, '5.10.2') for a in ('junit-jupiter','junit-jupiter-api','junit-jupiter-engine','junit-jupiter-params')],
    *[('org.apache.maven.plugins', a, v) for a,v in (
        ('maven-compiler-plugin','3.13.0'),('maven-surefire-plugin','3.2.5'),('maven-dependency-plugin','3.8.1'))],
    ('org.openjfx','javafx-maven-plugin','0.0.8')
}
ORIGINS = {'github.com','dlcdn.apache.org','downloads.apache.org','repo.maven.apache.org'}
REDIRECTS = ORIGINS | {'release-assets.githubusercontent.com','objects.githubusercontent.com'}

class GateError(Exception):
    pass

class OfficialRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if urllib.parse.urlparse(newurl).scheme != 'https' or urllib.parse.urlparse(newurl).hostname not in REDIRECTS:
            raise GateError('REDIRECT_NOT_APPROVED')
        return super().redirect_request(req, fp, code, msg, headers, newurl)

def fetch(url, target):
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != 'https' or parsed.hostname not in ORIGINS or parsed.username or parsed.password:
        raise GateError('ORIGIN_NOT_APPROVED')
    # Never overwrite a previous successful/partial transfer.
    if target.exists():
        raise GateError('TARGET_EXISTS')
    request = urllib.request.Request(url, headers={'User-Agent':'MACE-stage3-approved-downloads'})
    with urllib.request.build_opener(OfficialRedirects()).open(request, timeout=45) as response:
        with target.open('xb') as output:
            while chunk := response.read(1024*1024):
                output.write(chunk)
    return target

def sha(path, algorithm):
    digest = hashlib.new(algorithm)
    with path.open('rb') as source:
        while chunk := source.read(1024*1024):
            digest.update(chunk)
    return digest.hexdigest()

def verified(url, sum_url, relative, algorithm):
    target = ROOT / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    # Resume only previously verified bytes against the exact approved checksum URL.
    sum_path = target.with_name(target.name + '.' + algorithm)
    if not sum_path.exists():
        fetch(sum_url, sum_path)
    expected = re.findall(r'(?i)(?<![0-9a-f])[0-9a-f]{' + str(hashlib.new(algorithm).digest_size*2) + r'}(?![0-9a-f])', sum_path.read_text(encoding='ascii'))
    if len(expected) != 1:
        raise GateError('CHECKSUM_FORMAT')
    if not target.exists():
        attempt = 1
        partial = target.with_name(target.name + '.part-' + str(attempt))
        while partial.exists():
            attempt += 1
            partial = target.with_name(target.name + '.part-' + str(attempt))
        fetch(url, partial)
        if sha(partial, algorithm).lower() != expected[0].lower():
            raise GateError('CHECKSUM_MISMATCH')
        partial.rename(target)
    if sha(target, algorithm).lower() != expected[0].lower():
        raise GateError('EXISTING_CHECKSUM_MISMATCH')
    return {'file':relative,'url':url,'checksum_url':sum_url,'algorithm':algorithm,
            'bytes':target.stat().st_size,'digest':expected[0].lower(),
            'verified':True,'signature':'NOT_VERIFIED','checksum_same_channel':True}

def save(state):
    output = ROOT / 'evidence/downloads-private.json'
    temp = output.with_suffix('.tmp')
    temp.write_text(json.dumps(state, indent=2)+'\n', encoding='utf8')
    temp.replace(output)

def extract(record, relative):
    destination = ROOT / relative
    if destination.exists():
        raise GateError('EXTRACTION_EXISTS')
    destination.mkdir()
    with zipfile.ZipFile(ROOT / record['file']) as archive:
        for member in archive.infolist():
            name = member.filename.replace('\\','/')
            if ':' in name or not (destination / name).resolve().is_relative_to(destination.resolve()):
                raise GateError('ZIP_PATH_REJECTED')
            if (member.external_attr >> 16) & 0o170000 == 0o120000:
                raise GateError('ZIP_SYMLINK_REJECTED')
        archive.extractall(destination)

def parent_from_pom(text):
    node = ET.fromstring(text).find('{*}parent')
    return tuple(node.findtext('{*}'+field,'').strip() for field in ('groupId','artifactId','version')) if node is not None else None

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resume', action='store_true')
    args = parser.parse_args()
    if not ROOT.resolve().is_relative_to((REPO / 'native/build').resolve()):
        raise GateError('ROOT_REJECTED')
    before = {name:os.environ.get(name) for name in ('JAVA_HOME','PATH')}
    if ROOT.exists() and not args.resume:
        raise GateError('ROOT_EXISTS_USE_EXPLICIT_RESUME')
    if args.resume and not (ROOT / 'scope.json').is_file():
        raise GateError('RESUME_SCOPE_MISSING')
    ROOT.mkdir(parents=True, exist_ok=args.resume)
    for folder in ('downloads','tools','deps/m2','deps/nuget','evidence'):
        (ROOT / folder).mkdir(parents=True, exist_ok=True)
    scope = {'stage':3,'date':'2026-11-25','build_authorized':False,'variant_next':'CONSERVAR'}
    (ROOT / 'scope.json').write_text(json.dumps(scope)+'\n',encoding='utf8')
    state = {'date':'2026-11-25','artifacts':[],'unknown_downloads':0,'product_executed':False}
    try:
        jobs = [(JDK_URL,JDK_URL+'.sha256.txt','downloads/'+JDK,'sha256'),
                (MAVEN_URL,MAVEN_SUM,'downloads/'+MAVEN,'sha512')]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            futures = [pool.submit(verified,*job) for job in jobs]
            for future in concurrent.futures.as_completed(futures):
                record = future.result()
                state['artifacts'].append(record); save(state)
                print('VERIFIED file='+Path(record['file']).name+' bytes='+str(record['bytes'])+' algorithm='+record['algorithm'],flush=True)
        for record in state['artifacts']:
            relative = 'tools/temurin' if record['file'].endswith(JDK) else 'tools/maven'
            if not (ROOT / relative).exists():
                extract(record,relative)
        relative = 'deps/m2/org/apache/maven/plugins/maven-dependency-plugin/3.8.1/maven-dependency-plugin-3.8.1.pom'
        # Central publishes different sidecar sets for older releases. Metadata
        # is only inspected, not executed; record the algorithm actually available.
        for algorithm in ('sha512','sha256','sha1'):
            try:
                record = verified(POM_URL,POM_URL+'.'+algorithm,relative,algorithm)
                break
            except urllib.error.HTTPError as error:
                if error.code not in (404,410):
                    raise
        else:
            raise GateError('POM_CHECKSUM_UNAVAILABLE')
        state['artifacts'].append(record); save(state)
        parent = parent_from_pom((ROOT / relative).read_text(encoding='utf8'))
        if parent is not None and parent not in APPROVED_GAV:
            state['blocked'] = {'reason':'UNAPPROVED_MAVEN_PARENT','gav':':'.join(parent),
                'url':'https://repo.maven.apache.org/maven2/'+parent[0].replace('.','/')+'/'+parent[1]+'/'+parent[2]+'/'+parent[1]+'-'+parent[2]+'.pom',
                'downloaded':False}
            state['resolution_complete'] = False
            print('STOP reason=UNAPPROVED_MAVEN_PARENT gav='+':'.join(parent)+' downloaded=0',flush=True)
            return 2
        # Do not expand this probe into an implicit open-ended dependency resolver.
        state['blocked'] = {'reason':'FULL_CLOSURE_NOT_RUN'}
        state['resolution_complete'] = False
        return 2
    except Exception as error:
        reason = str(error) if isinstance(error,GateError) else 'NETWORK_OR_IO_FAILED'
        state['error'] = {'reason':reason,'type':type(error).__name__}
        if isinstance(error,urllib.error.HTTPError):
            state['error']['http_status'] = error.code
        print('STOP reason='+reason+' type='+type(error).__name__,flush=True)
        return 1
    finally:
        state['environment_unchanged'] = all(os.environ.get(k)==v for k,v in before.items())
        save(state)

if __name__ == '__main__':
    try:
        sys.exit(main())
    except GateError as error:
        print('STOP reason='+str(error))
        sys.exit(1)
    except Exception as error:
        print('STOP reason=UNEXPECTED_PREPARE_FAILURE type='+type(error).__name__)
        sys.exit(1)
