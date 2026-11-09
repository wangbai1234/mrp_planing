#!/usr/bin/env python3
"""B07 isolated API acceptance: source row order controls shared child stock."""
import datetime
import decimal
import hashlib
import json
import pathlib
import sys
import urllib.request

from current_source_snapshot import snapshot
from current_stock_policy_full_chain import request, data, await_task, sql

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-b07-shared-' + datetime.datetime.now().strftime('%H%M%S'))
BASE = 'http://127.0.0.1:8182/api/v1'
CHILD = '11520471'


def upload(filename, route, token):
    payload = (OUT / filename).read_bytes()
    boundary = '----MRPB07' + filename.replace('.', '')
    head = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + filename
            + '"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode()
    tail = ('\r\n--' + boundary + '--\r\n').encode()
    req = urllib.request.Request(BASE + route, data=head + payload + tail,
                                 headers={'Authorization': 'Bearer ' + token,
                                          'Content-Type': 'multipart/form-data; boundary=' + boundary})
    with urllib.request.urlopen(req, timeout=60) as response:
        staged = json.loads(response.read())
        assert response.status == 202 and staged['success'] and staged['data']['errorRows'] == 0, staged
    return staged['data']['taskId'], hashlib.sha256(payload).hexdigest()


def quantity(value):
    return decimal.Decimal(str(value))


def main():
    OUT.mkdir(parents=True)
    record = {'status': 'IN_PROGRESS', 'environment': '8182/3317/6391', 'beforeSource': snapshot(), 'cases': []}
    token = None
    try:
        from subprocess import run
        builder = pathlib.Path(__file__).with_name('build_b07_shared_stock.mjs')
        run(['node', str(builder), str(OUT)], check=True)
        http, auth = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
        assert http == 200 and auth['success'], auth
        token = auth['data']['token']
        policy = data(f'/mrp/stock-policies/page?warehouseCode=UAT_APK&keyword={CHILD}&pageSize=100', token)['items']
        assert len([p for p in policy if p['materialCode'] == CHILD and quantity(p['safetyStockQty']) == 0]) == 1, policy
        inventory_task, stock_sha = upload('inventory-shared.xlsx', '/inventory-imports/items', token)
        stock = data(f'/inventory-imports/items/{inventory_task}/confirm', token, 'POST', b'')
        snapshot_id = stock['snapshotId']
        record.update(inventoryTaskId=inventory_task, inventorySnapshotId=snapshot_id, inventorySha256=stock_sha)
        for filename, expected in [
            ('forecast-365-first.xlsx', {'6839AA000365': (10, 10, 0), '6839AA000362': (10, 5, 5)}),
            ('forecast-362-first.xlsx', {'6839AA000362': (10, 10, 0), '6839AA000365': (10, 5, 5)}),
        ]:
            case = {'file': filename, 'status': 'IN_PROGRESS'}
            record['cases'].append(case)
            batch_id = None
            try:
                forecast_task, checksum = upload(filename, '/forecast-imports', token)
                forecast = data(f'/forecast-imports/{forecast_task}/confirm', token, 'POST', b'')
                forecast_id = forecast['id']
                case.update(forecastTaskId=forecast_task, forecastVersionId=forecast_id, sha256=checksum)
                priority = sql(f'SELECT material_id,MIN(priority_seq) FROM forecast_detail WHERE version_id={forecast_id} '
                               'AND factory_code="爱培科" AND plan_month="2026-11-01" GROUP BY material_id ORDER BY MIN(priority_seq);')
                case['frozenPriority'] = priority
                assert [row.split('\t')[0] for row in priority] == list(expected), priority
                batch = data(f'/mrp/batches?factoryCode=ALL&forecastVersionId={forecast_id}&inventorySnapshotId={snapshot_id}',
                             token, 'POST', b'')
                batch_id = batch['id']
                case['batchId'] = batch_id
                pool = sql(f'SELECT available_qty,reserved_qty FROM mrp_inventory_pool WHERE batch_id={batch_id} '
                           f'AND warehouse_code="UAT_APK" AND material_code="{CHILD}";')
                assert len(pool) == 1 and [quantity(x) for x in pool[0].split('\t')] == [15, 0], pool
                case['pool'] = pool
                assert data(f'/mrp/batches/{batch_id}/precheck', token, 'POST', b'')['status'] == 'READY_TO_CALCULATE'
                case['scheduleTaskId'] = data(f'/mrp/batches/{batch_id}/calculate', token, 'POST', b'')['id']
                case['scheduleTask'] = await_task(batch_id, token)
                current = data(f'/mrp/batches/{batch_id}', token)
                assert current['status'] == 'SCHEDULE_READY', current
                run_id = current['activeRunId']
                case['runId'] = run_id
                reviews = data(f'/mrp/batches/{batch_id}/reviews', token)
                data(f'/mrp/batches/{batch_id}/schedule-confirmation', token, 'POST',
                     {'fingerprint': reviews['scheduleFingerprint']})
                case['materialTaskId'] = data(f'/mrp/batches/{batch_id}/material-analysis', token, 'POST', b'', expected=202)['id']
                case['materialTask'] = await_task(batch_id, token)
                current = data(f'/mrp/batches/{batch_id}', token)
                assert current['status'] == 'MATERIAL_REVIEW' and current['activeRunId'] == run_id, current
                rows = sql(f'SELECT root_material_code,gross_qty,inventory_used_qty,net_qty FROM mrp_requirement_detail '
                           f'WHERE batch_id={batch_id} AND run_id={run_id} AND current_material_code="{CHILD}" '
                           'AND factory_code="爱培科" AND source_month="2026-11-01" ORDER BY root_material_code;')
                case['childRequirements'] = rows
                actual = {parts[0]: tuple(quantity(x) for x in parts[1:]) for row in rows if (parts := row.split('\t'))}
                assert actual == expected, (actual, expected)
                allocations = sql(f'SELECT a.root_material_code,a.allocated_qty FROM mrp_inventory_allocation a '
                                  f'JOIN mrp_inventory_pool p ON p.id=a.pool_id WHERE p.batch_id={batch_id} '
                                  f'AND p.warehouse_code="UAT_APK" AND p.material_code="{CHILD}" ORDER BY a.root_material_code;')
                case['allocations'] = allocations
                assert sum(quantity(row.split('\t')[1]) for row in allocations) == 15, allocations
                assert all(gross == used + net for gross, used, net in actual.values())
                case['status'] = 'PASS'
            finally:
                if batch_id is not None:
                    case['finalBatchStatus'] = data(f'/mrp/batches/{batch_id}/cancel', token, 'POST', b'')['status']
        assert all(c['status'] == 'PASS' and c['finalBatchStatus'] == 'CANCELLED' for c in record['cases'])
        record['status'] = 'PASS'
    except Exception as error:
        record.update(status='FAIL', error=str(error))
        raise
    finally:
        record['afterSource'] = snapshot()
        record['protectedSourcesUnchanged'] = record['beforeSource']['tables'] == record['afterSource']['tables']
        if not record['protectedSourcesUnchanged']:
            record['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'status': record['status'], 'evidence': str(OUT),
                          'cases': [(c['file'], c['status']) for c in record['cases']]}, ensure_ascii=False))
    assert record['status'] == 'PASS', record


if __name__ == '__main__':
    main()
