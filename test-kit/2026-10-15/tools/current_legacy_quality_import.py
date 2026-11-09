#!/usr/bin/env python3
"""Check legacy quality-column compatibility and mismatched warehouse blocking."""
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
OUT = ROOT / 'evidence/runs' / ('2026-11-03-legacy-quality-' + datetime.datetime.now().strftime('%H%M%S'))
OUT.mkdir(parents=True)
STOCK = ROOT / 'evidence/runs/2026-11-03-inventory-quality-101530/inventory-quality.xlsx'
MATERIAL = '6839AA000365'


def upload(book, name, token):
    buffer = io.BytesIO()
    book.save(buffer)
    payload = buffer.getvalue()
    (OUT / name).write_bytes(payload)
    boundary = '----MRPLegacyQuality20261103'
    head = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + name + '"'
            '\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode()
    tail = ('\r\n--' + boundary + '--\r\n').encode()
    req = urllib.request.Request('http://127.0.0.1:8182/api/v1/inventory-imports/items',
                                 data=head + payload + tail, headers={'Authorization': 'Bearer ' + token,
                                 'Content-Type': 'multipart/form-data; boundary=' + boundary})
    with urllib.request.urlopen(req, timeout=60) as response:
        return response.status, json.loads(response.read()), hashlib.sha256(payload).hexdigest()


def legacy_book(quality):
    book = openpyxl.Workbook()
    sheet = book.active
    sheet.title = '库存导入'
    sheet.append(['仓库编码*', '仓库名称', '库存分类', '料号*', '库存品质*', '供应商名称', '库存数量*', '备注'])
    sheet.append(['填写说明'])
    sheet.append(['UAT_APK', '测试仓', '结构库存', MATERIAL, quality, 'UAT供应商', 10, '旧模板品质兼容'])
    return book


def main():
    status, login = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
    assert status == 200, login
    token = login['data']['token']
    before = sql('SELECT COUNT(*) FROM inventory_snapshot; SELECT COUNT(*) FROM inventory_item;')
    record = {'status': 'IN_PROGRESS', 'beforeSource': snapshot(), 'beforeFormalCounts': before, 'cases': []}
    try:
        new_book = openpyxl.load_workbook(STOCK)
        record['newTemplateHeaders'] = [c.value for c in new_book.active[1]]
        assert len(record['newTemplateHeaders']) == 7 and not any('品质' in str(x) for x in record['newTemplateHeaders'])
        for name, quality, expected_error in [('matching', '良品', None), ('mismatched', '不良品', 'QUALITY_MISMATCH')]:
            status, response, sha = upload(legacy_book(quality), 'legacy-' + name + '.xlsx', token)
            assert status == 202, response
            staged = response['data']
            case = {'name': name, 'sha256': sha, 'taskId': staged['taskId'], 'preview': staged}
            record['cases'].append(case)
            if expected_error:
                assert staged['errorRows'] == 1 and any(error['errorCode'] == expected_error and error['rowNumber'] == 3
                                                       for error in staged['errors']), staged
                denied, reason = request(f'/inventory-imports/items/{staged["taskId"]}/confirm', 'POST', b'', token)
                assert denied == 400, reason
                case['confirmHttp'] = denied
                case['status'] = 'PASS'
            else:
                assert staged['errorRows'] == 0 and staged['successRows'] == 1, staged
                confirmed = data(f'/inventory-imports/items/{staged["taskId"]}/confirm', token, 'POST', b'')
                case['snapshotId'] = confirmed['snapshotId']
                rows = sql(f'SELECT inventory_quality,shipping_available_qty FROM inventory_item '
                           f'WHERE snapshot_id={confirmed["snapshotId"]} AND material_id="{MATERIAL}";')
                assert rows == ['GOOD\t10.000000'], rows
                case['formalRows'] = rows
                case['status'] = 'PASS'
        after = sql('SELECT COUNT(*) FROM inventory_snapshot; SELECT COUNT(*) FROM inventory_item;')
        record['afterFormalCounts'] = after
        assert [int(after[0]) - int(before[0]), int(after[1]) - int(before[1])] == [1, 1], (before, after)
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
