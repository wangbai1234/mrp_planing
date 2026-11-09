#!/usr/bin/env python3
"""SELECT-only protected source comparison; never overwrite baseline."""
import pathlib,json,subprocess,hashlib,sys,datetime
p=pathlib.Path(__file__).resolve().parents[1]
baseline=json.loads((p/'data/source-manifest.json').read_text())['protected']
def sql(q):
 assert q.startswith(('SELECT','SHOW'))
 return subprocess.run(['docker','exec','-i','mrp-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --raw -u"$MYSQL_USER" -N -B mrp'],input=q,text=True,capture_output=True,check=True).stdout.splitlines()
results={}
for table,b in baseline.items():
 cols=[r.split('\t')[0] for r in sql('SHOW COLUMNS FROM `'+table+'`;')]
 expr=','.join("IFNULL(HEX(`"+c+"`),'NULL')" for c in cols)
 rows=sql("SELECT CONCAT_WS('|',"+expr+") FROM `"+table+'` ORDER BY id;')
 actual={'rows':len(rows),'sha256':hashlib.sha256(('\n'.join(rows)+'\n').encode()).hexdigest()}
 results[table]={'status':'PASS' if actual==b else 'FAIL','before':b,'after':actual}
result={'checkedAt':datetime.datetime.now().astimezone().isoformat(),'status':'PASS' if all(r['status']=='PASS' for r in results.values()) else 'FAIL','tables':results}
(p/'evidence/protected-source-verification.json').write_text(json.dumps(result,indent=2)+'\n');print(result['status']);sys.exit(result['status']!='PASS')
