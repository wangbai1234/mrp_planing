#!/usr/bin/env python3
"""Verify duplicate supplier rows aggregate once while raw inventory stays intact."""
import datetime
import decimal
import hashlib
import io
import json
import pathlib
import urllib.request

import openpyxl
from current_source_snapshot import snapshot
from current_stock_policy_full_chain import request, data, await_task, sql

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-stock-duplicate-' + datetime.datetime.now().strftime('%H%M%S'))
OUT.mkdir(parents=True)
STOCK = ROOT / 'evidence/runs/2026-11-03-inventory-quality-101530/inventory-quality.xlsx'
MATERIAL = '6839AA000365'


def qty(value):
    return decimal.Decimal(str(value))


def main():
    status, login = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
    assert status == 200, login
    token = login['data']['token']
    record = {'status': 'IN_PROGRESS', 'environment': '8182/3317/6391', 'beforeSource': snapshot()}
    batch_id = None
    try:
        book = openpyxl.load_workbook(STOCK)
        root = next(row for row in book.active.iter_rows(min_row=3)
                    if row[0].value == 'UAT_APK' and row[3].value == MATERIAL)
        root[4].value = '供应商甲'
        root[5].value = 100
        duplicate = [cell.value for cell in root]
        duplicate[4] = '供应商乙'
        duplicate[5] = 200
        book.active.append(duplicate)
        buffer = io.BytesIO()
        book.save(buffer)
        payload = buffer.getvalue()
        (OUT / 'stock-duplicate-suppliers.xlsx').write_bytes(payload)
        record['stockSha256'] = hashlib.sha256(payload).hexdigest()
        boundary = '----MRPStockDuplicate20261103'
        head = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="stock-duplicate-suppliers.xlsx"'
                '\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode()
        tail = ('\r\n--' + boundary + '--\r\n').encode()
        req = urllib.request.Request('http://127.0.0.1:8182/api/v1/inventory-imports/items',
                                     data=head + payload + tail, headers={'Authorization': 'Bearer ' + token,
                                     'Content-Type': 'multipart/form-data; boundary=' + boundary})
        with urllib.request.urlopen(req, timeout=60) as response:
            staged = json.loads(response.read())
            assert response.status == 202 and staged['data']['errorRows'] == 0, staged
        record['inventoryTaskId'] = staged['data']['taskId']
        confirmed = data(f'/inventory-imports/items/{record["inventoryTaskId"]}/confirm', token, 'POST', b'')
        snapshot_id = confirmed['snapshotId']
        record['snapshotId'] = snapshot_id
        raw = sql(f'SELECT warehouse_code,supplier_name,shipping_available_qty,inventory_quality FROM inventory_item '
                  f'WHERE snapshot_id={snapshot_id} AND material_id="{MATERIAL}" ORDER BY warehouse_code,supplier_name;')
        record['rawRows'] = raw
        apk = [row.split('\t') for row in raw if row.startswith('UAT_APK\t')]
        assert len(apk) == 2 and {r[1]: qty(r[2]) for r in apk} == {'供应商甲': 100, '供应商乙': 200}, raw
        assert any(row.startswith('UAT_APK_DEF\t') and qty(row.split('\t')[2]) == 1000 for row in raw), raw
        batch = data(f'/mrp/batches?factoryCode=ALL&forecastVersionId=38&inventorySnapshotId={snapshot_id}',
                     token, 'POST', b'')
        batch_id = batch['id']
        record['batchId'] = batch_id
        balance = sql(f'SELECT warehouse_scope,inventory_quality,available_quantity,included_flag FROM mrp_inventory_balance '
                      f'WHERE inventory_snapshot_id={snapshot_id} AND material_code="{MATERIAL}" ORDER BY warehouse_scope;')
        record['balances'] = balance
        assert any(row == 'UAT_APK\tGOOD\t300.000000\t1' for row in balance), balance
        assert any(row == 'UAT_APK_DEF\tDEFECTIVE\t1000.000000\t0' for row in balance), balance
        pools = sql(f'SELECT warehouse_code,available_qty FROM mrp_inventory_pool WHERE batch_id={batch_id} '
                    f'AND material_code="{MATERIAL}" ORDER BY warehouse_code;')
        record['pools'] = pools
        assert any(row == 'UAT_APK\t300.000000' for row in pools) and not any('UAT_APK_DEF' in row for row in pools), pools
        assert data(f'/mrp/batches/{batch_id}/precheck', token, 'POST', b'')['status'] == 'READY_TO_CALCULATE'
        record['taskId'] = data(f'/mrp/batches/{batch_id}/calculate', token, 'POST', b'')['id']
        record['task'] = await_task(batch_id, token)
        batch = data(f'/mrp/batches/{batch_id}', token)
        assert batch['status'] == 'SCHEDULE_READY', batch
        record['runId'] = batch['activeRunId']
        details = sql(f'SELECT factory_code,gross_qty,inventory_used_qty,net_qty FROM mrp_requirement_detail '
                      f'WHERE batch_id={batch_id} AND run_id={record["runId"]} AND current_material_code="{MATERIAL}" ORDER BY factory_code;')
        record['requirements'] = details
        observed = {r[0]: tuple(qty(x) for x in r[1:]) for row in details if (r := row.split('\t'))}
        assert observed == {'爱培科': (1003, 300, 703), '永惠': (10, 0, 10)}, observed
        record['status'] = 'PASS'
    except Exception as error:
        record['status'] = 'FAIL'
        record['error'] = str(error)
        raise
    finally:
        if batch_id is not None:
            record['finalBatchStatus'] = data(f'/mrp/batches/{batch_id}/cancel', token, 'POST', b'')['status']
        record['afterSource'] = snapshot()
        record['protectedSourcesUnchanged'] = record['beforeSource']['tables'] == record['afterSource']['tables']
        if not record['protectedSourcesUnchanged'] or (batch_id is not None and record['finalBatchStatus'] != 'CANCELLED'):
            record['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    assert record['status'] == 'PASS'
    print(json.dumps({'status': 'PASS', 'evidence': str(OUT), 'snapshotId': snapshot_id,
                      'batchId': batch_id}, ensure_ascii=False))


if __name__ == '__main__':
    main()
