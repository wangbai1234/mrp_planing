#!/usr/bin/env python3
"""I09: a new inventory import must not rewrite a batch frozen on the old snapshot."""
import datetime
import hashlib
import io
import json
import pathlib
import subprocess
import time

import openpyxl

from current_material_cell_matrix import call, count
from current_source_snapshot import snapshot

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-snapshot-freeze-' + datetime.datetime.now().strftime('%H%M%S'))
STOCK = ROOT / 'evidence/runs/2026-11-03-inventory-quality-101530/inventory-quality.xlsx'
FORECAST = 38
OLD_SNAPSHOT = 47
ROOT_MATERIAL = '6839AA000365'


def sql(statement):
    assert statement.startswith('SELECT')
    proc = subprocess.run(['docker', 'exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
                           'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" mrp_testkit'],
                          input=statement, text=True, capture_output=True, check=True)
    return proc.stdout.splitlines()


def data(path, token, method='GET', body=None, expected=200):
    http, result = call(path, method, body, token)
    assert http == expected and result.get('success'), (path, http, result)
    return result['data']


def wait_task(batch, token):
    for _ in range(90):
        time.sleep(1)
        task = data(f'/mrp/batches/{batch}/calculation-task', token)
        if task and task['status'] in ('SUCCEEDED', 'FAILED'):
            assert task['status'] == 'SUCCEEDED', task
            return task
    raise AssertionError('calculation task did not finish')


def net_rows(batch, run):
    return sql(f'SELECT factory_code,CAST(gross_qty AS CHAR),CAST(inventory_used_qty AS CHAR),'
               f'CAST(net_qty AS CHAR) FROM mrp_requirement_detail WHERE batch_id={batch} AND run_id={run} '
               f'AND level_no=0 AND current_material_code="{ROOT_MATERIAL}" ORDER BY factory_code;')


def main():
    OUT.mkdir(parents=True)
    http, login = call('/auth/login', 'POST', json.dumps({'username': 'admin', 'password': 'admin123'}).encode())
    assert http == 200, login
    token = login['data']['token']
    assert sql("SELECT COUNT(*) FROM mrp_calc_batch WHERE status NOT IN ('CANCELLED','PUBLISHED','STALE','SUPERSEDED');") == ['0']
    before = {'snapshots': count('inventory_snapshot'), 'items': count('inventory_item')}
    record = {'status': 'IN_PROGRESS', 'before': before, 'beforeSource': snapshot(), 'cases': []}
    active = None
    try:
        old = data(f'/mrp/batches?factoryCode=ALL&forecastVersionId={FORECAST}&inventorySnapshotId={OLD_SNAPSHOT}',
                   token, 'POST', b'')
        active = old['id']
        record['oldBatch'] = {'id': active, 'inventorySnapshotId': old['inventorySnapshotId'],
                              'inputChecksum': old['inputChecksum']}
        assert old['status'] == 'DRAFT' and old['inventorySnapshotId'] == OLD_SNAPSHOT

        wb = openpyxl.load_workbook(STOCK)
        sheet = wb.active
        changed = 0
        for row in sheet.iter_rows(min_row=3):
            if row[0].value == 'UAT_APK' and row[3].value == ROOT_MATERIAL:
                row[5].value = '1200'
                row[6].value = OUT.name
                changed += 1
        assert changed == 1, changed
        buffer = io.BytesIO()
        wb.save(buffer)
        blob = buffer.getvalue()
        (OUT / 'inventory-new-snapshot.xlsx').write_bytes(blob)
        record['inventoryFileSha256'] = hashlib.sha256(blob).hexdigest()
        boundary = '----MRPSnapshotFreeze20261103'
        head = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="inventory-new-snapshot.xlsx"'
                '\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n')
        http, preview = call('/inventory-imports/items', 'POST',
                             head.encode() + blob + ('\r\n--' + boundary + '--\r\n').encode(), token,
                             'multipart/form-data; boundary=' + boundary)
        assert http == 202 and preview['data']['errorRows'] == 0, preview
        task_id = preview['data']['taskId']
        imported = data(f'/inventory-imports/items/{task_id}/confirm', token, 'POST', b'')
        new_snapshot = imported['snapshotId']
        record['newSnapshotId'] = new_snapshot
        old_after_import = data(f'/mrp/batches/{active}', token)
        assert old_after_import['inventorySnapshotId'] == OLD_SNAPSHOT
        assert old_after_import['inputChecksum'] == old['inputChecksum']
        record['oldAfterNewImport'] = {'status': old_after_import['status'],
                                       'inventorySnapshotId': old_after_import['inventorySnapshotId'],
                                       'inputChecksum': old_after_import['inputChecksum']}
        assert data(f'/mrp/batches/{active}/precheck', token, 'POST', b'')['status'] == 'READY_TO_CALCULATE'
        old_submit = data(f'/mrp/batches/{active}/calculate', token, 'POST', b'')
        old_task = wait_task(active, token)
        old_run = data(f'/mrp/batches/{active}', token)['activeRunId']
        old_rows = net_rows(active, old_run)
        record['oldBatch'].update({'runId': old_run, 'taskId': old_submit['id'], 'rootRows': old_rows})
        assert any(row.startswith('爱培科\t1003.000000\t300.000000\t703.000000') for row in old_rows), old_rows
        cancelled = data(f'/mrp/batches/{active}/cancel', token, 'POST', b'')
        assert cancelled['status'] == 'CANCELLED'
        active = None

        newer = data(f'/mrp/batches?factoryCode=ALL&forecastVersionId={FORECAST}&inventorySnapshotId={new_snapshot}',
                     token, 'POST', b'')
        active = newer['id']
        assert newer['inventorySnapshotId'] == new_snapshot
        assert data(f'/mrp/batches/{active}/precheck', token, 'POST', b'')['status'] == 'READY_TO_CALCULATE'
        new_submit = data(f'/mrp/batches/{active}/calculate', token, 'POST', b'')
        new_task = wait_task(active, token)
        new_run = data(f'/mrp/batches/{active}', token)['activeRunId']
        new_rows = net_rows(active, new_run)
        record['newBatch'] = {'id': active, 'runId': new_run, 'taskId': new_submit['id'],
                              'inventorySnapshotId': newer['inventorySnapshotId'], 'rootRows': new_rows}
        assert any(row.startswith('爱培科\t1003.000000\t1003.000000\t0.000000') for row in new_rows), new_rows
        assert net_rows(old['id'], old_run) == old_rows
        cancelled = data(f'/mrp/batches/{active}/cancel', token, 'POST', b'')
        assert cancelled['status'] == 'CANCELLED'
        active = None
        after = {'snapshots': count('inventory_snapshot'), 'items': count('inventory_item')}
        record['after'] = after
        assert after['snapshots'] == before['snapshots'] + 1 and after['items'] > before['items']
        record['status'] = 'PASS'
    except Exception as error:
        record['status'] = 'FAIL'
        record['error'] = str(error)
        raise
    finally:
        if active is not None:
            current = data(f'/mrp/batches/{active}', token)
            if current['status'] == 'CALCULATING':
                wait_task(active, token)
            http, cancelled = call(f'/mrp/batches/{active}/cancel', 'POST', b'', token)
            record['cleanup'] = {'http': http, 'response': cancelled}
        record['afterSource'] = snapshot()
        record['protectedSourcesUnchanged'] = record['beforeSource']['tables'] == record['afterSource']['tables']
        if not record['protectedSourcesUnchanged']:
            record['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    assert record['status'] == 'PASS'
    print(json.dumps({'status': 'PASS', 'evidence': str(OUT), 'oldBatch': old['id'],
                      'newBatch': record['newBatch']['id'], 'newSnapshot': new_snapshot}, ensure_ascii=False))


if __name__ == '__main__':
    main()
