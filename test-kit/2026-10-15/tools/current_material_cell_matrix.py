#!/usr/bin/env python3
"""Check material-cell typing and hidden/sparse rows on the isolated API only."""
import datetime
import hashlib
import io
import json
import pathlib
import subprocess
import urllib.error
import urllib.request

import openpyxl

ROOT = pathlib.Path(__file__).resolve().parents[1]
SEED = ROOT / 'evidence/runs/2026-11-02-positive-chain'
OUT = ROOT / 'evidence/runs' / ('2026-11-03-material-cell-' + datetime.datetime.now().strftime('%H%M%S'))
BASE = 'http://127.0.0.1:8182/api/v1'
TABLES = ('forecast_version', 'forecast_detail', 'inventory_snapshot', 'inventory_item')


def count(table):
    assert table in TABLES
    proc = subprocess.run(['docker', 'exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
                           'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B -u"$MYSQL_USER" mrp_testkit'],
                          input=f'SELECT COUNT(*) FROM {table};', text=True, capture_output=True, check=True)
    return int(proc.stdout.strip())


def call(path, method='GET', payload=None, token=None, content_type='application/json'):
    headers = {'Content-Type': content_type}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=payload, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


def book(name):
    wb = openpyxl.load_workbook(SEED / name)
    wb.active.delete_rows(4, wb.active.max_row - 3)
    return wb


def upload(route, name, wb, token):
    buffer = io.BytesIO()
    wb.save(buffer)
    blob = buffer.getvalue()
    (OUT / name).write_bytes(blob)
    boundary = '----MRPMaterialCell20261103'
    head = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' +
            name + '"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n')
    result = call(route, 'POST', head.encode() + blob + ('\r\n--' + boundary + '--\r\n').encode(),
                  token, 'multipart/form-data; boundary=' + boundary)
    return *result, hashlib.sha256(blob).hexdigest()


def hidden_bad(wb):
    wb.active.row_dimensions[3].hidden = True
    wb.active['F3'] = 'not-a-number'


def sparse_bad(wb):
    wb.active['F3'] = None
    wb.active['G3'] = 'not-a-number'


CASES = [
    ('F03_empty', 'forecast-current.xlsx', '/forecast-imports', lambda w: setattr(w.active['C3'], 'value', None), 'REQUIRED_FIELD', 'material_id', ''),
    ('F03_unknown', 'forecast-current.xlsx', '/forecast-imports', lambda w: setattr(w.active['C3'], 'value', 'NO_MRP_MATERIAL'), 'MATERIAL_NOT_FOUND', 'material_id', 'NO_MRP_MATERIAL'),
    ('F03_numeric_zero', 'forecast-current.xlsx', '/forecast-imports', lambda w: setattr(w.active['C3'], 'value', 209006), 'INVALID_MATERIAL_ID', 'material_id', '209006'),
    ('F03_numeric_long', 'forecast-current.xlsx', '/forecast-imports', lambda w: setattr(w.active['C3'], 'value', 12345678901234567), 'INVALID_MATERIAL_ID', 'material_id', None),
    ('I07_numeric_zero', 'inventory-current.xlsx', '/inventory-imports/items', lambda w: setattr(w.active['D3'], 'value', 209006), 'INVALID_MATERIAL_ID', 'material_id', '209006'),
    ('I07_numeric_long', 'inventory-current.xlsx', '/inventory-imports/items', lambda w: setattr(w.active['D3'], 'value', 12345678901234567), 'INVALID_MATERIAL_ID', 'material_id', None),
    ('F08_hidden', 'forecast-current.xlsx', '/forecast-imports', hidden_bad, 'INVALID_QTY', None, 'not-a-number'),
    ('F08_sparse', 'forecast-current.xlsx', '/forecast-imports', sparse_bad, 'INVALID_QTY', None, 'not-a-number'),
]


def main():
    OUT.mkdir(parents=True)
    assert urllib.request.urlopen('http://127.0.0.1:8182/actuator/health', timeout=5).status == 200
    assert subprocess.run(['docker', 'inspect', 'mrp-testkit-mysql'], capture_output=True).returncode == 0
    status, auth = call('/auth/login', 'POST', json.dumps({'username': 'admin', 'password': 'admin123'}).encode())
    assert status == 200, auth
    token = auth['data']['token']
    before = {table: count(table) for table in TABLES}
    cases = []
    try:
        for name, file, route, mutate, expected_code, expected_col, expected_value in CASES:
            wb = book(file)
            mutate(wb)
            http, response, sha = upload(route, name + '.xlsx', wb, token)
            stage = response.get('data') or {}
            errors = stage.get('errors') or []
            task = stage.get('taskId')
            confirm = None
            if task and stage.get('errorRows', 0) > 0:
                confirm, _ = call(route + '/' + str(task) + '/confirm', 'POST', b'', token)
            match = [e for e in errors if e.get('errorCode') == expected_code and e.get('rowNumber') == 3
                     and (expected_col is None or e.get('column') == expected_col)
                     and (expected_value is None or e.get('originalValue') == expected_value)]
            cases.append({'name': name, 'status': 'PASS' if http == 202 and match and confirm == 400 else 'FAIL',
                          'sha256': sha, 'uploadHttp': http, 'taskId': task, 'errors': errors, 'confirmHttp': confirm})
            (OUT / 'progress.json').write_text(json.dumps(cases, ensure_ascii=False, indent=2) + '\n')
    finally:
        after = {table: count(table) for table in TABLES}
        passed = before == after and len(cases) == len(CASES) and all(c['status'] == 'PASS' for c in cases)
        record = {'status': 'PASS' if passed else 'FAIL', 'before': before, 'after': after, 'cases': cases}
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'status': record['status'], 'evidence': str(OUT),
                          'cases': [(c['name'], c['status']) for c in cases]}, ensure_ascii=False))
        assert passed, 'Inspect result.json for failed checks'


if __name__ == '__main__':
    main()
