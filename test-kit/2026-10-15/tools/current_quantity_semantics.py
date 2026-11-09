#!/usr/bin/env python3
"""Validate current import quantity semantics without confirming valid previews."""
import datetime
import hashlib
import io
import json
import pathlib
import urllib.request

import openpyxl

from current_material_cell_matrix import BASE, SEED, TABLES, call, count

OUT = pathlib.Path(__file__).resolve().parents[1] / 'evidence/runs' / (
    '2026-11-03-quantity-semantics-' + datetime.datetime.now().strftime('%H%M%S'))


def upload(route, name, wb, token):
    buffer = io.BytesIO()
    wb.save(buffer)
    blob = buffer.getvalue()
    (OUT / name).write_bytes(blob)
    boundary = '----MRPQuantitySemantics20261103'
    head = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' +
            name + '"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n')
    result = call(route, 'POST', head.encode() + blob + ('\r\n--' + boundary + '--\r\n').encode(),
                  token, 'multipart/form-data; boundary=' + boundary)
    return *result, hashlib.sha256(blob).hexdigest()


def book(name):
    wb = openpyxl.load_workbook(SEED / name)
    wb.active.delete_rows(4, wb.active.max_row - 3)
    return wb


CASES = [
    ('F07_zero', 'forecast-current.xlsx', '/forecast-imports', 'F3', 0, None),
    ('F07_blank', 'forecast-current.xlsx', '/forecast-imports', 'F3', None, None),
    ('F07_whitespace', 'forecast-current.xlsx', '/forecast-imports', 'F3', '   ', None),
    ('F07_six_decimal', 'forecast-current.xlsx', '/forecast-imports', 'F3', '1.123456', 'INVALID_QTY'),
    ('F07_scientific', 'forecast-current.xlsx', '/forecast-imports', 'F3', '1E+2', None),
    ('F07_seven_decimal', 'forecast-current.xlsx', '/forecast-imports', 'F3', '1.1234567', 'INVALID_QTY'),
    ('F07_fractional_scientific', 'forecast-current.xlsx', '/forecast-imports', 'F3', '1E-2', 'INVALID_QTY'),
    ('I07_six_decimal', 'inventory-current.xlsx', '/inventory-imports/items', 'F3', '1.123456', None),
    ('I07_numeric_six_decimal', 'inventory-current.xlsx', '/inventory-imports/items', 'F3', 1.123456, None),
    ('I07_max_decimal', 'inventory-current.xlsx', '/inventory-imports/items', 'F3', '999999999999.123456', None),
    ('I07_scientific', 'inventory-current.xlsx', '/inventory-imports/items', 'F3', '1E+2', None),
    ('I07_seven_decimal', 'inventory-current.xlsx', '/inventory-imports/items', 'F3', '1.1234567', 'INVALID_QTY'),
    ('I07_sparse_qty', 'inventory-current.xlsx', '/inventory-imports/items', 'F3', None, 'REQUIRED_FIELD'),
    ('F08_trailing_month_missing', 'forecast-current.xlsx', '/forecast-imports', 'K3', None, None),
    ('F08_blank_row', 'forecast-current.xlsx', '/forecast-imports', 'A4', None, None),
    ('I07_blank_row', 'inventory-current.xlsx', '/inventory-imports/items', 'A4', None, None),
]


def main():
    OUT.mkdir(parents=True)
    assert urllib.request.urlopen('http://127.0.0.1:8182/actuator/health', timeout=5).status == 200
    status, auth = call('/auth/login', 'POST', json.dumps({'username': 'admin', 'password': 'admin123'}).encode())
    assert status == 200, auth
    token = auth['data']['token']
    before = {table: count(table) for table in TABLES}
    cases = []
    try:
        for name, filename, route, cell, value, expected_error in CASES:
            wb = book(filename)
            wb.active[cell] = value
            if name == 'I07_sparse_qty':
                wb.active['G3'] = 'quantity intentionally absent'
            http, response, sha = upload(route, name + '.xlsx', wb, token)
            stage = response.get('data') or {}
            errors = stage.get('errors') or []
            task = stage.get('taskId')
            confirm = None
            if expected_error and task and stage.get('errorRows', 0) > 0:
                confirm, _ = call(route + '/' + str(task) + '/confirm', 'POST', b'', token)
            found = [e for e in errors if e.get('rowNumber') == 3 and e.get('errorCode') == expected_error]
            valid_preview = http == 202 and stage.get('errorRows') == 0 and stage.get('successRows', 0) > 0
            invalid_preview = http == 202 and bool(found) and confirm == 400
            passed = invalid_preview if expected_error else valid_preview
            cases.append({'name': name, 'status': 'PASS' if passed else 'FAIL', 'sha256': sha,
                          'uploadHttp': http, 'taskId': task, 'successRows': stage.get('successRows'),
                          'errorRows': stage.get('errorRows'), 'errors': errors, 'confirmHttp': confirm,
                          'validPreviewNotConfirmed': expected_error is None})
            (OUT / 'progress.json').write_text(json.dumps(cases, ensure_ascii=False, indent=2) + '\n')
    finally:
        after = {table: count(table) for table in TABLES}
        passed = before == after and len(cases) == len(CASES) and all(c['status'] == 'PASS' for c in cases)
        result = {'status': 'PASS' if passed else 'FAIL', 'before': before, 'after': after, 'cases': cases}
        (OUT / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'status': result['status'], 'evidence': str(OUT),
                          'cases': [(c['name'], c['status']) for c in cases]}, ensure_ascii=False))
        assert passed, 'Inspect result.json for failed quantity checks'


if __name__ == '__main__':
    main()
