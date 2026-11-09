#!/usr/bin/env python3
"""Verify root inventory is consumed once across two source months."""
import datetime
import decimal
import hashlib
import io
import json
import pathlib

import openpyxl
from current_source_snapshot import snapshot
from current_stock_policy_full_chain import request, data, await_task, sql

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-stock-cross-month-' + datetime.datetime.now().strftime('%H%M%S'))
OUT.mkdir(parents=True)
FORECAST = ROOT / 'evidence/runs/2026-11-02-bom-chain-20261103100337957612/forecast-bom.xlsx'
MATERIAL = '6839AA000365'
SNAPSHOT_ID = 47  # 300 GOOD in UAT_APK; policy must be reset to zero.


def qty(value):
    return decimal.Decimal(str(value))


def main():
    import urllib.request
    status, login = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
    assert status == 200, login
    token = login['data']['token']
    policy = data(f'/mrp/stock-policies/page?warehouseCode=UAT_APK&keyword={MATERIAL}&pageSize=100', token)['items']
    matching = [row for row in policy if row['materialCode'] == MATERIAL]
    assert len(matching) == 1 and qty(matching[0]['safetyStockQty']) == 0, matching
    record = {'status': 'IN_PROGRESS', 'environment': '8182/3317/6391', 'beforeSource': snapshot(),
              'snapshotId': SNAPSHOT_ID}
    batch_id = None
    try:
        book = openpyxl.load_workbook(FORECAST)
        target = next(row for row in book.active.iter_rows(min_row=3) if row[0].value == '爱培科')
        target[5].value = 200
        target[6].value = 200
        target[11].value = 400
        other = next(row for row in book.active.iter_rows(min_row=3) if row[0].value == '永惠')
        other[5].value = 10
        other[6].value = 10
        other[11].value = 20
        buffer = io.BytesIO()
        book.save(buffer)
        payload = buffer.getvalue()
        (OUT / 'forecast-two-months.xlsx').write_bytes(payload)
        record['forecastSha256'] = hashlib.sha256(payload).hexdigest()
        boundary = '----MRPStockCrossMonth20261103'
        head = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="forecast-two-months.xlsx"'
                '\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode()
        tail = ('\r\n--' + boundary + '--\r\n').encode()
        headers = {'Authorization': 'Bearer ' + token, 'Content-Type': 'multipart/form-data; boundary=' + boundary}
        req = urllib.request.Request('http://127.0.0.1:8182/api/v1/forecast-imports', data=head + payload + tail, headers=headers)
        with urllib.request.urlopen(req, timeout=60) as response:
            staged = json.loads(response.read())
            assert response.status == 202 and staged['data']['errorRows'] == 0, staged
        record['forecastTaskId'] = staged['data']['taskId']
        confirmed = data(f'/forecast-imports/{record["forecastTaskId"]}/confirm', token, 'POST', b'')
        forecast_id = confirmed['id']
        record['forecastVersionId'] = forecast_id
        batch = data(f'/mrp/batches?factoryCode=ALL&forecastVersionId={forecast_id}&inventorySnapshotId={SNAPSHOT_ID}',
                     token, 'POST', b'')
        batch_id = batch['id']
        record['batchId'] = batch_id
        assert batch['status'] == 'DRAFT', batch
        assert data(f'/mrp/batches/{batch_id}/precheck', token, 'POST', b'')['status'] == 'READY_TO_CALCULATE'
        record['taskId'] = data(f'/mrp/batches/{batch_id}/calculate', token, 'POST', b'')['id']
        record['task'] = await_task(batch_id, token)
        batch = data(f'/mrp/batches/{batch_id}', token)
        assert batch['status'] == 'SCHEDULE_READY', batch
        run_id = batch['activeRunId']
        record['runId'] = run_id
        rows = sql(f'SELECT factory_code,source_month,gross_qty,inventory_used_qty,net_qty FROM mrp_requirement_detail '
                   f'WHERE batch_id={batch_id} AND run_id={run_id} AND current_material_code="{MATERIAL}" '
                   'ORDER BY factory_code,source_month;')
        record['monthlyDetails'] = rows
        observed = {(p[0], p[1]): tuple(qty(x) for x in p[2:]) for row in rows if (p := row.split('\t'))}
        expected = {('爱培科', '2026-11-01'): (200, 200, 0),
                    ('爱培科', '2026-12-01'): (200, 100, 100),
                    ('永惠', '2026-11-01'): (10, 0, 10),
                    ('永惠', '2026-12-01'): (10, 0, 10)}
        assert observed == expected, (observed, expected)
        for gross, used, net in observed.values():
            assert gross == used + net and net >= 0
        allocation = sql(f'SELECT a.source_month,a.gross_qty,a.allocated_qty FROM mrp_inventory_allocation a '
                         f'JOIN mrp_inventory_pool p ON p.id=a.pool_id WHERE p.batch_id={batch_id} '
                         f'AND p.warehouse_code="UAT_APK" AND p.material_code="{MATERIAL}" ORDER BY a.source_month;')
        record['allocations'] = allocation
        assert sum(qty(row.split('\t')[2]) for row in allocation) == 300, allocation
        record['status'] = 'PASS'
    except Exception as error:
        record['status'] = 'FAIL'
        record['error'] = str(error)
        raise
    finally:
        if batch_id is not None:
            closed = data(f'/mrp/batches/{batch_id}/cancel', token, 'POST', b'')
            record['finalBatchStatus'] = closed['status']
        record['afterSource'] = snapshot()
        record['protectedSourcesUnchanged'] = record['beforeSource']['tables'] == record['afterSource']['tables']
        if not record['protectedSourcesUnchanged'] or (batch_id is not None and record['finalBatchStatus'] != 'CANCELLED'):
            record['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    assert record['status'] == 'PASS', record
    print(json.dumps({'status': 'PASS', 'evidence': str(OUT), 'batchId': batch_id}, ensure_ascii=False))


if __name__ == '__main__':
    main()
