"""Read isolated evidence and export sanitized indexes. No network or product."""
import csv
import hashlib
import json
from pathlib import Path
import urllib.parse

REPO=Path(__file__).resolve().parents[2]
ROOT=REPO/'native/build/fase3-lote-cerrado-20261126-01'
OUT=REPO/'helper'
data=json.loads((ROOT/'evidence/downloads-private.json').read_text())
signatures=json.loads((ROOT/'evidence/signatures.json').read_text())
by_url={x['url']:x for x in data['artifacts']}
rows=[]
for item in sorted(data['artifacts'],key=lambda x:x['url']):
    rows.append({'url':item['url'],'bytes':item['bytes'],'kind':item['kind'],
                 'verification':','.join(item['methods']) if item['kind']=='jar' else 'NuGet SDK ValidateIntegrityAsync; CMS math; GetContentHash',
                 'weak_only':str(item.get('weak_only','')),'lock_comparison':item.get('nuget',{}).get('lockComparison','NOT_APPLICABLE'),
                 'publisher_signature_trust':'NO_VERIFICADO'})
with (OUT/'FASE3-LOTE-DESCARGAS.csv').open('w',encoding='utf8',newline='') as f:
    writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
transfers=[]
for request in data['requests']:
    if request['status']!=200:continue
    url=request['url']
    if url in by_url:path=ROOT/by_url[url]['file'];method=';'.join(by_url[url].get('methods',['NuGet SDK integrity + lock if present']))
    elif url.endswith('.asc'):path=ROOT/'signatures'/url.rsplit('/',1)[-1];method='GPG_NO_AUTHENTICATED_KEY'
    else:path=ROOT/'deps/m2'/url.removeprefix('https://repo.maven.apache.org/maven2/');method='CHECKSUM_METADATA_SAME_HTTPS_CHANNEL'
    transfers.append({'url':url,'bytes':path.stat().st_size,'verification':method,'sha256_private':hashlib.sha256(path.read_bytes()).hexdigest()})
(ROOT/'evidence/all-transfers-private.json').write_text(json.dumps(transfers,indent=2)+'\n',encoding='utf8')
with (OUT/'FASE3-LOTE-TRANSFERENCIAS.csv').open('w',encoding='utf8',newline='') as f:
    writer=csv.DictWriter(f,fieldnames=['url','bytes','verification'],extrasaction='ignore');writer.writeheader();writer.writerows(transfers)
with (OUT/'FASE3-FIRMAS-PRODUCTO.csv').open('w',encoding='utf8',newline='') as f:
    fields=['url','bytes','status','gpg_exit','issuer_fingerprints_declared','key_ids_declared','authenticated_publisher']
    writer=csv.DictWriter(f,fieldnames=fields,extrasaction='ignore');writer.writeheader()
    for sig in signatures:
        row=dict(sig)
        for key in ('issuer_fingerprints_declared','key_ids_declared'):row[key]=';'.join(row.get(key,[]))
        writer.writerow(row)
summary={'artifacts':len(rows),'transfers':len(transfers),'bytes_binary':sum(x['bytes'] for x in data['artifacts']),'bytes_transfers':sum(x['bytes'] for x in transfers),'domains':sorted(set(urllib.parse.urlsplit(x['url']).hostname for x in data['requests']))}
(ROOT/'evidence/export-summary.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf8')
print(json.dumps(summary))
