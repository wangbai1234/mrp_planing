#!/usr/bin/env python3
"""Exercise disabled-factory forecast import; restore the isolated factory via API."""
import datetime
import hashlib
import io
import json
import pathlib
from urllib.parse import quote

import openpyxl

from current_material_cell_matrix import SEED, TABLES, call, count
from current_source_snapshot import snapshot

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-disabled-factory-' + datetime.datetime.now().strftime('%H%M%S'))


def main():
    OUT.mkdir(parents=True)
    http, auth = call('/auth/login', 'POST', json.dumps({'username': 'admin', 'password': 'admin123'}).encode())
    assert http == 200, auth
    token = auth['data']['token']
    http, factories = call('/mrp/factory-master', token=token)
    assert http == 200, factories
    rows = factories['data']
    original = next((row for row in rows if row['factoryCode'] == '爱培科'), None)
    assert original and original['enabled'], original
    factory_path = '/mrp/factory-master/' + quote(original['factoryCode'])
    before = {table: count(table) for table in TABLES}
    record = {'status': 'IN_PROGRESS', 'before': before, 'factoryBefore': original,
              'beforeSource': snapshot()}
    disabled = False
    try:
        update = {'factoryCode': original['factoryCode'], 'factoryName': original['factoryName'], 'enabled': False}
        http, response = call(factory_path, 'PUT', json.dumps(update).encode(), token)
        assert http == 200 and not response['data']['enabled'], response
        disabled = True
        record['disabledResponse'] = response
        wb = openpyxl.load_workbook(SEED / 'forecast-current.xlsx')
        sheet = wb.active
        sheet.delete_rows(4, sheet.max_row - 3)
        sheet['A3'] = '爱培科'
        buffer = io.BytesIO()
        wb.save(buffer)
        blob = buffer.getvalue()
        (OUT / 'forecast-disabled-factory.xlsx').write_bytes(blob)
        record['sha256'] = hashlib.sha256(blob).hexdigest()
        boundary = '----MRPDisabledFactory20261103'
        head = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="forecast-disabled-factory.xlsx"'
                '\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n')
        http, preview = call('/forecast-imports', 'POST',
                             head.encode() + blob + ('\r\n--' + boundary + '--\r\n').encode(), token,
                             'multipart/form-data; boundary=' + boundary)
        record['uploadHttp'] = http
        record['preview'] = preview
        data = preview.get('data') or {}
        assert http == 202 and data.get('errorRows') == 1, preview
        assert any(error['rowNumber'] == 3 and error['column'] == 'factory_code'
                   and error['originalValue'] == '爱培科' and error['errorCode'] == 'INVALID_FACTORY'
                   for error in data['errors']), data['errors']
        denied, result = call(f'/forecast-imports/{data["taskId"]}/confirm', 'POST', b'', token)
        record['confirmHttp'] = denied
        record['confirmResult'] = result
        assert denied == 400, result
        assert before == {table: count(table) for table in TABLES}
        record['status'] = 'PASS'
    except Exception as error:
        record['status'] = 'FAIL'
        record['error'] = str(error)
        raise
    finally:
        if disabled:
            restored = {'factoryCode': original['factoryCode'], 'factoryName': original['factoryName'],
                        'enabled': original['enabled']}
            http, response = call(factory_path, 'PUT', json.dumps(restored).encode(), token)
            record['restoreHttp'] = http
            record['factoryAfter'] = response
            assert http == 200 and response['data']['enabled'], response
        record['after'] = {table: count(table) for table in TABLES}
        record['afterSource'] = snapshot()
        record['protectedSourcesUnchanged'] = record['beforeSource']['tables'] == record['afterSource']['tables']
        if record['after'] != before or not record['protectedSourcesUnchanged']:
            record['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    assert record['status'] == 'PASS'
    print(json.dumps({'status': 'PASS', 'evidence': str(OUT)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
