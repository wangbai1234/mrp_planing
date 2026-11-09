#!/usr/bin/env python3
"""Verify missing, unknown, disabled, and changed warehouse import guards."""
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
OUT = ROOT / 'evidence/runs' / ('2026-11-03-warehouse-guards-' + datetime.datetime.now().strftime('%H%M%S'))
OUT.mkdir(parents=True)
STOCK = ROOT / 'evidence/runs/2026-11-03-inventory-quality-101530/inventory-quality.xlsx'
MATERIAL = '6839AA000365'


def upload(book, name, token):
    buffer = io.BytesIO()
    book.save(buffer)
    payload = buffer.getvalue()
    (OUT / name).write_bytes(payload)
    boundary = '----MRPWarehouseGuard20261103'
    head = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + name + '"'
            '\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode()
    tail = ('\r\n--' + boundary + '--\r\n').encode()
    req = urllib.request.Request('http://127.0.0.1:8182/api/v1/inventory-imports/items',
                                 data=head + payload + tail, headers={'Authorization': 'Bearer ' + token,
                                 'Content-Type': 'multipart/form-data; boundary=' + boundary})
    with urllib.request.urlopen(req, timeout=60) as response:
        return response.status, json.loads(response.read()), hashlib.sha256(payload).hexdigest()


def book_for(code, marker):
    book = openpyxl.load_workbook(STOCK)
    sheet = book.active
    sheet.delete_rows(3, sheet.max_row - 2)
    sheet.append([code, '测试仓', '结构库存', MATERIAL, '供应商', 10, marker])
    return book


def main():
    status, login = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
    assert status == 200, login
    token = login['data']['token']
    mappings = data('/mrp/factory-warehouse-mappings', token)
    selected = [row for row in mappings if row['warehouseCode'] == 'UAT_APK']
    assert len(selected) == 1 and selected[0]['enabled'] and selected[0]['qualityConfigured']
    original = selected[0]
    mapping_id = original['id']
    before = sql('SELECT COUNT(*) FROM inventory_snapshot; SELECT COUNT(*) FROM inventory_item;')
    record = {'status': 'IN_PROGRESS', 'beforeSource': snapshot(), 'beforeFormalCounts': before,
              'mappingId': mapping_id, 'cases': []}
    changed = False
    try:
        for name, code, expected in [('missing', None, 'REQUIRED_FIELD'),
                                      ('unknown', 'UNKNOWN_UAT_20261103', 'WAREHOUSE_NOT_CONFIGURED')]:
            status, response, sha = upload(book_for(code, name), name + '.xlsx', token)
            assert status == 202, response
            staged = response['data']
            errors = staged['errors']
            assert staged['errorRows'] == 1 and any(e['errorCode'] == expected and e['rowNumber'] == 3 for e in errors), errors
            denied, reason = request(f'/inventory-imports/items/{staged["taskId"]}/confirm', 'POST', b'', token)
            assert denied == 400, reason
            record['cases'].append({'name': name, 'status': 'PASS', 'sha256': sha, 'staging': staged,
                                    'confirmHttp': denied})
        disabled = dict(original)
        disabled['enabled'] = False
        updated = data(f'/mrp/factory-warehouse-mappings/{mapping_id}', token, 'PUT', disabled)
        changed = True
        assert not updated['enabled']
        status, response, sha = upload(book_for('UAT_APK', 'disabled'), 'disabled.xlsx', token)
        assert status == 202, response
        staged = response['data']
        assert staged['errorRows'] == 1 and any(e['errorCode'] == 'WAREHOUSE_NOT_CONFIGURED' for e in staged['errors'])
        denied, reason = request(f'/inventory-imports/items/{staged["taskId"]}/confirm', 'POST', b'', token)
        assert denied == 400, reason
        record['cases'].append({'name': 'disabled', 'status': 'PASS', 'sha256': sha, 'staging': staged,
                                'confirmHttp': denied})
        restored = data(f'/mrp/factory-warehouse-mappings/{mapping_id}', token, 'PUT', original)
        assert restored['enabled'] and restored['goodWarehouse']
        changed = False
        # The current API always sets qualityConfigured=true. Simulate a legacy
        # unconfirmed mapping only in the isolated test database, then restore
        # it through the audited configuration API.
        sql(f'UPDATE factory_warehouse_mapping SET quality_configured=0 WHERE id={mapping_id};')
        changed = True
        status, response, sha = upload(book_for('UAT_APK', 'unconfirmed'), 'unconfirmed.xlsx', token)
        assert status == 202, response
        staged = response['data']
        assert staged['errorRows'] == 1 and any(e['errorCode'] == 'WAREHOUSE_NOT_CONFIGURED' for e in staged['errors'])
        denied, reason = request(f'/inventory-imports/items/{staged["taskId"]}/confirm', 'POST', b'', token)
        assert denied == 400, reason
        record['cases'].append({'name': 'unconfirmed-legacy-fixture', 'status': 'PASS', 'sha256': sha,
                                'staging': staged, 'confirmHttp': denied})
        restored = data(f'/mrp/factory-warehouse-mappings/{mapping_id}', token, 'PUT', original)
        assert restored['qualityConfigured']
        changed = False
        status, response, sha = upload(book_for('UAT_APK', 'changed-after-preview'), 'changed-after-preview.xlsx', token)
        assert status == 202 and response['data']['errorRows'] == 0, response
        task_id = response['data']['taskId']
        changed_quality = dict(original)
        changed_quality['goodWarehouse'] = False
        quality = data(f'/mrp/factory-warehouse-mappings/{mapping_id}', token, 'PUT', changed_quality)
        changed = True
        assert not quality['goodWarehouse']
        denied, reason = request(f'/inventory-imports/items/{task_id}/confirm', 'POST', b'', token)
        assert denied == 400, reason
        record['cases'].append({'name': 'quality-changed-after-preview', 'status': 'PASS', 'sha256': sha,
                                'taskId': task_id, 'confirmHttp': denied, 'reason': reason})
        after = sql('SELECT COUNT(*) FROM inventory_snapshot; SELECT COUNT(*) FROM inventory_item;')
        assert before == after, (before, after)
        record['afterFormalCounts'] = after
        record['status'] = 'PASS'
    except Exception as error:
        record['status'] = 'FAIL'
        record['error'] = str(error)
        raise
    finally:
        if changed:
            record['restoredMapping'] = data(f'/mrp/factory-warehouse-mappings/{mapping_id}', token, 'PUT', original)
        record['afterSource'] = snapshot()
        record['protectedSourcesUnchanged'] = record['beforeSource']['tables'] == record['afterSource']['tables']
        if not record['protectedSourcesUnchanged']:
            record['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    assert record['status'] == 'PASS'
    print(json.dumps({'status': 'PASS', 'evidence': str(OUT)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
