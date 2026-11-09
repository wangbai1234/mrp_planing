#!/usr/bin/env python3
"""B08: multiple GOOD warehouses, factory isolation and machine-only day load."""
import datetime
import decimal
import hashlib
import json
import pathlib
import subprocess
import urllib.request

from current_source_snapshot import snapshot
from current_stock_policy_full_chain import request, data, await_task, sql

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-b08-multiwarehouse-' + datetime.datetime.now().strftime('%H%M%S'))
BASE = 'http://127.0.0.1:8182/api/v1'
CHILD = '11520471'
FORECAST_ID = 50


def quantity(value):
    return decimal.Decimal(str(value))


def upload(path, filename, token):
    payload = path.read_bytes()
    boundary = '----MRPB08Multiwarehouse'
    head = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + filename
            + '"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode()
    req = urllib.request.Request(BASE + '/inventory-imports/items', data=head + payload + ('\r\n--' + boundary + '--\r\n').encode(),
                                 headers={'Authorization': 'Bearer ' + token,
                                          'Content-Type': 'multipart/form-data; boundary=' + boundary})
    with urllib.request.urlopen(req, timeout=60) as response:
        result = json.loads(response.read())
        assert response.status == 202 and result['success'] and result['data']['errorRows'] == 0, result
    return result['data']['taskId'], hashlib.sha256(payload).hexdigest()


def main():
    OUT.mkdir(parents=True)
    record = {'status': 'IN_PROGRESS', 'environment': '8182/3317/6391', 'forecastVersionId': FORECAST_ID,
              'beforeSource': snapshot()}
    token = None
    mapping = None
    changed = False
    batch_id = None
    try:
        subprocess.run(['node', str(pathlib.Path(__file__).with_name('build_b07_shared_stock.mjs')), str(OUT)], check=True)
        http, login = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
        assert http == 200 and login['success'], login
        token = login['data']['token']
        mappings = data('/mrp/factory-warehouse-mappings', token)
        mapping = next(row for row in mappings if row['warehouseCode'] == 'UAT_SCOPE_EXTRA_1103')
        assert mapping['factoryCode'] == '爱培科' and mapping['goodWarehouse'] and not mapping['enabled'], mapping
        enabled = dict(mapping)
        enabled.update(enabled=True, priorityNo=2)
        updated = data(f'/mrp/factory-warehouse-mappings/{mapping["id"]}', token, 'PUT', enabled)
        assert updated['enabled'] and updated['priorityNo'] == 2, updated
        changed = True
        task_id, checksum = upload(OUT / 'inventory-multistock.xlsx', 'inventory-multistock.xlsx', token)
        stock = data(f'/inventory-imports/items/{task_id}/confirm', token, 'POST', b'')
        stock_id = stock['snapshotId']
        record.update(inventoryTaskId=task_id, inventorySnapshotId=stock_id, inventorySha256=checksum,
                      mappingBefore=mapping, mappingEnabled=updated)
        batch = data(f'/mrp/batches?factoryCode=ALL&forecastVersionId={FORECAST_ID}&inventorySnapshotId={stock_id}',
                     token, 'POST', b'')
        batch_id = batch['id']
        record['batchId'] = batch_id
        pools = sql(f'SELECT warehouse_code,available_qty,reserved_qty FROM mrp_inventory_pool WHERE batch_id={batch_id} '
                    f'AND factory_code="爱培科" AND material_code="{CHILD}" ORDER BY warehouse_code;')
        record['frozenPools'] = pools
        assert {r.split('\t')[0]: tuple(quantity(x) for x in r.split('\t')[1:]) for r in pools} == {
            'UAT_APK': (7, 0), 'UAT_SCOPE_EXTRA_1103': (8, 0)}, pools
        assert data(f'/mrp/batches/{batch_id}/precheck', token, 'POST', b'')['status'] == 'READY_TO_CALCULATE'
        record['scheduleTaskId'] = data(f'/mrp/batches/{batch_id}/calculate', token, 'POST', b'')['id']
        record['scheduleTask'] = await_task(batch_id, token)
        current = data(f'/mrp/batches/{batch_id}', token)
        assert current['status'] == 'SCHEDULE_READY', current
        run_id = current['activeRunId']
        record['runId'] = run_id
        loads = data(f'/mrp/batches/{batch_id}/capacity-loads?startDate=2026-10-01&endDate=2026-11-30', token)
        record['machineLoads'] = [row for row in loads if quantity(row['load']) > 0]
        assert record['machineLoads'] and all(row['factoryCode'] == '爱培科' and row['capacityBasis'] == 'MACHINE_OUTPUT'
                                                  for row in record['machineLoads']), record['machineLoads']
        assert sum(quantity(row['load']) for row in record['machineLoads']) == 20, record['machineLoads']
        reviews = data(f'/mrp/batches/{batch_id}/reviews', token)
        data(f'/mrp/batches/{batch_id}/schedule-confirmation', token, 'POST',
             {'fingerprint': reviews['scheduleFingerprint']})
        record['materialTaskId'] = data(f'/mrp/batches/{batch_id}/material-analysis', token, 'POST', b'', expected=202)['id']
        record['materialTask'] = await_task(batch_id, token)
        current = data(f'/mrp/batches/{batch_id}', token)
        assert current['status'] == 'MATERIAL_REVIEW', current
        allocations = sql(f'SELECT p.warehouse_code,a.root_material_code,a.allocated_qty FROM mrp_inventory_allocation a '
                          f'JOIN mrp_inventory_pool p ON p.id=a.pool_id WHERE p.batch_id={batch_id} '
                          f'AND p.material_code="{CHILD}" ORDER BY p.warehouse_code,a.root_material_code;')
        record['allocations'] = allocations
        by_warehouse = {}
        for row in allocations:
            warehouse, _, allocated = row.split('\t')
            by_warehouse[warehouse] = by_warehouse.get(warehouse, decimal.Decimal(0)) + quantity(allocated)
        assert by_warehouse == {'UAT_APK': 7, 'UAT_SCOPE_EXTRA_1103': 8}, by_warehouse
        details = sql(f'SELECT factory_code,SUM(gross_qty),SUM(inventory_used_qty),SUM(net_qty) '
                      f'FROM mrp_requirement_detail WHERE batch_id={batch_id} AND run_id={run_id} '
                      f'AND current_material_code="{CHILD}" GROUP BY factory_code;')
        record['childRequirements'] = details
        assert len(details) == 1 and details[0].split('\t')[0] == '爱培科', details
        assert [quantity(x) for x in details[0].split('\t')[1:]] == [20, 15, 5], details
        record['status'] = 'PASS'
    except Exception as error:
        record.update(status='FAIL', error=str(error))
        raise
    finally:
        if batch_id is not None:
            record['finalBatchStatus'] = data(f'/mrp/batches/{batch_id}/cancel', token, 'POST', b'')['status']
        if changed:
            record['mappingRestored'] = data(f'/mrp/factory-warehouse-mappings/{mapping["id"]}', token, 'PUT', mapping)
        record['afterSource'] = snapshot()
        record['protectedSourcesUnchanged'] = record['beforeSource']['tables'] == record['afterSource']['tables']
        if not record['protectedSourcesUnchanged'] or batch_id is not None and record.get('finalBatchStatus') != 'CANCELLED':
            record['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'status': record['status'], 'evidence': str(OUT), 'batchId': batch_id}, ensure_ascii=False))
    assert record['status'] == 'PASS', record


if __name__ == '__main__':
    main()
