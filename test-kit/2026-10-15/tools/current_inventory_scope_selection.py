#!/usr/bin/env python3
"""I06: partial, combined and extra-warehouse inventory snapshot selection."""
import datetime
import hashlib
import io
import json
import pathlib
import urllib.parse

import openpyxl

from current_material_cell_matrix import BASE, TABLES, call, count
from current_source_snapshot import snapshot

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-inventory-scope-' + datetime.datetime.now().strftime('%H%M%S'))
SEED = ROOT / 'evidence/runs/2026-11-03-inventory-quality-101530/inventory-quality.xlsx'
EXTRA = 'UAT_SCOPE_EXTRA_1103'
FORECAST = 45


def upload(name, blob, token):
    boundary = '----MRPInventoryScope20261103'
    header = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + name +
              '"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n')
    return call('/inventory-imports/items', 'POST',
                header.encode() + blob + ('\r\n--' + boundary + '--\r\n').encode(), token,
                'multipart/form-data; boundary=' + boundary)


def create_batch(token, snap, extra=''):
    path = f'/mrp/batches?factoryCode=ALL&forecastVersionId={FORECAST}&inventorySnapshotId={snap}' + extra
    http, response = call(path, 'POST', b'', token)
    return {'path': path, 'http': http, 'response': response}


def main():
    OUT.mkdir(parents=True)
    http, login = call('/auth/login', 'POST', json.dumps({'username': 'admin', 'password': 'admin123'}).encode())
    assert http == 200, login
    token = login['data']['token']
    before = {table: count(table) for table in TABLES}
    record = {'status': 'IN_PROGRESS', 'api': BASE, 'before': before, 'beforeSource': snapshot(), 'cases': []}
    mapping = None
    created_batch = None
    try:
        partial = create_batch(token, 56)
        record['cases'].append({'name': 'partial', **partial})
        assert partial['http'] == 400 and partial['response']['error']['code'] == 'INVENTORY_WAREHOUSE_MISMATCH', partial
        combination = create_batch(token, 56, '&inventorySnapshotIds=58')
        record['cases'].append({'name': 'combination', **combination})
        assert combination['http'] == 400 and combination['response']['error']['code'] == 'SINGLE_INVENTORY_VERSION_REQUIRED', combination
        assert before == {table: count(table) for table in TABLES}
        row = {'factoryCode': '爱培科', 'factoryName': '爱培科', 'warehouseCode': EXTRA,
               'warehouseName': '隔离范围外测试仓', 'enabled': True, 'goodWarehouse': True,
               'warehouseAttribute': '自有仓'}
        http, created = call('/mrp/factory-warehouse-mappings', 'POST', json.dumps(row).encode(), token)
        assert http == 200, created
        mapping = created['data']
        wb = openpyxl.load_workbook(SEED)
        wb.active.append([EXTRA, '隔离范围外测试仓', '结构库存', '6839AA000365',
                          '供应商', '1', OUT.name])
        buffer = io.BytesIO()
        wb.save(buffer)
        blob = buffer.getvalue()
        (OUT / 'inventory-extra-warehouse.xlsx').write_bytes(blob)
        record['fileSha256'] = hashlib.sha256(blob).hexdigest()
        http, staged = upload('inventory-extra-warehouse.xlsx', blob, token)
        record['preview'] = staged
        assert http == 202 and staged['data']['errorRows'] == 0, staged
        task = staged['data']['taskId']
        http, confirmed = call(f'/inventory-imports/items/{task}/confirm', 'POST', b'', token)
        assert http == 200 and confirmed.get('success'), confirmed
        record['extraSnapshotId'] = confirmed['data']['snapshotId']
        disabled = dict(mapping)
        disabled['enabled'] = False
        http, changed = call(f'/mrp/factory-warehouse-mappings/{mapping["id"]}', 'PUT',
                             json.dumps(disabled).encode(), token)
        assert http == 200 and not changed['data']['enabled'], changed
        mapping = changed['data']
        outside = create_batch(token, record['extraSnapshotId'])
        record['cases'].append({'name': 'outside-scope', **outside})
        if outside['http'] == 200:
            created_batch = outside['response']['data']['id']
        assert outside['http'] == 400 and outside['response']['error']['code'] == 'INVENTORY_WAREHOUSE_MISMATCH', outside
        record['status'] = 'PASS'
    except Exception as error:
        record['status'] = 'FAIL'
        record['error'] = str(error)
        raise
    finally:
        if created_batch is not None:
            http, cancelled = call(f'/mrp/batches/{created_batch}/cancel', 'POST', b'', token)
            record['unexpectedBatchCleanup'] = {'http': http, 'batch': cancelled}
            assert http == 200, cancelled
        if mapping is not None and mapping['enabled']:
            disabled = dict(mapping)
            disabled['enabled'] = False
            http, changed = call(f'/mrp/factory-warehouse-mappings/{mapping["id"]}', 'PUT',
                                 json.dumps(disabled).encode(), token)
            record['mappingCleanup'] = {'http': http, 'row': changed}
            assert http == 200 and not changed['data']['enabled'], changed
        record['after'] = {table: count(table) for table in TABLES}
        record['afterSource'] = snapshot()
        record['protectedSourcesUnchanged'] = record['beforeSource']['tables'] == record['afterSource']['tables']
        if not record['protectedSourcesUnchanged']:
            record['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    assert record['status'] == 'PASS'
    print(json.dumps({'status': 'PASS', 'evidence': str(OUT), 'snapshotId': record['extraSnapshotId']},
                     ensure_ascii=False))


if __name__ == '__main__':
    main()
