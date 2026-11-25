"""Read-only Git audit. Writes only sanitized JSON to a requested ignored output.
No fetch, checkout, ref writes, raw identities, blob bodies or full hashes in output.
Coverage: every blob reachable from --all (not reflogs/unreachable objects).
"""
import argparse
import collections
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--repo', required=True)
parser.add_argument('--out', required=True)
args = parser.parse_args()
repo = Path(args.repo).resolve()

def git(*argv):
    result = subprocess.run(['git', '-C', str(repo), *argv], capture_output=True)
    if result.returncode:
        raise RuntimeError('GIT_READ_FAILED')
    return result.stdout

def safe_path(p):
    # Repository-relative paths only; never reproduce an absolute path/identity.
    if re.search(r'(?i)[a-z]:[\\/]|(?:^|/)users/|S-\d-\d+(?:-\d+)+|[\w.+-]+@[\w.-]+', p):
        return '[REDACTED_PATH]'
    return p

refs = git('for-each-ref', '--format=%(refname)').decode().splitlines()
commits = git('rev-list', '--all').decode().splitlines()
objects = {}
for row in git('rev-list', '--objects', '--all').decode('utf-8', 'replace').splitlines():
    oid, _, path = row.partition(' ')
    objects[oid] = path
metadata = git('cat-file', '--batch-all-objects', '--batch-check=%(objectname) %(objecttype) %(objectsize)').decode().splitlines()
blobs = {}
for row in metadata:
    oid, kind, length = row.split()
    if oid in objects and kind == 'blob':
        blobs[oid] = int(length)
request = ''.join(oid+'\n' for oid in blobs).encode()
batch = subprocess.run(['git', '-C', str(repo), 'cat-file', '--batch'], input=request, capture_output=True)
if batch.returncode:
    raise RuntimeError('GIT_BLOB_READ_FAILED')

patterns = {
    'profile_path': re.compile(r'(?i)[a-z]:[\\/]+users[\\/]+[^\\/\r\n"<>;]+'),
    'unix_profile_path': re.compile(r'/(?:home|Users)/[^/\s<>"\']+'),
    'sid_literal': re.compile(r'\bS-1-5-21-\d+-\d+-\d+(?:-\d+)?\b'),
    'email_literal': re.compile(r'[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}'),
    'machine_field': re.compile(r'(?im)^\s*(?:ComputerName|MachineName|HostName)\s*[:=]\s*[^\r\n]+'),
    'hardware_model': re.compile(r'(?i)(?:AMD\s+Ryzen\s+\d\s+\d{4}[A-Z0-9]*|Intel\s+Core[^\r\n<>]{0,55}|NVIDIA\s+(?:GeForce|RTX)[^\r\n<>]{0,55}|Radeon\s+RX\s+\d{3,4}[A-Z]*)'),
}
binary_ext = {'.dll','.exe','.jar','.zip','.pdb','.sys','.msi','.msix','.7z','.nupkg','.jmod','.lib','.so','.a'}
binaries = []
findings = []
blob_flags = {}
cursor = 0
for oid, length in blobs.items():
    end = batch.stdout.index(b'\n', cursor)
    header = batch.stdout[cursor:end].split()
    if header[0].decode() != oid or int(header[2]) != length:
        raise RuntimeError('BATCH_LAYOUT_FAILED')
    data = batch.stdout[end+1:end+1+length]
    cursor = end+length+2
    path = objects[oid]
    if Path(path).suffix.lower() in binary_ext or length >= 1024*1024:
        binaries.append({'path':safe_path(path),'bytes':length,'binary_extension':Path(path).suffix.lower() in binary_ext})
    if data.startswith((b'\xff\xfe', b'\xfe\xff')):
        text = data.decode('utf-16', 'replace')
    elif b'\0' in data:
        continue
    else:
        text = data.decode('utf-8', 'replace')
    flags = {name:len(pat.findall(text)) for name,pat in patterns.items()}
    flags = {name:n for name,n in flags.items() if n}
    if flags:
        blob_flags[oid] = flags
        findings.append({'path':safe_path(path),'categories':flags})

authors = collections.Counter()
for line in git('log', '--all', '--format=%an%x00%ae').decode('utf-8','replace').splitlines():
    name, _, email = line.partition('\0')
    authors[(name,email)] += 1
author_rows = [{'label':'AUTHOR_'+str(i+1),'commits':n,
                'email_kind':'github_noreply' if email.lower().endswith('@users.noreply.github.com') else 'other_public_email'}
               for i,((name,email),n) in enumerate(sorted(authors.items(),key=lambda item:(-item[1],item[0])))]
committers = git('log', '--all', '--format=%cn%x00%ce').decode('utf-8','replace').splitlines()
messages = git('log', '--all', '--format=%B').decode('utf-8','replace')
message_flags = {name:len(pat.findall(messages)) for name,pat in patterns.items()}

trees = {}
local_txt = {'diagnostico-admin.txt','diagnostico-normal.txt','prueba-cpu.txt'}
history_txt = sorted({safe_path(p) for p in objects.values() if Path(p).name.lower() in local_txt})
for ref in refs:
    entries = []
    for row in git('ls-tree','-rlz',ref).split(b'\0'):
        if not row:
            continue
        fields,path = row.split(b'\t',1)
        mode,kind,oid,length = fields.decode().split()
        if kind != 'blob':
            continue
        entries.append((oid,path.decode('utf-8','replace'),int(length)))
    root_names={path for oid,path,n in entries}
    trees[ref]={'files':len(entries),'LICENSE': 'LICENSE' in root_names,'CONTRIBUTING.md':'CONTRIBUTING.md' in root_names,
                'large_or_binary':[{'path':safe_path(path),'bytes':n} for oid,path,n in entries if Path(path).suffix.lower() in binary_ext or n>=1024*1024],
                'phase_reports':[safe_path(path) for oid,path,n in entries if path.startswith('helper/') and re.search(r'FASE|INFORME|RESULTADOS',path,re.I) and path.endswith('.md')],
                'text_candidates':[{'path':safe_path(path),'categories':blob_flags[oid]} for oid,path,n in entries if oid in blob_flags],
                'forbidden_txt':[safe_path(path) for oid,path,n in entries if Path(path).name.lower() in local_txt]}
out={'coverage':'all refs reachable; no reflogs/unreachable; pattern candidates require manual classification',
     'commits':len(commits),'refs':len(refs),'reachable_blobs':len(blobs),
     'historical_binary_or_large_blob_versions':sorted(binaries,key=lambda x:(x['path'],-x['bytes'])),
     'historical_text_blob_candidates':findings,'authors':author_rows,
     'committer_identity_pairs':len(set(committers)),
     'commit_message_candidates':{k:n for k,n in message_flags.items() if n},
     'historical_forbidden_txt':history_txt,'trees':trees}
target=Path(args.out)
target.parent.mkdir(parents=True,exist_ok=True)
target.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'commits':len(commits),'refs':len(refs),'blobs':len(blobs),
                  'binary_or_large_versions':len(binaries),'text_candidate_versions':len(findings),
                  'author_pairs':len(authors),'forbidden_txt_history':len(history_txt)}))
