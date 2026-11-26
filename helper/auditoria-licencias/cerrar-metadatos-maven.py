"""Static Maven POM closure. No Maven/JAR/plugin execution or user cache access.

Only HTTPS repo.maven.apache.org POM, maven-metadata*.xml and checksum GETs.
Writes isolated evidence; public indexes never include digests or local paths.
This is a bounded POM model reader, not a replacement for Maven ModelBuilder.
"""
from __future__ import annotations
import collections
import concurrent.futures
import csv
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

REPO=Path(__file__).resolve().parents[2]
ROOT=REPO/'native/build/fase3-etapa3-metadata-20261125-01'
HOST='repo.maven.apache.org'
BASE='https://'+HOST+'/maven2/'
NS={'m':'http://maven.apache.org/POM/4.0.0'}
PATTERN=re.compile(r'\$\{([^}]+)\}')

class Stop(Exception): pass

def permitted(url):
    p=urllib.parse.urlsplit(url)
    name=p.path.rsplit('/',1)[-1]
    name=re.sub(r'\.(sha512|sha256|sha1|md5)$','',name)
    return (p.scheme=='https' and p.netloc==HOST and not p.query and not p.fragment
            and p.path.startswith('/maven2/') and '..' not in p.path.split('/')
            and (name.endswith('.pom') or re.fullmatch(r'maven-metadata[^/]*\.xml',name) is not None))

class RestrictedRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        if not permitted(newurl): raise Stop('UNAPPROVED_REDIRECT')
        return super().redirect_request(req,fp,code,msg,headers,newurl)

def expand(value,props):
    for _ in range(40):
        other=PATTERN.sub(lambda m:props.get(m[1],m[0]),value or '')
        if other==value: return other
        value=other
    return value

def text(node,path,default=''):
    return node.findtext(path,default,NS).strip() if node is not None else default

def fixed(v):
    return bool(v and not re.search(r'\$\{|[\[\](),/\\\s]',v) and not v.upper().endswith('SNAPSHOT') and v not in ('LATEST','RELEASE'))

def path_for(gav):
    g,a,v=gav
    if not all(re.fullmatch(r'[A-Za-z0-9_.+\-]+',x) for x in gav) or not fixed(v): raise Stop('NON_FIXED_COORDINATE')
    return g.replace('.','/')+'/'+a+'/'+v+'/'+a+'-'+v+'.pom'

class Reader:
    def __init__(self):
        self.transfers={};self.attempts=[];self.models={};self.loading=set()
        self.issues=[];self.edges=[];self.binaries={};self.profiles=[]
        self.opener=urllib.request.build_opener(RestrictedRedirects())
        previous=ROOT/'evidence/requests.json'
        self.absent=set()
        if previous.exists():
            # Reuse only this campaign's negative responses for fixed POM sidecars.
            self.absent={x['url'] for x in json.loads(previous.read_text(encoding='utf8'))
                         if x['status']==404 and re.search(r'\.pom\.(sha512|sha256|sha1)$',x['url'])}

    def fetch(self,url):
        if not permitted(url): raise Stop('UNAPPROVED_REQUEST')
        if url in self.absent:
            self.attempts.append({'url':url,'status':404,'bytes':0,'campaign_cache':True})
            return None
        local=ROOT/'metadata'/url.removeprefix(BASE)
        if local.exists(): return local.read_bytes()
        local.parent.mkdir(parents=True,exist_ok=True)
        try:
            with self.opener.open(urllib.request.Request(url,headers={'User-Agent':'MACE-metadata-only-audit'}),timeout=35) as response:
                body=response.read(8*1024*1024+1)
                if len(body)>8*1024*1024: raise Stop('METADATA_TOO_LARGE')
            local.write_bytes(body)
            self.attempts.append({'url':url,'status':200,'bytes':len(body)})
            return body
        except urllib.error.HTTPError as e:
            self.attempts.append({'url':url,'status':e.code,'bytes':0})
            if e.code==404: return None
            raise

    def pom(self,gav):
        url=BASE+path_for(gav)
        body=self.fetch(url)
        if body is None: raise Stop('POM_NOT_FOUND '+':'.join(gav))
        if url not in self.transfers:
            with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
                jobs={a:pool.submit(self.fetch,url+'.'+a) for a in ('sha512','sha256','sha1')}
                sums={a:f.result() for a,f in jobs.items()}
            matches=[]
            for a,b in sums.items():
                if b is None: continue
                values=re.findall(r'(?i)(?<![0-9a-f])[0-9a-f]{'+str(hashlib.new(a).digest_size*2)+r'}(?![0-9a-f])',b.decode('ascii'))
                if len(values)!=1 or hashlib.new(a,body).hexdigest().lower()!=values[0].lower(): raise Stop('CHECKSUM_MISMATCH')
                matches.append(a)
                self.transfers[url+'.'+a]={'url':url+'.'+a,'bytes':len(b),'method':'CHECKSUM_METADATA_HTTPS','strength':'SAME_CHANNEL','sha256_private':hashlib.sha256(b).hexdigest()}
            if not matches: raise Stop('CHECKSUM_MISSING')
            best=matches[0]
            self.transfers[url]={'url':url,'bytes':len(body),'method':best,'strength':'WEAK_SHA1' if best=='sha1' else 'STRONG_CHECKSUM_SAME_CHANNEL','sha256_private':hashlib.sha256(body).hexdigest()}
            print('POM '+':'.join(gav)+' checksum='+best,flush=True)
        return ET.fromstring(body)

    def active(self,profile,gav):
        act=profile.find('m:activation',NS)
        if act is None:return False
        clauses=[]
        for child in act:
            tag=child.tag.split('}')[-1]
            if tag=='activeByDefault':continue
            if tag=='os':
                values={'family':'windows','name':'windows 10','arch':'amd64','version':'10.0'}
                good=True
                for item in child:
                    key=item.tag.split('}')[-1];want=(item.text or '').lower();actual=values.get(key,'')
                    good &= actual!=want[1:] if want.startswith('!') else actual==want
                clauses.append(good)
            elif tag=='jdk':
                value=(child.text or '').strip()
                if re.fullmatch(r'!?\d+(?:\.\d+)*',value):
                    clauses.append(not '25.0.5.1'.startswith(value[1:]) if value.startswith('!') else '25.0.5.1'.startswith(value))
                else:
                    m=re.fullmatch(r'([\[(])([\d.]*),([\d.]*)([\])])',value)
                    if not m:self.issues.append({'kind':'PROFILE_JDK_UNRESOLVED','gav':':'.join(gav),'profile':text(profile,'m:id')});return False
                    def number(s):return tuple((list(map(int,s.split('.')))+[0]*5)[:5])
                    current=number('25.0.5.1');low=number(m[2]) if m[2] else None;high=number(m[3]) if m[3] else None
                    clauses.append((low is None or current>low or current==low and m[1]=='[') and (high is None or current<high or current==high and m[4]==']'))
            elif tag=='property':
                name=text(child,'m:name');val=text(child,'m:value')
                props={'java.version':'25.0.5.1','os.arch':'amd64','os.name':'Windows 10'}
                if name.startswith('!'):clauses.append(name[1:] not in props)
                elif val.startswith('!'):clauses.append(props.get(name)!=val[1:])
                else:clauses.append(name in props and (not val or props[name]==val))
            elif tag=='file':
                self.issues.append({'kind':'PROFILE_FILE_NOT_EVALUATED','gav':':'.join(gav),'profile':text(profile,'m:id')})
                return False
        return all(clauses) if clauses else text(act,'m:activeByDefault')=='true'

    def model(self,gav):
        if gav in self.models:return self.models[gav]
        if gav in self.loading:raise Stop('POM_CYCLE')
        self.loading.add(gav)
        node=self.pom(gav)
        own={p.tag.split('}')[-1]:(p.text or '').strip() for p in node.findall('m:properties/*',NS)}
        parent=node.find('m:parent',NS);base={'raw_properties':{},'management_levels':[],'raw_dependencies':[]}
        parent_gav=None
        if parent is not None:
            parent_gav=tuple(expand(text(parent,'m:'+k),own) for k in ('groupId','artifactId','version'))
            self.edges.append({'from':':'.join(gav),'to':':'.join(parent_gav),'kind':'parent'})
            base=self.model(parent_gav)
        raw_props=dict(base['raw_properties']);raw_props.update(own)
        props=dict(raw_props)
        props.update({'project.groupId':gav[0],'pom.groupId':gav[0],'project.artifactId':gav[1],'pom.artifactId':gav[1],'project.version':gav[2],'pom.version':gav[2]})
        if parent_gav:
            props.update({'project.parent.groupId':parent_gav[0],'project.parent.artifactId':parent_gav[1],'project.parent.version':parent_gav[2],'parent.version':parent_gav[2]})
        selected=[]
        for p in node.findall('m:profiles/m:profile',NS):
            active=self.active(p,gav)
            self.profiles.append({'gav':':'.join(gav),'profile':text(p,'m:id'),'active':active})
            if active:selected.append(p)
        # Maven disables default profiles when another profile from the same POM is active.
        if any(text(p,'m:activation/m:activeByDefault')!='true' for p in selected):
            selected=[p for p in selected if text(p,'m:activation/m:activeByDefault')!='true']
        for p in selected:
            changes={c.tag.split('}')[-1]:(c.text or '').strip() for c in p.findall('m:properties/*',NS)}
            props.update(changes);raw_props.update(changes)
        for _ in range(20):props={k:expand(v,props) for k,v in props.items()}
        dm={};local=[]
        for source in [node]+selected:local.extend(source.findall('m:dependencyManagement/m:dependencies/m:dependency',NS))
        levels=base['management_levels']+[local]
        def dep(d,management):
            info={k:expand(text(d,'m:'+k,default),props) for k,default in (('groupId',''),('artifactId',''),('version',''),('scope',''),('type','jar'),('classifier',''),('optional','false'))}
            key=(info['groupId'],info['artifactId'],info['type'],info['classifier'])
            entry=management.get(key,{})
            for k in ('version','scope'):
                if not info[k]:info[k]=entry.get(k,'compile' if k=='scope' else '')
            info['exclusions']=[(expand(text(x,'m:groupId'),props),expand(text(x,'m:artifactId'),props)) for x in d.findall('m:exclusions/m:exclusion',NS)]
            return info
        for level in levels:
            imported={}
            for d in level:
                info=dep(d,dm)
                if info['type']=='pom' and info['scope']=='import':
                    target=(info['groupId'],info['artifactId'],info['version'])
                    self.edges.append({'from':':'.join(gav),'to':':'.join(target),'kind':'bom_import'})
                    for key,value in self.model(target)['management'].items():imported.setdefault(key,value)
            dm.update(imported)
            for d in level:
                info=dep(d,dm)
                if info['scope']!='import':dm[(info['groupId'],info['artifactId'],info['type'],info['classifier'])]=info
        dependencies=[]
        own_deps=[]
        for source in [node]+selected:own_deps.extend(source.findall('m:dependencies/m:dependency',NS))
        raw_dependencies=base['raw_dependencies']+own_deps
        for d in raw_dependencies:
            info=dep(d,dm)
            dependencies=[x for x in dependencies if (x['groupId'],x['artifactId'],x['type'],x['classifier'])!=(info['groupId'],info['artifactId'],info['type'],info['classifier'])]
            dependencies.append(info)
        model={'properties':props,'management':dm,'dependencies':dependencies,'packaging':text(node,'m:packaging','jar'),
               'raw_properties':raw_props,'management_levels':levels,'raw_dependencies':raw_dependencies}
        self.models[gav]=model;self.loading.remove(gav)
        return model

    def graph(self,realm,roots,managed=None):
        queue=collections.deque((gav,kind,classifier,frozenset(),0) for gav,kind,classifier in roots)
        chosen={}
        while queue:
            gav,kind,classifier,excluded,depth=queue.popleft();key=(gav[0],gav[1],kind,classifier)
            if any((g in ('*',gav[0]) and a in ('*',gav[1])) for g,a in excluded):continue
            if key in chosen:continue
            if not fixed(gav[2]):
                self.issues.append({'kind':'NON_FIXED_VERSION','realm':realm,'gav':':'.join(gav)});continue
            chosen[key]=gav[2]
            model=self.model(gav)
            if kind!='pom':
                binary=(gav,kind,classifier)
                self.binaries.setdefault(binary,set()).add(realm)
            for d in model['dependencies']:
                if d['optional']=='true' or d['scope'] in ('test','provided','system','import'):continue
                dv=d['version'];depkey=(d['groupId'],d['artifactId'],d['type'],d['classifier'])
                if managed and depkey in managed:dv=managed[depkey]['version']
                target=(d['groupId'],d['artifactId'],dv)
                self.edges.append({'from':':'.join(gav),'to':':'.join(target),'kind':'dependency','realm':realm,'scope':d['scope']})
                queue.append((target,d['type'],d['classifier'],excluded|frozenset(d['exclusions']),depth+1))

    def output(self):
        evidence=ROOT/'evidence';evidence.mkdir(exist_ok=True)
        def write(name,data): (evidence/name).write_text(json.dumps(data,indent=2,ensure_ascii=True)+'\n',encoding='utf8')
        write('transfers-private.json',list(self.transfers.values()))
        write('requests.json',self.attempts);write('issues.json',self.issues);write('edges.json',self.edges);write('profiles.json',self.profiles)
        binaries=[]
        for (gav,kind,classifier),realms in sorted(self.binaries.items()):
            extension={'maven-plugin':'jar','test-jar':'jar','bundle':'jar'}.get(kind,kind)
            actual=classifier or ('tests' if kind=='test-jar' else '')
            stem=path_for(gav).removesuffix('.pom')+('-'+actual if actual else '')+'.'+extension
            binaries.append({'groupId':gav[0],'artifactId':gav[1],'version':gav[2],'type':kind,'classifier':actual,'url':BASE+stem,'realms':';'.join(sorted(realms)),'downloaded':False,'signature':'NO_VERIFICADO'})
        write('binary-proposal.json',binaries)
        write('summary.json',{'poms':len(self.models),'transfers':len(self.transfers),'bytes':sum(x['bytes'] for x in self.transfers.values()),'binary_proposals':len(binaries),'issues':len(self.issues),'maven_executed':False,'binary_downloads':0})

def main():
    ROOT.mkdir(parents=True,exist_ok=True);(ROOT/'metadata').mkdir(exist_ok=True)
    reader=Reader();before={k:os.environ.get(k) for k in ('JAVA_HOME','PATH')}
    try:
        junit=reader.model(('org.junit','junit-bom','5.10.2'))['management']
        app=[(('org.openjfx','javafx-'+a,'27.0.1.1'),'jar',c) for a in ('base','graphics','controls','fxml') for c in ('','win')]
        app += [(('io.github.mkpaz','atlantafx-base','2.0.1'),'jar','')]
        app += [(('org.junit.jupiter','junit-jupiter','5.10.2'),'jar','')]
        app += [(('org.mockito',a,'5.20.0'),'jar','') for a in ('mockito-core','mockito-junit-jupiter')]
        reader.graph('application_and_tests',app,junit)
        plugins=[('maven-dependency-plugin','3.8.1'),('maven-compiler-plugin','3.13.0'),('maven-surefire-plugin','3.2.5'),('maven-resources-plugin','3.4.0'),('maven-jar-plugin','3.5.0')]
        for a,v in plugins:reader.graph('plugin:'+a,[(('org.apache.maven.plugins',a,v),'maven-plugin','')])
        reader.graph('plugin:javafx-maven-plugin',[(('org.openjfx','javafx-maven-plugin','0.0.8'),'maven-plugin','')])
        # Dynamic provider needed by the fixed Surefire 3.2.5/Jupiter combination.
        reader.graph('surefire:junit-platform-provider',[(('org.apache.maven.surefire','surefire-junit-platform','3.2.5'),'jar','')])
        reader.graph('surefire:booter',[(('org.apache.maven.surefire','surefire-booter','3.2.5'),'jar','')])
        # Proposed conditional launcher alignment for the application's fixed Jupiter 5.10.2.
        reader.graph('surefire:launcher-aligned-to-application',[(('org.junit.platform','junit-platform-launcher','1.10.2'),'jar','')])
        if any(os.environ.get(k)!=v for k,v in before.items()):raise Stop('ENVIRONMENT_CHANGED')
        print('COMPLETE bounded_static_metadata=1 issues='+str(len(reader.issues)),flush=True)
        return 0
    except Exception as error:
        reason=str(error) if isinstance(error,Stop) else type(error).__name__
        reader.issues.append({'kind':'STOP','reason':reason})
        print('STOP reason='+reason,flush=True)
        return 2
    finally:reader.output()

if __name__=='__main__':sys.exit(main())
