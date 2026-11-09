#!/usr/bin/env python3
"""Verify mixed GOOD/DEFECTIVE import in the isolated test environment."""
import datetime
import hashlib
import io
import json
import pathlib
import urllib.error
import urllib.request

import openpyxl

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'evidence/runs/2026-11-02-bom-chain-20261103100337957612/inventory-bom.xlsx'
OUT = ROOT / 'evidence/runs' / ('2026-11-03-inventory-quality-' + datetime.datetime.now().strftime('%H%M%S'))
OUT.mkdir(parents=True)
BASE = 'http://127.0.0.1:8182/api/v1'

def request(path, method='GET', body=None, token=None, content_type='application/json'):
    headers = {'Content-Type': content_type}
    if token: headers['Authorization'] = 'Bearer ' + token
    payload = json.dumps(body).encode() if isinstance(body, dict) else body
    req = urllib.request.Request(BASE + path, data=payload, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())

def upload(blob, token):
    boundary = '----MRPQuality20261103'
    head = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="inventory-quality.xlsx"\r\n'
            'Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode()
    tail = ('\r\n--' + boundary + '--\r\n').encode()
    return request('/inventory-imports/items', 'POST', head + blob + tail, token, 'multipart/form-data; boundary=' + boundary)

status, login = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
assert status == 200, login
token = login['data']['token']
status, mapping = request('/mrp/factory-warehouse-mappings', token=token)
assert status == 200, mapping
warehouses = {row['warehouseCode']: row for row in mapping['data']}
assert all(warehouses[code]['goodWarehouse'] and warehouses[code]['qualityConfigured']
           for code in ('UAT_APK', 'UAT_YH'))
if 'UAT_APK_DEF' not in warehouses:
    status, created = request('/mrp/factory-warehouse-mappings', 'POST', {
        'factoryCode': '爱培科', 'warehouseCode': 'UAT_APK_DEF', 'warehouseName': '隔离测试不良品仓',
        'goodWarehouse': False, 'warehouseAttribute': '自有仓', 'enabled': True
    }, token)
    assert status == 200 and created['data']['goodWarehouse'] is False, created
    defective = created['data']
else:
    defective = warehouses['UAT_APK_DEF']
    assert defective['goodWarehouse'] is False and defective['qualityConfigured'] and defective['enabled']

status, before = request('/inventory-snapshots/page?page=1&pageSize=3', token=token)
assert status == 200
before_count = before['data']['total']
book = openpyxl.load_workbook(SOURCE)
book.active.append(['UAT_APK_DEF', '隔离测试不良品仓', '结构库存', '6839AA000365',
                    'UAT供应商', 1000, '不良品不得参与扣减'])
content = io.BytesIO()
book.save(content)
blob = content.getvalue()
(OUT / 'inventory-quality.xlsx').write_bytes(blob)
status, preview = upload(blob, token)
assert status == 202 and preview['data']['errorRows'] == 0, preview
assert preview['data']['totalRows'] == 165, preview
status, middle = request('/inventory-snapshots/page?page=1&pageSize=3', token=token)
assert status == 200 and middle['data']['total'] == before_count
task_id = preview['data']['taskId']
status, confirmed = request(f'/inventory-imports/items/{task_id}/confirm', 'POST', b'', token)
assert status == 200, confirmed
snapshot_id = confirmed['data']['snapshotId']
status, after = request('/inventory-snapshots/page?page=1&pageSize=3', token=token)
assert status == 200 and after['data']['total'] == before_count + 1
rows = []
for page in (1, 2):
    status, response = request(f'/inventory-snapshots/{snapshot_id}?page={page}&pageSize=100', token=token)
    assert status == 200, response
    rows.extend(response['data']['items'])
assert len(rows) == 165
assert sum(row['inventoryQuality'] == 'GOOD' for row in rows) == 164
bad = [row for row in rows if row['inventoryQuality'] == 'DEFECTIVE']
assert len(bad) == 1 and bad[0]['warehouseCode'] == 'UAT_APK_DEF'
assert str(bad[0]['inventoryQty']) in ('1000', '1000.0', '1000.000000')
result = {'status': 'PASS', 'environment': '8182/3317', 'warehouseId': defective['id'],
          'forecastBatchUntouched': 79, 'beforeSnapshotCount': before_count,
          'afterSnapshotCount': after['data']['total'], 'taskId': task_id,
          'snapshotId': snapshot_id, 'previewRows': preview['data']['totalRows'],
          'goodRows': 164, 'defectiveRows': bad, 'sha256': hashlib.sha256(blob).hexdigest()}
(OUT / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({'evidence': str(OUT), 'status': 'PASS', 'snapshotId': snapshot_id,
                  'goodRows': 164, 'defectiveRows': len(bad)}, ensure_ascii=False))
