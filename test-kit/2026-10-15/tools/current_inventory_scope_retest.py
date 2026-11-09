#!/usr/bin/env python3
"""Retest I06 guards against already preserved isolated snapshots."""
import datetime
import json
import pathlib
import subprocess

from current_material_cell_matrix import BASE, TABLES, call, count
from current_source_snapshot import snapshot

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-inventory-scope-retest-' + datetime.datetime.now().strftime('%H%M%S'))


def batch_count():
    proc = subprocess.run(['docker', 'exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
                           'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B -u"$MYSQL_USER" mrp_testkit'],
                          input='SELECT COUNT(*) FROM mrp_calc_batch;', capture_output=True, text=True, check=True)
    return int(proc.stdout.strip())


def main():
    OUT.mkdir(parents=True)
    http, login = call('/auth/login', 'POST', json.dumps({'username': 'admin', 'password': 'admin123'}).encode())
    assert http == 200, login
    token = login['data']['token']
    before = {table: count(table) for table in TABLES}
    before_batches = batch_count()
    record = {'status': 'IN_PROGRESS', 'base': BASE, 'before': before,
              'beforeBatchCount': before_batches, 'beforeSource': snapshot(), 'cases': []}
    try:
        for name, path, expected in (
                ('partial', '/mrp/batches?factoryCode=ALL&forecastVersionId=45&inventorySnapshotId=56',
                 'INVENTORY_WAREHOUSE_MISMATCH'),
                ('combination', '/mrp/batches?factoryCode=ALL&forecastVersionId=45&inventorySnapshotId=56&inventorySnapshotIds=58',
                 'SINGLE_INVENTORY_VERSION_REQUIRED'),
                ('outside-scope', '/mrp/batches?factoryCode=ALL&forecastVersionId=45&inventorySnapshotId=59',
                 'INVENTORY_WAREHOUSE_MISMATCH')):
            http, response = call(path, 'POST', b'', token)
            passed = http == 400 and response.get('error', {}).get('code') == expected
            if name == 'outside-scope':
                passed = passed and 'UAT_SCOPE_EXTRA_1103' in response.get('error', {}).get('message', '')
            record['cases'].append({'name': name, 'status': 'PASS' if passed else 'FAIL',
                                    'path': path, 'http': http, 'response': response})
            assert passed, record['cases'][-1]
        assert before == {table: count(table) for table in TABLES}
        assert batch_count() == before_batches
        record['status'] = 'PASS'
    except Exception as error:
        record['status'] = 'FAIL'
        record['error'] = str(error)
        raise
    finally:
        record['after'] = {table: count(table) for table in TABLES}
        record['afterBatchCount'] = batch_count()
        record['afterSource'] = snapshot()
        record['protectedSourcesUnchanged'] = record['beforeSource']['tables'] == record['afterSource']['tables']
        if not record['protectedSourcesUnchanged']:
            record['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    assert record['status'] == 'PASS'
    print(json.dumps({'status': 'PASS', 'evidence': str(OUT),
                      'cases': [(case['name'], case['http']) for case in record['cases']]}, ensure_ascii=False))


if __name__ == '__main__':
    main()
