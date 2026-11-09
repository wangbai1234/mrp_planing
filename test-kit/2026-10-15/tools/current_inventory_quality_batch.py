#!/usr/bin/env python3
"""Verify a DEFECTIVE-only stock row does not enter calculation pools."""
import datetime
import json
import pathlib
import subprocess
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'evidence/runs/2026-11-03-inventory-quality-101530/result.json'
OUT = ROOT / 'evidence/runs' / ('2026-11-03-inventory-quality-batch-' + datetime.datetime.now().strftime('%H%M%S'))
OUT.mkdir(parents=True)
BASE = 'http://127.0.0.1:8182/api/v1'

def request(path, method='GET', body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    data = json.dumps(body).encode() if isinstance(body, dict) else body
    req = urllib.request.Request(BASE + path, method=method, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())

def sql(statement):
    process = subprocess.run(['docker', 'exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
                              'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" mrp_testkit'],
                             input=statement, capture_output=True, text=True, check=True)
    return process.stdout.splitlines()

source = json.loads(SOURCE.read_text())
snapshot_id = source['snapshotId']
status, login = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
assert status == 200
token = login['data']['token']
status, forecast = request('/forecast-versions/current', token=token)
assert status == 200
forecast_id = forecast['data']['id']
status, created = request(f'/mrp/batches?factoryCode=ALL&forecastVersionId={forecast_id}&inventorySnapshotId={snapshot_id}', 'POST', b'', token)
assert status == 200 and created['data']['status'] == 'DRAFT', created
batch_id = created['data']['id']
record = {'status': 'IN_PROGRESS', 'snapshotId': snapshot_id, 'forecastId': forecast_id, 'batchId': batch_id,
          'createHttp': status, 'createStatus': created['data']['status']}
(OUT / 'progress.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
try:
    balances = sql(f'SELECT warehouse_scope,inventory_quality,available_quantity,included_flag FROM mrp_inventory_balance WHERE inventory_snapshot_id={snapshot_id} AND material_code="6839AA000365" ORDER BY warehouse_scope;')
    pools = sql(f'SELECT warehouse_code,available_qty,reserved_qty,allocated_qty FROM mrp_inventory_pool WHERE batch_id={batch_id} AND material_code="6839AA000365" ORDER BY warehouse_code;')
    assert any(row.startswith('UAT_APK_DEF\tDEFECTIVE\t1000.000000\t0') for row in balances), balances
    assert all(not row.startswith('UAT_APK_DEF\t') for row in pools), pools
    assert len(pools) == 2 and all(row.split('\t')[1] == '0.000000' for row in pools), pools
    record.update({'balances': balances, 'pools': pools})
    status, precheck = request(f'/mrp/batches/{batch_id}/precheck', 'POST', b'', token)
    record['precheckHttp'] = status
    record['precheckStatus'] = (precheck.get('data') or {}).get('status')
    assert status == 200 and record['precheckStatus'] == 'READY_TO_CALCULATE', precheck
    status, task = request(f'/mrp/batches/{batch_id}/calculate', 'POST', b'', token)
    assert status == 200, task
    record['taskId'] = task['data']['id']
    for _ in range(60):
        time.sleep(1)
        status, task = request(f'/mrp/batches/{batch_id}/calculation-task', token=token)
        assert status == 200, task
        if task['data']['status'] in ('SUCCEEDED', 'FAILED'): break
    record['taskStatus'] = task['data']['status']
    assert record['taskStatus'] == 'SUCCEEDED', task
    status, batch = request(f'/mrp/batches/{batch_id}', token=token)
    assert status == 200 and batch['data']['status'] == 'SCHEDULE_READY', batch
    run_id = batch['data']['activeRunId']
    record['runId'] = run_id
    rows = sql(f'SELECT factory_code,source_month,SUM(system_quantity) FROM mrp_daily_plan_detail WHERE batch_id={batch_id} AND run_id={run_id} AND terminal_type="MACHINE_OUTPUT" AND root_material_code="6839AA000365" GROUP BY factory_code,source_month ORDER BY factory_code,source_month;')
    assert len(rows) == 2 and all(row.split('\t')[2] == '10.000000' for row in rows), rows
    record['machineOutputByFactory'] = rows
    record['status'] = 'PASS'
finally:
    (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({'evidence': str(OUT), 'status': record['status'], 'batchId': batch_id,
                  'runId': record.get('runId'), 'machineOutputByFactory': record.get('machineOutputByFactory')}, ensure_ascii=False))
