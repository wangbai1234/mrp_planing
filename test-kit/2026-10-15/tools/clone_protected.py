#!/usr/bin/env python3
"""Copy read-only master inputs to fixed isolated destination; refuses occupied destination."""
import subprocess,json,pathlib,re,sys,hashlib,datetime
P=pathlib.Path(__file__).resolve().parents[1]
baseline=json.loads((P/'data/source-manifest.json').read_text());sid=int(baseline['snapshot']['id'])
def source(query):
 if not query.startswith(('SELECT','SHOW')):raise ValueError('Read-only source only')
 return subprocess.run(['docker','exec','-i','mrp-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 --raw -u"$MYSQL_USER" -N -B mrp'],input=query,text=True,capture_output=True,check=True).stdout
def dest(query):
 return subprocess.run(['docker','exec','-i','mrp-testkit-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 --raw -u"$MYSQL_USER" -N -B mrp_testkit'],input=query,text=True,capture_output=True,check=True).stdout
tables=[('material_category',None),('material',None),('bom_snapshot',f'id={sid}'),('bom_snapshot_detail',f'snapshot_id={sid}')]
def fingerprint(connection,table,where=None):
 cols=[r.split('\t')[0] for r in connection('SHOW COLUMNS FROM `'+table+'`;').splitlines()]
 expr=','.join("IFNULL(HEX(`"+c+"`),'NULL')" for c in cols)
 data=connection("SELECT CONCAT_WS('|',"+expr+") FROM `"+table+'`'+(' WHERE '+where if where else '')+' ORDER BY id;')
 return {'rows':len(data.splitlines()),'sha256':hashlib.sha256(data.encode()).hexdigest()}
meta=json.loads(source(f"SELECT JSON_OBJECT('id',id,'status',status,'checksum',checksum) FROM bom_snapshot WHERE id={sid};"))
if meta!=baseline['snapshot']:raise SystemExit('BLOCKED: source snapshot differs from fixture baseline')
sourceprints={t:fingerprint(source,t,w) for t,w in tables}
for t in ['material','material_category']:
 if sourceprints[t]!=baseline['protected'][t]:raise SystemExit('BLOCKED: source master baseline changed')
if sourceprints['bom_snapshot_detail']['rows']!=baseline['snapshotEdges']:raise SystemExit('BLOCKED: source snapshot edge count changed')
verify_only=sys.argv[1:]==['--verify-only']
if sys.argv[1:] and not verify_only:raise SystemExit('Only --verify-only is supported')
if not verify_only and int(dest('SELECT COUNT(*) FROM material;').strip()):raise SystemExit('Refusing: destination material is not empty. Use another isolated environment; never clear protected tables.')
for table,where in ([] if verify_only else tables):
 cmd='MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --default-character-set=utf8mb4 -u"$MYSQL_USER" --no-create-info --compact --skip-triggers --skip-add-locks --skip-disable-keys --hex-blob --complete-insert --set-gtid-purged=OFF --single-transaction mrp '+table
 if where:cmd+=' --where='+where # fixed numeric expression, no shell/user data
 data=subprocess.run(['docker','exec','mrp-mysql','sh','-c',cmd],capture_output=True,text=True,check=True).stdout
 # Dump has INSERT statements only; disallow any DDL / destructive statement.
 lines=[line for line in data.splitlines() if line.strip() and not line.startswith(('/*','--'))]
 if any(not line.startswith('INSERT INTO `'+table+'`') for line in lines):raise RuntimeError('Unexpected dump statement; inspect before executing')
 if table=='material_category':
  # Flyway seeds categories; copy the source values over destination seeds only.
  converted=[]
  for line in lines:
   cols=re.search(r'\((.*?)\) VALUES',line).group(1).split(', ')
   converted.append(line.rstrip(';')+' ON DUPLICATE KEY UPDATE '+','.join(c+'=VALUES('+c+')' for c in cols)+';')
  lines=converted
 try:dest('\n'.join(lines)+'\n')
 except subprocess.CalledProcessError as error:
  raise RuntimeError(error.stderr) from error
 print('Cloned',table,'to mrp-testkit-mysql/mrp_testkit')
results={t:{'source':sourceprints[t],'destination':fingerprint(dest,t,w)} for t,w in tables}
ok=all(v['source']==v['destination'] for v in results.values())
(P/'evidence/clone-verification.json').write_text(json.dumps({'status':'PASS' if ok else 'FAIL','checkedAt':datetime.datetime.now().astimezone().isoformat(),'tables':results},indent=2)+'\n')
if not ok:raise SystemExit('FAIL: cloned inputs differ; preserve environment for investigation')
print('Only copied inputs; BOM sync and management actions are excluded.')
