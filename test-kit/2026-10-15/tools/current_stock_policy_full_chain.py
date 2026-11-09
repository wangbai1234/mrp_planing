#!/usr/bin/env python3
"""D2: verify frozen safety/warning stock through both workbench stages."""
import datetime
import decimal
import json
import pathlib
import subprocess
import time
import urllib.error
import urllib.request

from current_source_snapshot import snapshot

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-stock-policy-full-' + datetime.datetime.now().strftime('%H%M%S'))
OUT.mkdir(parents=True)
BASE = 'http://127.0.0.1:8182/api/v1'
MATERIAL = '6839AA000365'
FORECAST_ID = 38
CASES = (('safety', 47, 300, 200, 803, 'SAFETY_STOCK_BREACH', 100),
         ('warning', 49, 1200, 1003, 0, 'WARNING_STOCK_BREACH', 197))


def request(path, method='GET', body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
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


def data(path, token, method='GET', body=None, expected=200):
    status, result = request(path, method, body, token)
    assert status == expected and result.get('success'), (path, status, result)
    return result['data']


def await_task(batch_id, token):
    task = None
    for _ in range(90):
        time.sleep(1)
        task = data(f'/mrp/batches/{batch_id}/calculation-task', token)
        if task and task['status'] in ('SUCCEEDED', 'FAILED'):
            break
    assert task and task['status'] == 'SUCCEEDED', task
    return task


def number(value):
    return decimal.Decimal(str(value))


def main():
    status, login = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
    assert status == 200, login
    token = login['data']['token']
    policies = data(f'/mrp/stock-policies/page?warehouseCode=UAT_APK&keyword={MATERIAL}&pageSize=100', token)['items']
    matches = [row for row in policies if row['materialCode'] == MATERIAL]
    assert len(matches) == 1, matches
    original = matches[0]
    assert number(original['safetyStockQty']) == 0 and number(original['warningStockQty']) == 0
    policy_id = original['id']
    record = {'status': 'IN_PROGRESS', 'environment': '8182/3317/6391',
              'policyId': policy_id, 'forecastId': FORECAST_ID, 'beforeSource': snapshot(), 'cases': []}
    changed = False
    try:
        configured = dict(original)
        configured.update({'safetyStockQty': 100, 'warningStockQty': 250})
        record['configuredPolicy'] = data(f'/mrp/stock-policies/{policy_id}', token, 'PUT', configured)
        changed = True
        for name, inventory_id, available, used, net, risk_type, remaining in CASES:
            case = {'name': name, 'snapshotId': inventory_id, 'status': 'IN_PROGRESS'}
            record['cases'].append(case)
            batch_id = None
            try:
                batch = data(f'/mrp/batches?factoryCode=ALL&forecastVersionId={FORECAST_ID}&inventorySnapshotId={inventory_id}',
                             token, 'POST', b'')
                batch_id = batch['id']
                case['batchId'] = batch_id
                case['frozenStockPolicyVersionId'] = batch['stockPolicyVersionId']
                assert batch['status'] == 'DRAFT' and batch['stockPolicyVersionId'] is not None, batch
                pool = sql(f'SELECT available_qty,reserved_qty,warning_qty FROM mrp_inventory_pool '
                           f'WHERE batch_id={batch_id} AND factory_code="爱培科" '
                           f'AND warehouse_code="UAT_APK" AND material_code="{MATERIAL}";')
                assert len(pool) == 1 and [number(part) for part in pool[0].split('\t')] == [available, 100, 250], pool
                case['frozenPool'] = pool[0]
                assert data(f'/mrp/batches/{batch_id}/precheck', token, 'POST', b'')['status'] == 'READY_TO_CALCULATE'
                submitted = data(f'/mrp/batches/{batch_id}/calculate', token, 'POST', b'')
                case['machineTaskId'] = submitted['id']
                case['machineTask'] = await_task(batch_id, token)
                batch = data(f'/mrp/batches/{batch_id}', token)
                assert batch['status'] == 'SCHEDULE_READY', batch
                run_id = batch['activeRunId']
                case['runId'] = run_id
                details = sql(f'SELECT factory_code,gross_qty,inventory_used_qty,net_qty FROM mrp_requirement_detail '
                              f'WHERE batch_id={batch_id} AND run_id={run_id} AND current_material_code="{MATERIAL}" '
                              'ORDER BY factory_code;')
                apk = [row.split('\t') for row in details if row.startswith('爱培科\t')]
                yh = [row.split('\t') for row in details if row.startswith('永惠\t')]
                assert len(apk) == len(yh) == 1, details
                assert [number(x) for x in apk[0][1:]] == [1003, used, net], apk
                assert [number(x) for x in yh[0][1:]] == [10, 0, 10], yh
                case['machineDetails'] = details
                assert not [risk for risk in data(f'/mrp/inventory/stock-risks/{batch_id}', token)
                            if risk['materialCode'] == MATERIAL]
                review = data(f'/mrp/batches/{batch_id}/reviews', token)
                assert review['schedule'] == 'PENDING' and review['scheduleFingerprint'], review
                confirmed = data(f'/mrp/batches/{batch_id}/schedule-confirmation', token, 'POST',
                                 {'fingerprint': review['scheduleFingerprint']})
                assert confirmed['schedule'] == 'CONFIRMED', confirmed
                material_task = data(f'/mrp/batches/{batch_id}/material-analysis', token, 'POST', b'', expected=202)
                case['materialTaskId'] = material_task['id']
                case['materialTask'] = await_task(batch_id, token)
                batch = data(f'/mrp/batches/{batch_id}', token)
                assert batch['status'] == 'MATERIAL_REVIEW' and batch['activeRunId'] == run_id, batch
                risks = [row for row in data(f'/mrp/inventory/stock-risks/{batch_id}', token)
                         if row['materialCode'] == MATERIAL and row['factoryCode'] == '爱培科'
                         and row['warehouseCode'] == 'UAT_APK']
                assert len(risks) == 1, risks
                risk = risks[0]
                assert risk['riskType'] == risk_type, risk
                assert [number(risk[field]) for field in ('availableQty', 'usedQty', 'remainingQty',
                                                           'safetyStockQty', 'warningStockQty')] == [available, used, remaining, 100, 250], risk
                case['risk'] = risk
                case['status'] = 'PASS'
            finally:
                if batch_id is not None:
                    cancelled = data(f'/mrp/batches/{batch_id}/cancel', token, 'POST', b'')
                    case['finalBatchStatus'] = cancelled['status']
                    assert cancelled['status'] == 'CANCELLED', cancelled
    finally:
        if changed:
            record['restoredPolicy'] = data(f'/mrp/stock-policies/{policy_id}', token, 'PUT', original)
        record['afterSource'] = snapshot()
        record['protectedSourcesUnchanged'] = record['beforeSource']['tables'] == record['afterSource']['tables']
        record['status'] = 'PASS' if all(c['status'] == 'PASS' and c.get('finalBatchStatus') == 'CANCELLED'
                                         for c in record['cases']) and record['protectedSourcesUnchanged'] else 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    assert record['status'] == 'PASS', record
    print(json.dumps({'status': record['status'], 'evidence': str(OUT),
                      'batches': [c['batchId'] for c in record['cases']]}, ensure_ascii=False))


if __name__ == '__main__':
    main()
