#!/usr/bin/env python3
"""Read-only extraction. Source connection is docker env, never emitted."""
import subprocess,json,gzip,hashlib,pathlib,datetime
BASE=pathlib.Path(__file__).resolve().parents[1]
if (BASE/'data/source-manifest.json').exists():raise SystemExit('Baseline exists: use verify_protected.py; extraction must not overwrite baseline.')
def sql(query):
    assert query.lstrip().upper().startswith(('SELECT','SHOW')), 'Read-only SQL only'
    p=subprocess.run(['docker','exec','-i','mrp-mysql','sh','-c',
      'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 --raw -u"$MYSQL_USER" -N -B mrp'],input=query,text=True,capture_output=True,check=True)
    return p.stdout.splitlines()
def rows(query):return [json.loads(s) for s in sql(query)]
def save(name,value):
    p=BASE/'data'/name
    with gzip.open(p,'wt',encoding='utf-8') as f:json.dump(value,f,ensure_ascii=False,separators=(',',':'))
snapshot=rows("SELECT JSON_OBJECT('id',id,'checksum',checksum,'status',status) FROM bom_snapshot WHERE status='READY' ORDER BY id DESC LIMIT 1;")[0]
materials=rows("SELECT JSON_OBJECT('code',m.material_code,'name',m.material_name,'unit',m.unit,'category',c.code,'deleted',m.is_deleted,'active',m.is_active) FROM material m LEFT JOIN material_category c ON c.id=m.material_category_id AND c.is_deleted=0 AND c.enabled=1 ORDER BY m.id;")
categories=rows("SELECT JSON_OBJECT('id',id,'code',code,'name',name,'parent',parent_id,'enabled',enabled,'deleted',is_deleted) FROM material_category ORDER BY id;")
edges=rows(f"SELECT JSON_OBJECT('parent',parent_code,'child',child_code,'pq',CAST(parent_qty AS CHAR),'cq',CAST(child_qty AS CHAR),'version',bom_version,'id',id) FROM bom_snapshot_detail WHERE snapshot_id={int(snapshot['id'])} ORDER BY id;")
save('source-materials.json.gz',materials);save('source-categories.json.gz',categories);save('source-bom.json.gz',edges)
# Full protected table hashes including all historical snapshots, no business test or mutation.
protected=['material','material_category','bom_parent','bom_detail','bom_sync_status','bom_snapshot','bom_snapshot_detail']
fingerprints={}
for table in protected:
    columns=[s.split('\t')[0] for s in sql(f'SHOW COLUMNS FROM `{table}`;')]
    # HEX each field prevents tabs/newlines from changing canonical row boundaries; NULL != empty.
    expr=','.join(f"IFNULL(HEX(`{c}`),'NULL')" for c in columns)
    data=sql(f"SELECT CONCAT_WS('|',{expr}) FROM `{table}` ORDER BY id;")
    digest=hashlib.sha256(('\n'.join(data)+'\n').encode()).hexdigest()
    fingerprints[table]={'rows':len(data),'sha256':digest}
manifest={'capturedAt':datetime.datetime.now().astimezone().isoformat(),'source':'mrp-mysql/mrp (SELECT only)', 'snapshot':snapshot,'materials':len(materials),'categories':len(categories),'snapshotEdges':len(edges),'protected':fingerprints}
(BASE/'data'/'source-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in manifest.items() if k!='protected'},ensure_ascii=False))
