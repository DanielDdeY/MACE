"""Export sanitized indexes from the isolated metadata campaign. No network."""
from pathlib import Path
import collections
import csv
import json
import urllib.parse

REPO=Path(__file__).resolve().parents[2]
EVIDENCE=REPO/'native/build/fase3-etapa3-metadata-20261125-01/evidence'
OUT=REPO/'helper'

def read(name):return json.loads((EVIDENCE/name).read_text(encoding='utf8'))
def table(name,fields,rows):
    with (OUT/name).open('w',encoding='utf8',newline='') as output:
        writer=csv.DictWriter(output,fieldnames=fields,extrasaction='ignore')
        writer.writeheader();writer.writerows(rows)

trans=read('transfers-private.json');binaries=read('binary-proposal.json')
issues=read('issues.json');edges=read('edges.json');profiles=read('profiles.json')
table('FASE3-MAVEN-METADATOS.csv',['url','bytes','method','strength'],sorted(trans,key=lambda x:x['url']))
unique={json.dumps(e,sort_keys=True):e for e in edges}
table('FASE3-MAVEN-GRAFO.csv',['from','to','kind','realm','scope'],sorted(unique.values(),key=lambda x:(x['from'],x['to'],x['kind'],x.get('realm',''))))
table('FASE3-MAVEN-PERFILES.csv',['gav','profile','active'],profiles)
table('FASE3-MAVEN-CONDICIONES.csv',['kind','gav','profile'],issues)
nuget=json.loads((OUT/'pendientes/CANDIDATA-NUGET-DESCARGAS.json').read_text(encoding='utf-8-sig'))['entries']
plan={'date':'2026-11-25','checkpoint':'053277e','candidate_variant':'EXCLUIR_MONO',
      'approval_required':True,'downloaded_now':False,
      'scope':'Bounded static POM closure; not Maven ModelBuilder output. See accompanying report.',
      'maven':binaries,
      'nuget':[{'component':x['component'],'version':x['version'],'url':x['url'],'downloaded':False,
                'verification':'locked contentHash and applicable signature policy; NOT_VERIFIED_NOW'} for x in nuget]}
(OUT/'FASE3-LOTE-BINARIO-PROPUESTO.json').write_text(json.dumps(plan,indent=2,ensure_ascii=True)+'\n',encoding='utf8')
poms=[x for x in trans if x['url'].endswith('.pom')]
stats={'poms':len(poms),'transfers':len(trans),'bytes':sum(x['bytes'] for x in trans),
       'pom_checksums':dict(collections.Counter(x['method'] for x in poms)),
       'graph_edges':len(unique),'profiles':len(profiles),'issues':len(issues),
       'issues_by_kind':dict(collections.Counter(x['kind'] for x in issues)),
       'maven_binary_proposals':len(binaries),'unique_maven_binary_urls':len(set(x['url'] for x in binaries)),
       'nuget_proposals':len(nuget),
       'request_hosts':sorted(set(urllib.parse.urlsplit(x['url']).hostname for x in read('requests.json')))}
(EVIDENCE/'export-summary.json').write_text(json.dumps(stats,indent=2)+'\n',encoding='utf8')
print(json.dumps(stats))
