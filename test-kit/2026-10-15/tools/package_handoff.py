#!/usr/bin/env python3
"""Create an explicit allowlist ZIP; no runtime, env, browser traces or credentials.
Set UAT_PASSWORD while packaging to scan/redact the locally used test credential.
"""
from pathlib import Path
import hashlib,json,os,re,zipfile
P=Path(__file__).resolve().parents[1]
OUT=P.parent/'MRP测试交接包-2026-10-15.zip'
secrets=[]
if (P/'.env').exists():
 for line in (P/'.env').read_text().splitlines():
  if '=' in line and not line.startswith('#'):secrets.append(line.split('=',1)[1].strip())
if os.environ.get('UAT_PASSWORD'):secrets.append(os.environ['UAT_PASSWORD'])
secrets=[v.encode() for v in secrets if len(v)>=8]
allowed={'.md','.yaml','.py','.mjs','.json','.jsonl','.csv','.gz','.xlsx','.png','.log'}
jwt=re.compile(rb'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+')
bearer=re.compile(rb'(?i)(Bearer\s+)[A-Za-z0-9._-]{20,}')
entries=[];manifest=[]
for file in sorted(P.rglob('*')):
 rel=file.relative_to(P)
 if not file.is_file() or any(x.startswith('.') or x in ['runtime','node_modules','__pycache__','e2e-results','playwright-report','private'] for x in rel.parts):continue
 if file.suffix not in allowed:continue
 if len(rel.parts)>1 and rel.parts[0] not in ['data','expected','tools','e2e','evidence']:continue
 if file.name.endswith('.inspect.ndjson'):continue
 if file.name=='package-verification.json':continue # external archive checksum cannot refer to itself
 raw=file.read_bytes()
 if file.suffix in ['.md','.py','.mjs','.json','.jsonl','.csv','.yaml','.log']:
  raw=re.sub(rb'Using generated security password:[^\r\n]*',b'Using generated security password: [REDACTED]',raw)
  raw=jwt.sub(b'[REDACTED_JWT]',raw);raw=bearer.sub(rb'\1[REDACTED]',raw)
  for secret in secrets:raw=raw.replace(secret,b'[REDACTED]')
 if jwt.search(raw) or bearer.search(raw) or any(x in raw for x in secrets):raise SystemExit('Secret scan rejected '+str(rel))
 entries.append((str(rel),raw));manifest.append({'path':str(rel),'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()})
index=json.dumps({'files':manifest,'excluded':['.env','runtime','raw browser traces/reports','private','dependency/cache dirs'],'secretScan':'PASS'},ensure_ascii=False,indent=2).encode()
with zipfile.ZipFile(OUT,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for name,raw in entries:z.writestr('2026-10-15/'+name,raw)
 z.writestr('2026-10-15/PACKAGE-MANIFEST.json',index)
with zipfile.ZipFile(OUT) as z:
 if z.testzip():raise SystemExit('ZIP integrity failed')
report={'status':'PASS','archive':str(OUT),'files':len(entries)+1,'bytes':OUT.stat().st_size,'sha256':hashlib.sha256(OUT.read_bytes()).hexdigest(),'secretScan':'PASS','traceExclusion':'PASS'}
(P/'evidence/package-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False))
