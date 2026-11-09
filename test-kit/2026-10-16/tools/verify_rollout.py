"""Read-only main rollout verification; preserve the original before evidence."""
import datetime, hashlib, json, pathlib, subprocess

root = pathlib.Path(__file__).resolve().parents[1]
evidence = root / 'evidence/repair'
before = json.loads((evidence / 'main-protected-before.json').read_text())

def sql(query):
    assert query.startswith(('SELECT', 'SHOW'))
    return subprocess.run(['docker', 'exec', '-i', 'mrp-mysql', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 --raw -u"$MYSQL_USER" -NB mrp'],
        input=query, text=True, capture_output=True, check=True).stdout.splitlines()

def fingerprint(table, where='', encoding='raw'):
    columns = [line.split('\t')[0] for line in sql('SHOW COLUMNS FROM `' + table + '`;')]
    expression = ','.join("IFNULL(HEX(`" + column + "`),'NULL')" for column in columns)
    projection = "CONCAT_WS('|'," + expression + ')' if encoding == 'hex' else '*'
    rows = sql('SELECT ' + projection + ' FROM `' + table + '` ' + where + ' ORDER BY id;')
    return {'rows': len(rows), 'sha256': hashlib.sha256(('\n'.join(rows) + ('\n' if rows else '')).encode()).hexdigest()}

protected = {}
for name, table, where in [
    ('batch91', 'mrp_calc_batch', 'WHERE id=91'),
    ('run37', 'mrp_calc_run', 'WHERE id=37'),
    ('facts91', 'mrp_daily_plan_detail', 'WHERE batch_id=91'),
    ('override91', 'mrp_daily_plan_override', 'WHERE batch_id=91'),
    ('snapshot33', 'bom_snapshot_detail', 'WHERE snapshot_id=33'),
]:
    actual = fingerprint(table, where)
    protected[name] = {'status': 'PASS' if actual == before[name] else 'FAIL', 'before': before[name], 'after': actual}

source = json.loads((root.parent / '2026-10-15/data/source-manifest.json').read_text())['protected']
for table in ['material', 'material_category']:
    actual = fingerprint(table, encoding='hex')
    protected[table] = {'status': 'PASS' if actual == source[table] else 'FAIL', 'before': source[table], 'after': actual}

organization = {}
for table, field, where in [('bom_parent', 'org_name', ''), ('bom_detail', 'org_name', ''),
                          ('bom_snapshot_detail', 'organization_code', 'WHERE snapshot_id=34')]:
    organization[table] = [json.loads(row) for row in sql(
        f"SELECT JSON_OBJECT('organization',{field},'rows',COUNT(*)) FROM {table} {where} GROUP BY {field};")]
snapshot = json.loads(sql("SELECT JSON_OBJECT('id',id,'status',status,'checksum',checksum) FROM bom_snapshot WHERE id=34;")[0])
duplicates = int(sql('SELECT COUNT(*) FROM (SELECT parent_code,child_code FROM bom_snapshot_detail WHERE snapshot_id=34 GROUP BY parent_code,child_code HAVING COUNT(*)>1) d;')[0])
valid = all(row['organization'] == '上海七十迈数字科技有限公司' for rows in organization.values() for row in rows)
result = {'status': 'PASS' if all(row['status'] == 'PASS' for row in protected.values()) and valid and snapshot['status'] == 'READY' and duplicates == 0 else 'FAIL',
          'checkedAt': datetime.datetime.now().astimezone().isoformat(), 'protected': protected,
          'fingerprintEncoding': 'Historical batch baseline uses raw TSV plus trailing newline; master data baseline uses HEX fields. Empty batch selection hashes empty bytes.',
          'organization': organization, 'newSnapshot': snapshot, 'duplicateEdges': duplicates,
          'authorizedChange': 'Local BOM replaced by Shanghai-only NC sync; new snapshot 34 created. Historical snapshot 33 and batch 91 are protected.'}
(evidence / 'main-rollout-verification.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(result, ensure_ascii=False))
raise SystemExit(result['status'] != 'PASS')
