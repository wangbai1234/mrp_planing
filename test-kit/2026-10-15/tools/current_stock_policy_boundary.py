#!/usr/bin/env python3
"""D2: freeze a safety/warning policy, verify a real schedule, restore policy."""
import datetime
import json
import pathlib
import subprocess
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-stock-policy-' + datetime.datetime.now().strftime('%H%M%S'))
OUT.mkdir(parents=True)
BASE = 'http://127.0.0.1:8182/api/v1'
FORECAST_ID = 38
SNAPSHOT_ID = 47  # GOOD=300, DEFECTIVE=1000 for the chosen root
MATERIAL = '6839AA000365'

def request(path, method='GET', body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    data = json.dumps(body).encode() if isinstance(body, dict) else body
    req = urllib.request.Request(BASE + path, method=method, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=40) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())

def sql(statement):
    process = subprocess.run(['docker', 'exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
                              'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" mrp_testkit'],
                             input=statement, capture_output=True, text=True, check=True)
    return process.stdout.splitlines()

status, login = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
assert status == 200
token = login['data']['token']
status, response = request(f'/mrp/stock-policies/page?warehouseCode=UAT_APK&keyword={MATERIAL}&pageSize=100', token=token)
assert status == 200, response
policies = [row for row in response['data']['items'] if row['materialCode'] == MATERIAL]
assert len(policies) == 1, policies
original = policies[0]
assert str(original['safetyStockQty']) in ('0', '0.0') and str(original['warningStockQty']) in ('0', '0.0'), original
policy_id = original['id']
record = {'status': 'IN_PROGRESS', 'environment': '8182/3317/6391', 'policyId': policy_id,
          'forecastId': FORECAST_ID, 'snapshotId': SNAPSHOT_ID, 'originalPolicy': original}
batch_id = None
changed = False
try:
    policy = dict(original)
    policy.update({'safetyStockQty': 100, 'warningStockQty': 250})
    status, updated = request(f'/mrp/stock-policies/{policy_id}', 'PUT', policy, token)
    assert status == 200, updated
    changed = True
    record['policyAfterUpdate'] = updated['data']
    status, created = request(f'/mrp/batches?factoryCode=ALL&forecastVersionId={FORECAST_ID}&inventorySnapshotId={SNAPSHOT_ID}', 'POST', b'', token)
    assert status == 200 and created['data']['status'] == 'DRAFT', created
    batch_id = created['data']['id']
    record['batchId'] = batch_id
    record['frozenStockPolicyVersionId'] = created['data']['stockPolicyVersionId']
    pools = sql(f'SELECT factory_code,warehouse_code,available_qty,reserved_qty,warning_qty FROM mrp_inventory_pool WHERE batch_id={batch_id} AND material_code="{MATERIAL}" ORDER BY warehouse_code;')
    record['pools'] = pools
    apk = next(row.split('\t') for row in pools if '\tUAT_APK\t' in row)
    assert apk[2:] == ['300.000000', '100.000000', '250.000000'], apk
    assert all('UAT_APK_DEF' not in row for row in pools)
    status, precheck = request(f'/mrp/batches/{batch_id}/precheck', 'POST', b'', token)
    assert status == 200 and precheck['data']['status'] == 'READY_TO_CALCULATE', precheck
    allocations = sql(f'SELECT p.warehouse_code,a.allocated_qty FROM mrp_inventory_allocation a JOIN mrp_inventory_pool p ON p.id=a.pool_id WHERE p.batch_id={batch_id} AND p.material_code="{MATERIAL}";')
    record['allocations'] = allocations
    assert sum(float(row.split('\t')[1]) for row in allocations if row.startswith('UAT_APK\t')) == 200, allocations
    status, submitted = request(f'/mrp/batches/{batch_id}/calculate', 'POST', b'', token)
    assert status == 200, submitted
    record['taskId'] = submitted['data']['id']
    for _ in range(90):
        time.sleep(1)
        status, task = request(f'/mrp/batches/{batch_id}/calculation-task', token=token)
        assert status == 200, task
        if task['data']['status'] in ('SUCCEEDED', 'FAILED'): break
    assert task['data']['status'] == 'SUCCEEDED', task
    status, batch = request(f'/mrp/batches/{batch_id}', token=token)
    assert status == 200 and batch['data']['status'] == 'SCHEDULE_READY', batch
    record['runId'] = batch['data']['activeRunId']
    details = sql(f'SELECT factory_code,gross_qty,inventory_used_qty,net_qty FROM mrp_requirement_detail WHERE batch_id={batch_id} AND run_id={record["runId"]} AND current_material_code="{MATERIAL}" ORDER BY factory_code;')
    record['details'] = details
    assert len(details) == 2
    apk = next(row.split('\t') for row in details if row.startswith('爱培科\t'))
    yh = next(row.split('\t') for row in details if row.startswith('永惠\t'))
    assert apk[1:] == ['1003.000000', '200.000000', '803.000000'], apk
    assert yh[1:] == ['10.000000', '0.000000', '10.000000'], yh
    status, risks = request(f'/mrp/inventory/stock-risks/{batch_id}', token=token)
    assert status == 200, risks
    record['riskRows'] = [row for row in risks['data'] if row['materialCode'] == MATERIAL]
    record['status'] = 'PASS'
finally:
    if batch_id is not None:
        status, closed = request(f'/mrp/batches/{batch_id}/cancel', 'POST', b'', token)
        record['cancelHttp'] = status
        record['finalBatchStatus'] = (closed.get('data') or {}).get('status')
    if changed:
        original_policy = dict(original)
        status, restored = request(f'/mrp/stock-policies/{policy_id}', 'PUT', original_policy, token)
        record['policyRestoreHttp'] = status
        record['policyRestored'] = restored.get('data')
    (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
assert record['status'] == 'PASS' and record['finalBatchStatus'] == 'CANCELLED' and record['policyRestoreHttp'] == 200
print(json.dumps({'evidence': str(OUT), 'status': record['status'], 'batchId': batch_id,
                  'runId': record['runId'], 'netQty': 803}, ensure_ascii=False))
