#!/usr/bin/env python3
"""Verify leading-zero material codes survive both Excel import pipelines."""
import datetime
import hashlib
import io
import json
import pathlib
import urllib.request

import openpyxl
from current_source_snapshot import snapshot
from current_stock_policy_full_chain import request, data, sql

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-leading-zero-' + datetime.datetime.now().strftime('%H%M%S'))
OUT.mkdir(parents=True)
FORECAST = ROOT / 'evidence/runs/2026-11-02-bom-chain-20261103100337957612/forecast-bom.xlsx'
STOCK = ROOT / 'evidence/runs/2026-11-03-inventory-quality-101530/inventory-quality.xlsx'
CODE = '0209006'


def upload(book, name, route, token):
    buffer = io.BytesIO()
    book.save(buffer)
    payload = buffer.getvalue()
    (OUT / name).write_bytes(payload)
    boundary = '----MRPLeadingZero20261103'
    head = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + name + '"'
            '\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode()
    tail = ('\r\n--' + boundary + '--\r\n').encode()
    req = urllib.request.Request('http://127.0.0.1:8182/api/v1' + route, data=head + payload + tail,
                                 headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'multipart/form-data; boundary=' + boundary})
    with urllib.request.urlopen(req, timeout=60) as response:
        return response.status, json.loads(response.read()), hashlib.sha256(payload).hexdigest()


def main():
    status, login = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
    assert status == 200, login
    token = login['data']['token']
    original = sql('SELECT COUNT(*) FROM forecast_version; SELECT COUNT(*) FROM inventory_snapshot;')
    assert sql(f'SELECT COUNT(*) FROM material WHERE material_code="{CODE}" AND is_active=1 AND is_deleted=0;') == ['1']
    record = {'status': 'IN_PROGRESS', 'beforeSource': snapshot(), 'beforeFormalCounts': original, 'cases': []}
    try:
        forecast = openpyxl.load_workbook(FORECAST)
        sheet = forecast.active
        sheet.delete_rows(4, sheet.max_row - 3)
        sheet['C3'] = CODE
        sheet['C3'].number_format = '@'
        sheet['F3'] = 0
        sheet['L3'] = 0
        status, staged, sha = upload(forecast, 'forecast-leading-zero.xlsx', '/forecast-imports', token)
        assert status == 202 and staged['data']['errorRows'] == 0, staged
        task_id = staged['data']['taskId']
        forecast_version = data(f'/forecast-imports/{task_id}/confirm', token, 'POST', b'')
        rows = sql(f'SELECT material_id,COUNT(*) FROM forecast_detail WHERE version_id={forecast_version["id"]} GROUP BY material_id;')
        assert rows == [CODE + '\t6'], rows
        record['cases'].append({'name': 'forecast', 'status': 'PASS', 'taskId': task_id, 'versionId': forecast_version['id'],
                                'sha256': sha, 'materialRows': rows})
        inventory = openpyxl.load_workbook(STOCK)
        sheet = inventory.active
        sheet.delete_rows(3, sheet.max_row - 2)
        sheet.append(['UAT_APK', '测试仓', '结构库存', CODE, '供应商', '1.123456', '前导零与六位精度'])
        sheet['D3'].number_format = '@'
        status, staged, sha = upload(inventory, 'inventory-leading-zero.xlsx', '/inventory-imports/items', token)
        assert status == 202 and staged['data']['errorRows'] == 0, staged
        task_id = staged['data']['taskId']
        stock = data(f'/inventory-imports/items/{task_id}/confirm', token, 'POST', b'')
        rows = sql(f'SELECT material_id,shipping_available_qty,inventory_quality FROM inventory_item '
                   f'WHERE snapshot_id={stock["snapshotId"]};')
        assert rows == [CODE + '\t1.123456\tGOOD'], rows
        record['cases'].append({'name': 'inventory', 'status': 'PASS', 'taskId': task_id, 'snapshotId': stock['snapshotId'],
                                'sha256': sha, 'materialRows': rows})
        after = sql('SELECT COUNT(*) FROM forecast_version; SELECT COUNT(*) FROM inventory_snapshot;')
        record['afterFormalCounts'] = after
        assert [int(after[i]) - int(original[i]) for i in range(2)] == [1, 1], (original, after)
        record['status'] = 'PASS'
    except Exception as error:
        record['status'] = 'FAIL'
        record['error'] = str(error)
        raise
    finally:
        record['afterSource'] = snapshot()
        record['protectedSourcesUnchanged'] = record['beforeSource']['tables'] == record['afterSource']['tables']
        if not record['protectedSourcesUnchanged']:
            record['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    assert record['status'] == 'PASS'
    print(json.dumps({'status': 'PASS', 'evidence': str(OUT)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
