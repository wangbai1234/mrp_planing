#!/usr/bin/env python3
"""D2: exercise root stock boundaries through isolated imports and actual Worker."""
import datetime
import hashlib
import io
import json
import pathlib
import subprocess
import time
import urllib.error
import urllib.request

import openpyxl

ROOT = pathlib.Path(__file__).resolve().parents[1]
RUN = ROOT / 'evidence/runs' / ('2026-11-03-inventory-boundaries-' + datetime.datetime.now().strftime('%H%M%S'))
RUN.mkdir(parents=True)
FORECAST = ROOT / 'evidence/runs/2026-11-02-bom-chain-20261103100337957612/forecast-bom.xlsx'
STOCK = ROOT / 'evidence/runs/2026-11-03-inventory-quality-101530/inventory-quality.xlsx'
BASE = 'http://127.0.0.1:8182/api/v1'
MATERIAL = '6839AA000365'
CASES = [('missing', None, 1003), ('zero', 0, 1003), ('partial', 300, 703),
         ('equal', 1003, 0), ('excess', 1200, 0)]

def request(path, method='GET', body=None, token=None, content_type='application/json'):
    headers = {'Content-Type': content_type}
    if token: headers['Authorization'] = 'Bearer ' + token
    data = json.dumps(body).encode() if isinstance(body, dict) else body
    req = urllib.request.Request(BASE + path, method=method, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())

def send_workbook(path, book, name, token):
    content = io.BytesIO()
    book.save(content)
    data = content.getvalue()
    (RUN / name).write_bytes(data)
    boundary = '----MRPBoundaries20261103'
    head = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + name +
            '"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode()
    tail = ('\r\n--' + boundary + '--\r\n').encode()
    status, result = request(path, 'POST', head + data + tail, token, 'multipart/form-data; boundary=' + boundary)
    assert status == 202 and result['data']['errorRows'] == 0, (status, result)
    return result['data']['taskId'], hashlib.sha256(data).hexdigest()

def sql(statement):
    process = subprocess.run(['docker', 'exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
                              'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" mrp_testkit'],
                             input=statement, capture_output=True, text=True, check=True)
    return process.stdout.splitlines()

status, login = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
assert status == 200
token = login['data']['token']
book = openpyxl.load_workbook(FORECAST)
root = next(row for row in book.active.iter_rows(min_row=3) if row[0].value == '爱培科')
root[5].value = 1003
root[11].value = 1003
task_id, forecast_sha = send_workbook('/forecast-imports', book, 'forecast-1003.xlsx', token)
status, confirmed = request(f'/forecast-imports/{task_id}/confirm', 'POST', b'', token)
assert status == 200, confirmed
forecast_id = confirmed['data']['id']
record = {'status': 'IN_PROGRESS', 'environment': '8182/3317/6391', 'forecastId': forecast_id,
          'forecastSha256': forecast_sha, 'cases': []}
(RUN / 'progress.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')

for name, available, expected in CASES:
    item = {'case': name, 'availableGood': available, 'expectedNet': expected, 'status': 'IN_PROGRESS'}
    record['cases'].append(item)
    batch_id = None
    try:
        book = openpyxl.load_workbook(STOCK)
        rows = [row for row in book.active.iter_rows(min_row=3)
                if row[0].value == 'UAT_APK' and row[3].value == MATERIAL]
        assert len(rows) == 1, len(rows)
        root_row = rows[0][0].row
        if available is None: book.active.delete_rows(root_row)
        else: book.active.cell(root_row, 6).value = available
        book.active.cell(3, 7).value = 'D2-' + name
        task_id, stock_sha = send_workbook('/inventory-imports/items', book, 'stock-' + name + '.xlsx', token)
        item.update({'inventoryTaskId': task_id, 'inventorySha256': stock_sha})
        status, confirmed = request(f'/inventory-imports/items/{task_id}/confirm', 'POST', b'', token)
        assert status == 200, confirmed
        snapshot_id = confirmed['data']['snapshotId']
        item['snapshotId'] = snapshot_id
        status, created = request(f'/mrp/batches?factoryCode=ALL&forecastVersionId={forecast_id}&inventorySnapshotId={snapshot_id}', 'POST', b'', token)
        assert status == 200 and created['data']['status'] == 'DRAFT', created
        batch_id = created['data']['id']
        item['batchId'] = batch_id
        balance = sql(f'SELECT warehouse_scope,inventory_quality,available_quantity,included_flag FROM mrp_inventory_balance WHERE inventory_snapshot_id={snapshot_id} AND material_code="{MATERIAL}" ORDER BY warehouse_scope;')
        pools = sql(f'SELECT warehouse_code,available_qty,reserved_qty FROM mrp_inventory_pool WHERE batch_id={batch_id} AND material_code="{MATERIAL}" ORDER BY warehouse_code;')
        item['balance'] = balance
        item['pools'] = pools
        assert any(row.startswith('UAT_APK_DEF\tDEFECTIVE\t1000.000000\t0') for row in balance), balance
        assert all(not row.startswith('UAT_APK_DEF\t') for row in pools), pools
        assert ('UAT_APK\tGOOD' in '\n'.join(balance)) == (available is not None), balance
        status, precheck = request(f'/mrp/batches/{batch_id}/precheck', 'POST', b'', token)
        assert status == 200 and precheck['data']['status'] == 'READY_TO_CALCULATE', precheck
        status, submitted = request(f'/mrp/batches/{batch_id}/calculate', 'POST', b'', token)
        assert status == 200, submitted
        item['taskId'] = submitted['data']['id']
        for _ in range(90):
            time.sleep(1)
            status, task = request(f'/mrp/batches/{batch_id}/calculation-task', token=token)
            assert status == 200, task
            if task['data']['status'] in ('SUCCEEDED', 'FAILED'): break
        assert task['data']['status'] == 'SUCCEEDED', task
        status, batch = request(f'/mrp/batches/{batch_id}', token=token)
        assert status == 200 and batch['data']['status'] == 'SCHEDULE_READY', batch
        run_id = batch['data']['activeRunId']
        item['runId'] = run_id
        details = sql(f'SELECT factory_code,source_month,gross_qty,inventory_used_qty,net_qty FROM mrp_requirement_detail WHERE batch_id={batch_id} AND run_id={run_id} AND current_material_code="{MATERIAL}" ORDER BY factory_code,source_month;')
        item['details'] = details
        assert len(details) == 2, details
        apk = next(row.split('\t') for row in details if row.startswith('爱培科\t'))
        yh = next(row.split('\t') for row in details if row.startswith('永惠\t'))
        assert apk[2:] == [f'{1003:.6f}', f'{1003-expected:.6f}', f'{expected:.6f}'], apk
        assert yh[2:] == ['10.000000', '0.000000', '10.000000'], yh
        scheduled = sql(f'SELECT factory_code,SUM(system_quantity) FROM mrp_daily_plan_detail WHERE batch_id={batch_id} AND run_id={run_id} AND terminal_type="MACHINE_OUTPUT" AND root_material_code="{MATERIAL}" GROUP BY factory_code;')
        item['machineSchedule'] = scheduled
        scheduled_by_factory = dict(row.split('\t') for row in scheduled)
        assert float(scheduled_by_factory.get('爱培科', '0')) == expected, scheduled
        assert float(scheduled_by_factory['永惠']) == 10, scheduled
        item['status'] = 'PASS'
    except Exception as error:
        item['status'] = 'FAIL'
        item['error'] = str(error)
        raise
    finally:
        if batch_id is not None:
            status, closed = request(f'/mrp/batches/{batch_id}/cancel', 'POST', b'', token)
            item['cancelHttp'] = status
            item['finalBatchStatus'] = (closed.get('data') or {}).get('status')
        (RUN / 'progress.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')

record['status'] = 'PASS' if all(item['status'] == 'PASS' and item['finalBatchStatus'] == 'CANCELLED' for item in record['cases']) else 'FAIL'
(RUN / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({'evidence': str(RUN), 'status': record['status'], 'cases': [(row['case'], row['status'], row['expectedNet']) for row in record['cases']]}, ensure_ascii=False))
