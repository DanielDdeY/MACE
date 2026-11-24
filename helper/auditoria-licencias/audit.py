"""Auditoria estatica. Solo lee entradas; escribe informes saneados en native/build.

No ejecuta binarios, no restaura paquetes, no modifica imagen/registro/entorno.
Python >= 3.10, biblioteca estandar. --refresh-official solo GET de fuentes publicas.
"""
from __future__ import annotations
import argparse
import collections
import concurrent.futures
import csv
import datetime as dt
import html
import io
import json
import os
from pathlib import Path
import random
import re
import struct
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
import hashlib

ROOT = Path(__file__).resolve().parents[2]
IMAGE = ROOT / 'packaging/dist/MACE'
REPORT = ROOT / 'helper/FASE3-LICENCIA-INVENTARIO.md'
HERE = Path(__file__).resolve().parent
TODAY = dt.datetime.now(dt.timezone(dt.timedelta(hours=-5))).date().isoformat()
SPDX = 'https://raw.githubusercontent.com/spdx/license-list-data/main/json/licenses.json'
EXCEPTIONS = 'https://raw.githubusercontent.com/spdx/license-list-data/main/json/exceptions.json'
OSI = 'https://opensource.org/licenses'
HOSTS = {'raw.githubusercontent.com', 'github.com', 'spdx.org', 'opensource.org',
         'www.opensource.org', 'api.opensource.org', 'jrsoftware.org', 'www.jrsoftware.org',
         'nsis.sourceforge.io', 'www.oracle.com', 'adoptium.net', 'signpath.org',
         'learn.microsoft.com', 'go.microsoft.com', 'www.nuget.org', 'pawnio.eu'}

def read(p):
    return Path(p).read_text(encoding='utf-8-sig', errors='replace')

def write_json(p, obj):
    p.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def csv_out(out, name, rows, fields=None):
    if not fields:
        fields = list(rows[0]) if rows else ['estado']
    with (out / name).open('w', encoding='utf-8-sig', newline='') as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)

def digest(p):
    with Path(p).open('rb') as f:
        h = hashlib.sha256()
        for b in iter(lambda: f.read(1024 * 1024), b''):
            h.update(b)
    return h.digest()  # solo comparacion; nunca se serializa

def rel(p):
    return Path(p).relative_to(ROOT).as_posix()

def table_rows(text):
    rows, kind, header, section = [], None, [], 'principal'
    for line_no, line in enumerate(text.splitlines(), 1):
        if line.startswith('## Anexo II '):
            section = 'net'
        elif line.startswith('## Anexo I '):
            section = 'java'
        if not line.startswith('|'):
            continue
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        if cells[0] in ('Componente', 'Encabezado del aviso'):
            header = cells
            kind = 'java' if 'Aviso E6' in cells else ('net' if cells[0].startswith('Encabezado') else 'principal')
            # Tras correccion, encabezado unificado; se identifica por seccion.
            kind = section
            continue
        if cells[0] not in ('---',) and any(x in cells[0] for x in ('Comprobación nueva', 'Grupo disjunto')):
            header=[]
        if not kind or not header or re.fullmatch(r'[-: ]+', cells[0]):
            continue
        if len(cells) != len(header):
            continue
        if header[0] not in ('Componente', 'Encabezado del aviso'):
            continue
        # Tablas no componente intercaladas terminan el estado.
        if len(cells) not in (4, 5, 8, 11):
            continue
        if len(header) == 11:
            lic, osi, evidence = cells[2], cells[3], cells[5]
        elif kind == 'principal':
            lic, osi, evidence = cells[2], '', cells[4]
        elif kind == 'java':
            lic, osi, evidence = cells[3], '', cells[2]
        else:
            lic, osi, evidence = cells[2], '', 'E5, THIRD-PARTY-NOTICES'
        rows.append(dict(id=f'R{len(rows)+1:03}', componente=cells[0], version=cells[1],
                         licencia=lic, osi_informe=osi, evidencia_informe=evidence,
                         seccion=kind, linea=line_no, celdas=cells, columnas=len(header)))
        # Evitar que otras tablas del documento se consideren componentes.
        # El siguiente encabezado distinto resetea header abajo en parser exterior.
    return rows

def pe_version(data):
    result = {}
    for key in ('FileVersion', 'ProductVersion', 'CompanyName', 'ProductName'):
        needle = (key + '\0').encode('utf-16-le')
        start = 0
        while True:
            ix = data.find(needle, start)
            if ix < 0:
                break
            start = ix + len(needle)
            if ix < 6:
                continue
            length, size, typ = struct.unpack_from('<HHH', data, ix - 6)
            at = (ix + len(needle) + 3) & ~3
            if typ == 1 and 0 < size < 1024 and at + size * 2 <= len(data):
                value = data[at:at+size*2].decode('utf-16-le', errors='replace').rstrip('\0')
                if value and all(ord(c) >= 32 for c in value):
                    # Metadatos pueden contener revision completa; se sanea.
                    result[key] = re.sub(r'\b[a-fA-F0-9]{40,64}\b', '[revision omitida]', value)
                    break
    return result

def xml_values(p):
    root = ET.fromstring(read(p))
    return {tag: [(e.text or '').strip() for e in root.iter() if e.tag.split('}')[-1] == tag]
            for tag in ('id', 'version', 'license', 'licenseUrl', 'requireLicenseAcceptance')}

def primary_local():
    """Se captura antes de leer el inventario: no deriva licencias del informe."""
    nu = Path.home() / '.nuget/packages'
    packages = {}
    versions = {'librehardwaremonitorlib': '0.9.6', 'ramspdtoolkit-ndd': '1.4.2',
                'hidsharp': '2.6.4', 'blacksharp.core': '1.0.7', 'diskinfotoolkit': '1.1.2',
                'mono.posix.netstandard': '1.0.0', 'microsoft.netcore.app.runtime.win-x64': '10.0.12',
                'system.codedom': '10.0.2', 'system.io.ports': '10.0.3', 'system.management': '10.0.2'}
    for name, version in versions.items():
        p = nu / name / version
        nuspec = p / (name + '.nuspec')
        meta = xml_values(nuspec) if nuspec.exists() else {}
        licenses = [x.name for x in p.glob('*') if x.is_file() and re.match(r'(?i)^(LICENSE|COPYING)', x.name)]
        packages[name] = dict(version=version, metadatos=meta, textos=licenses,
                              evidencia=f'<cacheNuGet>/{name}/{version}/{name}.nuspec')
        if licenses:
            body=read(p/licenses[0])
            packages[name]['titulo_apache_2']='Apache License' in body and 'Version 2.0' in body
            packages[name]['titulo_mit']='MIT License' in body or 'The MIT License' in body
    pe = {p.name: pe_version(p.read_bytes()) for p in IMAGE.rglob('*') if p.suffix.lower() in ('.dll', '.exe')}
    jars, natives = [], []
    for p in sorted((IMAGE / 'app').glob('*.jar')):
        with zipfile.ZipFile(p) as z:
            entries = z.namelist()
            versions={}
            for n in entries:
                if n.endswith('pom.properties'):
                    props=z.read(n).decode(errors='replace')
                    m=re.search(r'^version=(.+)$',props,re.M)
                    if m: versions['pom.properties']=m.group(1).strip()
            cache_jar=None
            m=re.fullmatch(r'javafx-(base|controls|fxml|graphics)-22\.0\.2(-win)?\.jar',p.name)
            if m:
                cache_jar=Path.home()/'.m2/repository/org/openjfx'/('javafx-'+m.group(1))/'22.0.2'/p.name
            elif p.name=='atlantafx-base-2.0.1.jar':
                cache_jar=Path.home()/'.m2/repository/io/github/mkpaz/atlantafx-base/2.0.1'/p.name
            match_cache=(digest(p)==digest(cache_jar)) if cache_jar and cache_jar.exists() else None
            jars.append(dict(archivo=rel(p), entradas=len(entries),versiones_embebidas=versions,
                             coincide_cache_maven=match_cache,
                             legales=len([n for n in entries if re.search(r'(?i)(^|/)(LICENSE|NOTICE|COPYING|legal)(/|\.|$)', n)]),
                             firmas=len([n for n in entries if re.search(r'(?i)^META-INF/[^/]+\.(SF|RSA|DSA)$', n)])))
            for n in entries:
                if n.lower().endswith('.dll'):
                    natives.append(dict(jar=p.name, archivo=n, **pe_version(z.read(n))))
    legal = {}
    for p in sorted((IMAGE / 'runtime/legal').rglob('*.md')):
        if p.name not in legal:
            txt = read(p)
            legal[p.name] = dict(archivo=rel(p), titulo=txt.splitlines()[0],
                                texto_disponible=True)
    return dict(paquetes=packages, pe=pe, jars=jars, nativos_jar=natives, legales_java=legal)

class OfficialRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if urllib.parse.urlsplit(newurl).hostname not in HOSTS:
            raise urllib.error.HTTPError(req.full_url, code, 'Redirect no permitido', headers, fp)
        return super().redirect_request(req, fp, code, msg, headers, newurl)

def fetch(url):
    if urllib.parse.urlsplit(url).hostname not in HOSTS:
        return None, dict(url=url, estado='NO VERIFICADO', motivo='Host fuera de lista oficial', fecha=TODAY)
    try:
        opener = urllib.request.build_opener(OfficialRedirect)
        req = urllib.request.Request(url, headers={'User-Agent': 'MACE-license-read-only-audit/1.0'})
        with opener.open(req, timeout=30) as r:
            text = r.read().decode('utf-8', errors='replace')
            return text, dict(url=url, final=r.url, estado='CONSULTADA', http=r.status, fecha=TODAY,
                              consultado_utc=dt.datetime.now(dt.timezone.utc).isoformat())
    except Exception as e:
        return None, dict(url=url, estado='NO VERIFICADO', motivo=type(e).__name__, fecha=TODAY)

def get_official(report_text, refresh, out):
    snapshot = HERE / 'fuentes-oficiales.json'
    if not refresh:
        if snapshot.exists():
            return json.loads(read(snapshot))
        return dict(spdx=[], excepciones=[], osi_ids=[], urls=[], muestras={})
    urls = sorted(set(re.findall(r'\]\((https?://[^)]+)\)', report_text)) | {
        SPDX, EXCEPTIONS, OSI, 'https://api.opensource.org/licenses/',
        'https://raw.githubusercontent.com/jrsoftware/issrc/main/license.txt',
        'https://www.nuget.org/packages/Mono.Posix.NETStandard/1.0.0',
        'https://go.microsoft.com/fwlink/?linkid=869050',
        'https://raw.githubusercontent.com/LibreHardwareMonitor/LibreHardwareMonitor/v0.9.6/LICENSE',
        'https://github.com/Blacktempel/RAMSPDToolkit/blob/3b47b96/LICENSE',
        'https://raw.githubusercontent.com/Blacktempel/RAMSPDToolkit/3b47b96/LICENSE',
        'https://raw.githubusercontent.com/mono/mono/main/LICENSE',
    })
    records, texts = [], {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        responses = list(pool.map(fetch, urls))
    for url, (content, record) in zip(urls, responses):
        host = urllib.parse.urlsplit(url).hostname
        record['autoridad'] = ('REGISTRO_DE_PAQUETES' if host=='www.nuget.org' else
            ('LICENCIAS' if host in ('spdx.org','opensource.org','api.opensource.org','raw.githubusercontent.com') and ('spdx' in url or 'opensource.org' in url) else 'TITULAR_O_PROYECTO'))
        records.append(record)
        if content:
            texts[url] = content
    spdx_data = json.loads(texts[SPDX]) if SPDX in texts else {}
    exceptions = json.loads(texts[EXCEPTIONS]).get('exceptions', []) if EXCEPTIONS in texts else []
    osi_ids = re.findall(r'<td[^>]*class="license-table--spdx"[^>]*>\s*([^<]+)', texts.get(OSI,''))
    osi_ids = [html.unescape(x.strip()) for x in osi_ids if re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9.+-]*',x.strip())]
    spdx_rows = [dict(id=x['licenseId'], osi=bool(x.get('isOsiApproved')), deprecated=bool(x.get('isDeprecatedLicenseId')))
                 for x in spdx_data.get('licenses',[])]
    # Solo fragmentos de evidencia no literales: evita copiar licencias o datos de terceros en logs.
    evidence = {}
    for url, txt in texts.items():
        if url in (SPDX, EXCEPTIONS, OSI):
            continue
        if 'issrc/main/license.txt' in url:
            evidence['inno'] = dict(licencia='InnoSetup' if 'Inno Setup License' in txt else 'NOASSERTION', url=url)
        if 'PawnIO/master/README' in url:
            evidence['pawnio_driver_actual'] = dict(licencia='GPL-2.0-or-later' if 'either version 2' in txt else 'NOASSERTION',
                                                    excepcion_ioctl='device IO control' in txt,
                                                    alternativa='alternative licensing' in txt, url=url,
                                                    correspondencia_setup='NO VERIFICADO')
        if 'PawnIO.Modules/main/IntelMSR' in url:
            m = re.search(r'SPDX-License-Identifier:\s*([^\r\n]+)',txt)
            evidence['pawnio_modulo_actual'] = dict(licencia=m.group(1).strip() if m else 'NOASSERTION',url=url,
                                                    correspondencia_binarios='NO VERIFICADO')
        if '/v0.9.6/LICENSE' in url:
            evidence['lhm'] = dict(licencia='MPL-2.0' if 'Mozilla Public License' in txt and '2.0' in txt else 'NOASSERTION',url=url)
        if '/RAMSPDToolkit/' in url:
            evidence['ndd'] = dict(licencia='MPL-2.0' if 'Mozilla Public License' in txt and '2.0' in txt else 'NOASSERTION',url=url)
        if 'fwlink/?linkid=869050' in url:
            evidence['mono_enlace'] = dict(destino=next(x['final'] for x in records if x['url']==url),
                                           correspondencia_paquete_1_0_0='NO VERIFICADO')
    result = dict(fecha=TODAY, spdx_version=spdx_data.get('licenseListVersion','NO VERIFICADO'), spdx=spdx_rows,
                  excepciones=[x['licenseExceptionId'] for x in exceptions],
                  osi_ids=sorted(set(osi_ids)), urls=records, muestras=evidence)
    write_json(out / 'fuentes-oficiales-observadas.json', result)
    return result

def component_for(path, deps):
    """Asignacion cerrada: no hay comodin universal para archivos nuevos."""
    p = Path(path)
    name = p.name
    if path.startswith('app/lhm/'):
        if name == 'Mace.Lhm.Helper.exe':
            return 'Mace.Lhm.Helper.exe y DLL propia', 'APPHOST_PROPIO'
        if name == 'helper-runtime.manifest':
            return 'Mace.Lhm.Helper.exe y DLL propia', 'METADATO_PROPIO'
        if name in ('Mace.Lhm.Helper.deps.json','Mace.Lhm.Helper.runtimeconfig.json'):
            return 'Mace.Lhm.Helper.exe y DLL propia', 'CONFIGURACION_PROPIA'
        return deps.get(name, ('SIN_COMPONENTE','NO_CATALOGADO'))
    if path in ('MACE.exe','MACE-Preview.exe') or name in ('MACE.cfg','MACE-Preview.cfg','.jpackage.xml'):
        return 'MACE.exe y MACE-Preview.exe', 'ARTEFACTO_O_CONFIGURACION'
    if path == 'app/mace_native.dll':
        return 'mace_native.dll', 'BINARIO'
    if path in ('app/runtime-manifest.json','app/THIRD-PARTY-NOTICES.txt'):
        return 'Aplicación Java MACE', 'METADATO_O_AVISO_AGREGADO'
    if name == 'mace-app-1.0.0-SNAPSHOT.jar':
        return 'Aplicación Java MACE', 'JAR'
    if name == 'atlantafx-base-2.0.1.jar':
        return 'AtlantaFX base', 'JAR'
    if re.fullmatch(r'javafx-(base|controls|fxml|graphics)-22\.0\.2(-win)?\.jar',name):
        return 'OpenJFX base, controls, fxml, graphics', 'JAR'
    if path == 'app/prerequisites/PawnIO_setup.exe':
        return 'PawnIO_setup.exe', 'BINARIO_INTACTO'
    if path.startswith('app/third-party/'):
        mapping={'HidSharp-LICENSE.txt':'HidSharp','Intel-IGCL-LICENSE.txt':'Cabecera Intel IGCL',
                 'System.CodeDom-THIRD-PARTY-NOTICES.TXT':'System.CodeDom',
                 'System.IO.Ports-THIRD-PARTY-NOTICES.TXT':'System.IO.Ports',
                 'System.Management-THIRD-PARTY-NOTICES.TXT':'System.Management'}
        if name.startswith('Microsoft.NETCore.App.Runtime.win-x64-'):
            return 'Microsoft.NETCore.App.Runtime.win-x64','AVISO'
        if name in mapping:
            return mapping[name],'AVISO'
    if path.startswith('runtime/'):
        if path.startswith('runtime/legal/'):
            return 'Subcomponentes declarados por Java','AVISO' if name.endswith('.md') else 'LEGAL_RUNTIME'
        if name in ('msvcp140.dll','vcruntime140.dll','vcruntime140_1.dll'):
            return 'Microsoft VC runtime en runtime/bin','BINARIO'
        return 'Runtime jlink Oracle Java','DISTRIBUCION_JLINK'
    return 'SIN_COMPONENTE','NO_CATALOGADO'

def coverage(out, rows):
    deps_json = json.loads(read(IMAGE/'app/lhm/Mace.Lhm.Helper.deps.json'))
    deps = {}
    for target in deps_json['targets'].values():
        for library, assets in target.items():
            lib = library.rsplit('/',1)[0]
            if lib == 'runtimepack.Microsoft.NETCore.App.Runtime.win-x64':
                lib = 'Microsoft.NETCore.App.Runtime.win-x64'
            if lib == 'Mace.Lhm.Helper': lib='Mace.Lhm.Helper.exe y DLL propia'
            if lib == 'Mace.Lhm.Protocol': lib='Mace.Lhm.Protocol.dll'
            if lib == 'Mono.Posix.NETStandard': lib='Mono.Posix.NETStandard.dll'
            for kind in ('runtime','native','resources'):
                for asset in assets.get(kind,{}):
                    name=Path(asset).name
                    component=lib
                    if name in ('MonoPosixHelper.dll','libMonoPosixHelper.dll'):
                        component='MonoPosixHelper.dll y libMonoPosixHelper.dll'
                    deps[name]=(component,library)
    known={r['componente']:r['id'] for r in rows}
    files=[]
    for p in sorted(IMAGE.rglob('*')):
        if not p.is_file(): continue
        path=p.relative_to(IMAGE).as_posix()
        component,reason=component_for(path,deps)
        if component not in known: component='SIN_COMPONENTE'
        files.append(dict(archivo=path,componente=component,fila=known.get(component,''),motivo=reason,bytes=p.stat().st_size))
    csv_out(out,'cobertura-imagen.csv',files)
    manifest=[]
    for line in read(IMAGE/'app/lhm/helper-runtime.manifest').splitlines()[1:]:
        h,n=line.split('  ',1)
        p=IMAGE/'app/lhm'/n
        c,why=component_for('app/lhm/'+n,deps)
        manifest.append(dict(archivo=n,componente=c,fila=known.get(c,''),coincide=p.is_file() and digest(p).hex()==h))
    csv_out(out,'cobertura-helper-202.csv',manifest)
    # Published manifest 205: localizar por conjunto y comparar bytes, sin divulgar nombre de staging.
    published=[]
    expected_names={x['archivo'] for x in manifest}
    for p in sorted((ROOT/'helper/artifacts').glob('*/artifact-manifest.json')):
        data=json.loads(read(p)); entries=data.get('files',[])
        if len(entries)!=205 or not expected_names.issubset({x.get('file',x.get('path',x.get('name',''))) for x in entries}):
            continue
        original={line.split('  ',1)[1]:line.split('  ',1)[0].lower() for line in read(IMAGE/'app/lhm/helper-runtime.manifest').splitlines()[1:]}
        declared={x.get('file',x.get('path',x.get('name',''))):x.get('sha256',x.get('hash','')).lower() for x in entries}
        if any(declared.get(n)!=h for n,h in original.items()):
            continue
        for item in entries:
            name=item.get('file',item.get('path',item.get('name','')))
            component,why=component_for('app/lhm/'+name,deps)
            if name=='createdump.exe': component='Microsoft.NETCore.App.Runtime.win-x64'
            if name=='Mace.Lhm.Helper.pdb': component='Mace.Lhm.Helper.exe y DLL propia'
            if name=='Mace.Lhm.Protocol.pdb': component='Mace.Lhm.Protocol.dll'
            f=p.parent/name
            expected=item.get('sha256',item.get('hash',''))
            published.append(dict(archivo=name,componente=component,fila=known.get(component,''),
                                  viaja_imagen=name in expected_names,
                                  coincide_manifiesto=f.is_file() and digest(f).hex().lower()==expected.lower()))
        break
    csv_out(out,'cobertura-publicado-205.csv',published)
    image_manifest=[]
    declared_names=set()
    for line in read(ROOT/'packaging/dist/MACE-SHA256SUMS.txt').splitlines():
        h,name=line.split('  ',1)
        declared_names.add(name.removeprefix('MACE/'))
        f=ROOT/'packaging/dist'/name
        image_manifest.append(f.is_file() and digest(f).hex().lower()==h.lower())
    modules=re.search(r'MODULES="([^"]+)"',read(IMAGE/'runtime/release')).group(1).split()
    csv_out(out,'modulos-jlink.csv',[dict(modulo=m,componente='Runtime jlink Oracle Java',
                                        directorio_legal=(IMAGE/'runtime/legal'/m).is_dir()) for m in modules])
    return dict(archivos_imagen=len(files),bytes_imagen=sum(x['bytes'] for x in files),
                sin_componente=sum(x['componente']=='SIN_COMPONENTE' for x in files),
                helper202=len(manifest),helper202_correctos=sum(x['coincide'] for x in manifest),
                publicado205=len(published),publicado205_correctos=sum(x['coincide_manifiesto'] for x in published),
                publicado205_sin_componente=sum(not x['fila'] for x in published),
                imagen_pins_correctos=sum(image_manifest),imagen_pins_total=len(image_manifest),
                imagen_extras=len({x['archivo'] for x in files}-declared_names),modulos=len(modules))

def source_for(row, primary, official):
    n=row['componente']; kind=row['seccion']
    if kind=='java':
        p=row['celdas'][2] if row['columnas']==5 else row['evidencia_informe']
        match=re.search(r'\b([a-z0-9_-]+\.md)\b',p)
        if match and match.group(1) in primary['legales_java']:
            return 'ARCHIVO_LOCAL',primary['legales_java'][match.group(1)]['archivo'],'TEXTO; CORRESPONDENCIA_BINARIA_NV'
        return 'SIN_EVIDENCIA','','NO VERIFICADO'
    if kind=='net':
        return 'ARCHIVO_LOCAL','packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT','TEXTO_AGREGADO; VERSION_INDIVIDUAL_NV'
    key={'LibreHardwareMonitorLib':'librehardwaremonitorlib','RAMSPDToolkit-NDD':'ramspdtoolkit-ndd',
         'HidSharp':'hidsharp','BlackSharp.Core':'blacksharp.core','DiskInfoToolkit':'diskinfotoolkit',
         'Microsoft.NETCore.App.Runtime.win-x64':'microsoft.netcore.app.runtime.win-x64',
         'System.CodeDom':'system.codedom','System.IO.Ports':'system.io.ports','System.Management':'system.management'}.get(n)
    if key:
        p=primary['paquetes'][key]
        if p['textos']:
            return 'ARCHIVO_LOCAL',p['evidencia'].rsplit('/',1)[0]+'/'+p['textos'][0],'LICENCIA_PAQUETE; SUBCOMPONENTES_NV'
        if p['metadatos'].get('license'):
            return 'METADATO_PAQUETE',p['evidencia'],'DECLARACION_PAQUETE'
        return 'SIN_EVIDENCIA','','NO VERIFICADO'
    if n in ('Runtime jlink Oracle Java','Subcomponentes declarados por Java','Cabecera Intel IGCL'):
        p={'Runtime jlink Oracle Java':'packaging/dist/MACE/runtime/legal/java.base/LICENSE',
           'Subcomponentes declarados por Java':'packaging/dist/MACE/runtime/legal/',
           'Cabecera Intel IGCL':'native/vendor_headers/intel_igcl/LICENSE'}[n]
        return 'ARCHIVO_LOCAL',p,'TEXTO; ALCANCE SEGUN COMPONENTE'
    if n.startswith('OpenJFX base'):
        pom=Path.home()/'.m2/repository/org/openjfx/javafx/22.0.2/javafx-22.0.2.pom'
        if pom.exists() and 'GPLv2+CE' in read(pom):
            return 'METADATO_PAQUETE','<cacheMaven>/org/openjfx/javafx/22.0.2/javafx-22.0.2.pom','DECLARACION; NATIVOS AJENOS NO CUBIERTOS'
    urls={
        'Inno Setup, motor, loader y desinstalador futuros':'https://raw.githubusercontent.com/jrsoftware/issrc/main/license.txt',
        'NSIS como alternativa, no elegida':'https://nsis.sourceforge.io/License',
        'AtlantaFX base':'https://raw.githubusercontent.com/mkpaz/atlantafx/v2.0.1/LICENSE',
        'Driver PawnIO incluido por setup':'https://raw.githubusercontent.com/namazso/PawnIO/master/README.md',
        'Módulos PawnIO embebidos en LHM':'https://raw.githubusercontent.com/namazso/PawnIO.Modules/main/IntelMSR.p',
    }
    if n in urls:
        ok=any(x['url']==urls[n] and x['estado']=='CONSULTADA' for x in official['urls'])
        return ('URL_OFICIAL' if ok else 'SIN_EVIDENCIA'),urls[n],('FUENTE_ACTUAL; CORRESPONDENCIA_NV' if 'PawnIO' in n else ('TEXTO_OFICIAL' if ok else 'NO VERIFICADO'))
    return 'SIN_EVIDENCIA','','NO VERIFICADO; DECISION O METADATO TECNICO NO ES LICENCIA'

def evidence(out, rows, primary, official):
    result=[]
    for r in rows:
        category,src,scope=source_for(r,primary,official)
        result.append(dict(fila=r['id'],componente=r['componente'],categoria=category,fuente=src,
                           alcance=scope,licencia_declarada=r['licencia'],
                           doble_comercial='NO VERIFICADO',
                           comprobacion_doble=('Fuente oficial ofrece licencias alternativas; modalidad comercial exacta NV' if r['componente']=='Driver PawnIO incluido por setup' else
                             ('Sin texto o contrato acreditado para esta fila' if category=='SIN_EVIDENCIA' else
                              'Texto/metadato indicado revisado; no acredita inexistencia de contratos alternativos'))))
    csv_out(out,'evidencia-por-fila.csv',result)
    csv_out(out,'doble-licencia-comercial.csv',[{k:x[k] for k in ('fila','componente','fuente','doble_comercial','comprobacion_doble')} for x in result])
    # Uno por fila, no porcentajes ambiguos por numero de ficheros.
    return result,dict(collections.Counter(x['categoria'] for x in result))

def license_status(out, rows, official):
    ids={x['id']:x for x in official['spdx']}
    ex=set(official.get('excepciones',[])); osi=set(official.get('osi_ids',[]))
    aliases={'GPL-2.0-only':'GPL-2.0','GPL-2.0-or-later':'GPL-2.0',
             'GPL-3.0-only':'GPL-3.0','GPL-3.0-or-later':'GPL-3.0',
             'LGPL-2.1-or-later':'LGPL-2.1','LGPL-2.1-only':'LGPL-2.1'}
    prose_ids=re.findall(r'\b(?:GPL|LGPL)-[0-9]+\.[0-9]+(?:-only|-or-later)?\b',read(REPORT))
    all_text='\n'.join(x['licencia'] for x in rows)+'\n'+'\n'.join(prose_ids)
    tokens=set(re.findall(r'(?<![\w-])[A-Za-z][A-Za-z0-9.+-]*',all_text))
    wanted=sorted((tokens & set(ids)) | (tokens & ex) | {x for x in tokens if x.startswith('LicenseRef-') and len(x)>len('LicenseRef-')})
    results=[]
    for name in wanted:
        if name.startswith('LicenseRef-'):
            record=dict(identificador=name,spdx='PERSONALIZADO',spdx_osi='NO APLICA',osi_lista='NO APLICA',alias='',fecha=TODAY)
        elif name in ex:
            record=dict(identificador=name,spdx='EXCEPCION',spdx_osi='NO APLICA',osi_lista='NO APLICA',alias='',fecha=TODAY)
        else:
            matched=name if name in osi else aliases.get(name,'')
            record=dict(identificador=name,spdx='DEPRECADO' if ids[name]['deprecated'] else 'ID_OFICIAL',
                        spdx_osi=ids[name]['osi'],osi_lista='LISTADO' if matched and matched in osi else ('NO_LISTADO' if osi else 'NO VERIFICADO'),
                        alias=matched if matched!=name else '',fecha=TODAY)
        results.append(record)
    csv_out(out,'spdx-osi.csv',results)
    csv_out(out,'identificador-ausente.csv',[dict(fila=x['id'],componente=x['componente'],motivo='NOASSERTION o identificacion no estandar') for x in rows if 'NOASSERTION' in x['licencia'] or not any(k in x['licencia'] for k in ids)])
    return dict(identificadores=len(results),spdx_licencias=len(ids),osi_listadas=len(osi),
                personalizados=sum(x['spdx']=='PERSONALIZADO' for x in results))

def version_checks(out, rows, primary):
    # Declaraciones de version comparadas con fuentes distintas al texto redactado.
    checks=[]
    names={r['componente']:r for r in rows}
    for name,key in [('LibreHardwareMonitorLib','librehardwaremonitorlib'),('RAMSPDToolkit-NDD','ramspdtoolkit-ndd'),
                     ('HidSharp','hidsharp'),('BlackSharp.Core','blacksharp.core'),('DiskInfoToolkit','diskinfotoolkit'),
                     ('Microsoft.NETCore.App.Runtime.win-x64','microsoft.netcore.app.runtime.win-x64'),
                     ('Mono.Posix.NETStandard.dll','mono.posix.netstandard'),
                     ('MonoPosixHelper.dll y libMonoPosixHelper.dll','mono.posix.netstandard'),
                     ('System.CodeDom','system.codedom'),('System.IO.Ports','system.io.ports'),('System.Management','system.management')]:
        actual=primary['paquetes'][key]['metadatos'].get('version',['NO VERIFICADO'])[0]
        r=names[name]
        checks.append(dict(componente=name,informe=r['version'],observado=actual,resultado='PASS' if actual in r['version'] else 'FAIL',fuente=primary['paquetes'][key]['evidencia']))
    for name,file,field in [('MACE.exe y MACE-Preview.exe','MACE.exe','ProductVersion'),
                            ('mace_native.dll','mace_native.dll','FileVersion'),
                            ('Mace.Lhm.Helper.exe y DLL propia','Mace.Lhm.Helper.exe','FileVersion'),
                            ('Mace.Lhm.Protocol.dll','Mace.Lhm.Protocol.dll','FileVersion'),
                            ('PawnIO_setup.exe','PawnIO_setup.exe','FileVersion')]:
        actual=primary['pe'].get(file,{}).get(field,'NO VERIFICADO')
        r=names[name]; checks.append(dict(componente=name,informe=r['version'],observado=actual,
                                         resultado='PASS' if actual in r['version'] else 'FAIL',fuente='PE '+file+' '+field))
    for name,expected in [('OpenJFX base, controls, fxml, graphics','22.0.2'),('AtlantaFX base','2.0.1'),('Aplicación Java MACE','1.0.0-SNAPSHOT')]:
        sources=[]
        if name.startswith('OpenJFX'):
            pom=Path.home()/'.m2/repository/org/openjfx/javafx/22.0.2/javafx-22.0.2.pom'
            if pom.exists(): sources=xml_values(pom).get('version',[])
        else:
            fn='atlantafx-base-2.0.1.jar' if name=='AtlantaFX base' else 'mace-app-1.0.0-SNAPSHOT.jar'
            sources=[v for j in primary['jars'] if j['archivo'].endswith('/'+fn) for v in j['versiones_embebidas'].values()]
        checks.append(dict(componente=name,informe=names[name]['version'],observado=';'.join(sources),
                           resultado='PASS' if expected in sources else 'NO VERIFICADO',fuente='POM padre o pom.properties del JAR'))
    for name,predicate,field in [
        ('Nativos OpenJFX dentro de graphics-win',lambda n:not n.startswith(('api-ms-','msvcp','vcruntime','ucrtbase')),'22.0.2'),
        ('Microsoft VC runtime dentro de JavaFX',lambda n:n.startswith(('msvcp','vcruntime')),'14.36.32532.0'),
        ('Microsoft UCRT/API sets dentro de JavaFX',lambda n:n.startswith(('api-ms-','ucrtbase')),'10.0.17763.132')]:
        selected=[x for x in primary['nativos_jar'] if predicate(Path(x['archivo']).name)]
        ok=all(x.get('FileVersion','').startswith(field) for x in selected) and bool(selected)
        checks.append(dict(componente=name,informe=names[name]['version'],observado=f'{len(selected)} PE; {field}',
                           resultado='PASS' if ok else 'FAIL',fuente='VERSIONINFO leido directamente del ZIP'))
    vc_files=['msvcp140.dll','vcruntime140.dll','vcruntime140_1.dll']
    actuals=[primary['pe'].get(n,{}).get('FileVersion','NO VERIFICADO') for n in vc_files]
    name='Microsoft VC runtime en runtime/bin'
    checks.append(dict(componente=name,informe=names[name]['version'],observado=';'.join(actuals),
                       resultado='PASS' if all(x=='14.36.32532.0' for x in actuals) else 'FAIL',
                       fuente='VERSIONINFO de tres DLL, no del nombre del archivo'))
    actual=re.search(r'JAVA_VERSION="([^"]+)"',read(IMAGE/'runtime/release')).group(1)
    r=names['Runtime jlink Oracle Java']; checks.append(dict(componente=r['componente'],informe=r['version'],observado=actual,
                                      resultado='PASS' if actual in r['version'] else 'FAIL',fuente='runtime/release'))
    for r in rows:
        if r['seccion']=='java':
            cat,src,_=source_for(r,primary,{'urls':[]})
            title=read(ROOT/src).splitlines()[0] if cat=='ARCHIVO_LOCAL' else ''
            ver=r['version'].replace('Version ','')
            state='NO VERIFICADO' if ver=='No declarada' else ('PASS' if ver in title else 'FAIL')
            checks.append(dict(componente=r['componente'],informe=ver,observado=title,resultado=state,fuente=src))
    csv_out(out,'versiones.csv',checks)
    tested={x['componente'] for x in checks}
    csv_out(out,'versiones-no-acreditadas.csv',[dict(componente=r['componente'],version=r['version'],resultado='NO VERIFICADO') for r in rows if r['componente'] not in tested])
    return dict(versiones_comprobadas=sum(x['resultado']=='PASS' for x in checks),
                discrepancias_version=sum(x['resultado']=='FAIL' for x in checks),
                versiones_nv=sum(x['resultado']=='NO VERIFICADO' for x in checks)+sum(r['componente'] not in tested for r in rows))

def language_privacy(out):
    txt=read(REPORT)
    words=r'\b(probablemente|creo|suele|deber[ií]a|parece|seg[uú]n recuerdo)\b'
    matches=[dict(linea=txt[:m.start()].count('\n')+1,expresion=m.group()) for m in re.finditer(words,txt,re.I)]
    csv_out(out,'lenguaje.csv',matches,['linea','expresion'])
    patterns={
        'ruta_perfil':r'(?i)[A-Z]:[\\/]Users[\\/][^\s<>]+',
        'sid':r'\bS-1-5-21-[0-9-]+\b',
        'hash_completo':r'(?i)(?<![a-f0-9])[a-f0-9]{40}(?:[a-f0-9]{24})?(?![a-f0-9])',
        'correo':r'(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}',
    }
    # Se buscan valores reales en memoria sin serializarlos. Alias publico no se extrae del host.
    for key in ('USERNAME','COMPUTERNAME'):
        value=os.environ.get(key,'')
        if len(value)>3: patterns['identidad_'+key]=re.escape(value)
    findings=[]
    inputs=[REPORT]+[p for p in HERE.rglob('*') if p.is_file() and p.suffix in ('.py','.ps1','.json','.md')]
    for p in inputs:
        for name,pattern in patterns.items():
            count=len(re.findall(pattern,read(p)))
            if count: findings.append(dict(archivo=rel(p),tipo=name,coincidencias=count))
    csv_out(out,'datos-locales.csv',findings,['archivo','tipo','coincidencias'])
    return dict(lenguaje_no_verificado=len(matches),datos_locales=sum(x['coincidencias'] for x in findings),archivos_saneados=len(inputs))

def structure_and_static(out, rows):
    txt=read(REPORT)
    conflicts=[]
    for i in range(1,10):
        m=re.search(rf'^### C{i} .*?(?=^### C\d|^## |\Z)',txt,re.M|re.S)
        block=m.group() if m else ''
        flags={key:bool(re.search(pattern,block,re.I)) for key,pattern in {
            'opciones':r'opciones','recomendacion':r'recomendaci[oó]n',
            'riesgo':r'riesgo residual','decision':r'decisi[oó]n necesaria'}.items()}
        conflicts.append(dict(conflicto=f'C{i}',**flags))
    csv_out(out,'estructura-conflictos.csv',conflicts)
    patterns={
        'RED_HTTP_SOCKET':r'\b(HttpClient|WebClient|URLConnection|ServerSocket|Socket|WinHttp\w*|InternetOpen\w*|WSAStartup|curl_easy_\w*)\b',
        'PIPE_LOCAL':r'\b(NamedPipeClientStream|NamedPipeServerStream|CreateNamedPipeW|GetNamedPipeClientProcessId)\b',
        'FICHERO_ESCRITURA':r'\b(CREATE_NEW|FileWriter|createDirectories|deleteIfExists|writeHeader|channel\.write)\b',
        'REGISTRO_PREFERENCIAS':r'\b(Preferences|OpenSubKey|RegOpenKeyExW|RegSetValueExW)\b'}
    findings=[]
    for base in ('app/src/main/java','native/src','helper/Mace.Lhm.Helper','helper/Mace.Lhm.Protocol'):
        for path in sorted((ROOT/base).rglob('*')):
            if path.suffix not in ('.java','.cs','.c','.h'): continue
            for lineno,line in enumerate(read(path).splitlines(),1):
                for kind,pat in patterns.items():
                    if re.search(pat,line): findings.append(dict(archivo=rel(path),linea=lineno,categoria=kind))
    csv_out(out,'privacidad-estatica.csv',findings,['archivo','linea','categoria'])
    return dict(tablas_con_11_columnas=sum(r['columnas']==11 for r in rows),
                conflictos_completos=sum(all(x[k] for k in ('opciones','recomendacion','riesgo','decision')) for x in conflicts),
                referencias_red_propias=sum(x['categoria']=='RED_HTTP_SOCKET' for x in findings))

def sample(out,rows,primary,official):
    mandatory=['Inno Setup, motor, loader y desinstalador futuros','PawnIO_setup.exe',
               'Módulos PawnIO embebidos en LHM','Mono.Posix.NETStandard.dll','Runtime jlink Oracle Java',
               'OpenJFX base, controls, fxml, graphics','LibreHardwareMonitorLib','RAMSPDToolkit-NDD','HidSharp','Cabecera Intel IGCL']
    # Universo: otras filas de componente; orden estable y semilla publica, seleccion previa a cotejo.
    population=sorted(r['componente'] for r in rows if r['componente'] not in mandatory)
    extra=random.Random(20261123).sample(population,3)
    names={r['componente']:r for r in rows}
    result=[]
    for name in mandatory+extra:
        r=names[name];cat,src,scope=source_for(r,primary,official)
        derivation='NO VERIFICADO'
        if name=='HidSharp': derivation='Apache-2.0: LICENSE.txt local, titulo y cuerpo' if primary['paquetes']['hidsharp'].get('titulo_apache_2') else 'NO VERIFICADO'
        elif name=='LibreHardwareMonitorLib': derivation=str(primary['paquetes']['librehardwaremonitorlib']['metadatos'].get('license',['NO VERIFICADO'])[0])+': nuspec 0.9.6; contraste oficial registrado'
        elif name=='RAMSPDToolkit-NDD': derivation=str(primary['paquetes']['ramspdtoolkit-ndd']['metadatos'].get('license',['NO VERIFICADO'])[0])+': nuspec 1.4.2; contraste oficial registrado'
        elif name=='Runtime jlink Oracle Java': derivation='Oracle NFTC: texto real runtime/legal/java.base/LICENSE; version en release'
        elif name=='OpenJFX base, controls, fxml, graphics': derivation='GPLv2+CE: POM padre restaurado; no acredita todas las DLL de terceros'
        elif name=='Cabecera Intel IGCL': derivation='Intel Software License Agreement 10.07.21; restriccion de plataforma, texto local'
        elif name=='System.Management': derivation=str(primary['paquetes']['system.management']['metadatos'].get('license',['NO VERIFICADO'])[0])+': nuspec 10.0.2'
        elif name=='c-libutl': derivation='BSD-2-Clause: dos condiciones y exencion de responsabilidad del texto local'
        elif name=='musl': derivation='MIT para conjunto y porciones separadas; conjunto completo NO VERIFICADO'
        elif name=='Módulos PawnIO embebidos en LHM': derivation=str(official.get('muestras',{}).get('pawnio_modulo_actual',{}).get('licencia','NO VERIFICADO'))+': fuente actual; correspondencia con doce binarios NO VERIFICADO'
        elif name.startswith('Inno '): derivation=str(official.get('muestras',{}).get('inno',{}).get('licencia','NO VERIFICADO'))
        elif cat=='ARCHIVO_LOCAL': derivation='Texto local individual disponible; identificacion SPDX requiere cotejo de variante'
        result.append(dict(componente=name,seleccion='OBLIGATORIA' if name in mandatory else 'AZAR_SEMILLA_20261123',
                           derivacion_primaria=derivation,fuente=src,categoria=cat,alcance=scope,
                           comparacion='NO VERIFICADO' if 'NO VERIFICADO' in derivation else 'SIN CONTRADICCION EN EL ALCANCE INDICADO'))
    csv_out(out,'muestreo-13.csv',result)
    return extra

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--refresh-official',action='store_true')
    p.add_argument('--out',default='native/build/auditoria-licencias')
    args=p.parse_args()
    out=(ROOT/args.out).resolve()
    allowed=(ROOT/'native/build').resolve()
    if not out.is_relative_to(allowed) or out==allowed:
        raise ValueError('La salida debe ser una subcarpeta de native/build')
    out.mkdir(parents=True,exist_ok=True)
    # Fase 1 primaria sin leer el informe; fase 2 comparacion.
    primary=primary_local()
    write_json(out/'fuentes-locales.json',primary)
    txt=read(REPORT)
    rows=table_rows(txt)
    official=get_official(txt,args.refresh_official,out)
    evidence_rows,counts=evidence(out,rows,primary,official)
    csv_out(out,'urls.csv',official.get('urls',[]),['url','final','estado','http','fecha','consultado_utc','autoridad','motivo'])
    stats=dict(fecha=TODAY,filas=len(rows),filas_por_seccion=dict(collections.Counter(r['seccion'] for r in rows)),
               categorias=counts,**coverage(out,rows),**license_status(out,rows,official),**structure_and_static(out,rows),
               **version_checks(out,rows,primary),**language_privacy(out),
               jars=len(primary['jars']),dlls_en_jars=len(primary['nativos_jar']),
               filas_doble_comercial_nv=len(rows),
               urls_consultadas=sum(x['estado']=='CONSULTADA' for x in official.get('urls',[])),
               urls_nv=sum(x['estado']!='CONSULTADA' for x in official.get('urls',[])),
               modo_fuentes='CONSULTA_HTTP' if args.refresh_official else 'SNAPSHOT_CON_FECHA_ORIGINAL')
    stats['azar']=sample(out,rows,primary,official)
    write_json(out/'resultado.json',stats)
    # Solo cifras, no rutas locales ni errores con datos del host.
    print(json.dumps(stats,ensure_ascii=False))
    failures=(stats['sin_componente'] or stats['datos_locales'] or stats['discrepancias_version'] or
              stats['helper202_correctos']!=stats['helper202'] or stats['publicado205']!=205 or
              stats['publicado205_correctos']!=205 or stats['publicado205_sin_componente'] or
              stats['imagen_pins_correctos']!=stats['imagen_pins_total'] or stats['imagen_extras'])
    return 2 if failures else 0

if __name__=='__main__':
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(json.dumps(dict(estado='ERROR_COMPROBACION',tipo=type(exc).__name__)))
        raise SystemExit(1)
