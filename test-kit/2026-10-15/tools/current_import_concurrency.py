#!/usr/bin/env python3
"""Concurrent import confirmation and cross-user staging access on isolated API."""
import concurrent.futures
import datetime
import hashlib
import io
import json
import pathlib
import subprocess

import openpyxl

from current_material_cell_matrix import BASE, SEED, TABLES, call, count
from current_source_snapshot import snapshot

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-import-concurrency-' + datetime.datetime.now().strftime('%H%M%S'))


def sql(statement):
    assert statement.startswith('SELECT')
    proc = subprocess.run(['docker', 'exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
                           'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B -u"$MYSQL_USER" mrp_testkit'],
                          input=statement, text=True, capture_output=True, check=True)
    return proc.stdout.splitlines()


def body(route, name, wb):
    buffer = io.BytesIO()
    wb.save(buffer)
    blob = buffer.getvalue()
    (OUT / name).write_bytes(blob)
    boundary = '----MRPImportConcurrency20261103'
    header = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + name +
              '"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n')
    return header.encode() + blob + ('\r\n--' + boundary + '--\r\n').encode(), boundary, hashlib.sha256(blob).hexdigest()


def preview(route, payload, boundary, token):
    return call(route, 'POST', payload, token, 'multipart/form-data; boundary=' + boundary)


def resource(result, kind):
    data = result.get('data') or {}
    return data.get('id' if kind == 'forecast' else 'snapshotId')


def main():
    OUT.mkdir(parents=True)
    http, login = call('/auth/login', 'POST', json.dumps({'username': 'admin', 'password': 'admin123'}).encode())
    assert http == 200, login
    admin = login['data']['token']
    assert sql("SELECT COUNT(*) FROM mrp_calc_batch WHERE status NOT IN ('CANCELLED','PUBLISHED','STALE','SUPERSEDED');") == ['0']
    http, roles = call('/roles/active', token=admin)
    assert http == 200, roles
    admin_role = next(row['id'] for row in roles['data'] if row['code'] == 'ADMIN')
    username = 'uat-import-other-' + datetime.datetime.now().strftime('%H%M%S')
    before = {table: count(table) for table in TABLES}
    record = {'status': 'IN_PROGRESS', 'base': BASE, 'before': before, 'beforeSource': snapshot(), 'cases': []}
    other_id = None
    try:
        http, created = call('/users', 'POST',
                             json.dumps({'username': username, 'displayName': username, 'roleIds': [admin_role]}).encode(), admin)
        assert http == 200, created
        other_id = created['data']['id']
        http, secondary = call('/auth/login', 'POST',
                               json.dumps({'username': username, 'password': 'Mrp@123456'}).encode())
        assert http == 200, secondary
        other = secondary['data']['token']
        for kind, route, filename, task_route, error_route in (
                ('forecast', '/forecast-imports', 'forecast-current.xlsx', '/import-tasks/', '/forecast-imports/'),
                ('inventory', '/inventory-imports/items', 'inventory-current.xlsx',
                 '/inventory-import-tasks/', '/inventory-imports/')):
            wb = openpyxl.load_workbook(SEED / filename)
            marker = OUT.name
            if kind == 'forecast':
                wb.active['E3'] = marker
            else:
                wb.active['G3'] = marker
            name = kind + '-concurrent.xlsx'
            payload, boundary, sha = body(route, name, wb)
            http, staged = preview(route, payload, boundary, admin)
            data = staged.get('data') or {}
            assert http == 202 and data.get('errorRows') == 0, staged
            task = data['taskId']
            confirm_route = route + '/' + str(task) + '/confirm'
            unauthorized = []
            for path, method in ((task_route + str(task), 'GET'),
                                 (error_route + str(task) + '/errors', 'GET'),
                                 (confirm_route, 'POST')):
                status, denied = call(path, method, b'' if method == 'POST' else None, other)
                unauthorized.append({'path': path, 'method': method, 'status': status, 'response': denied})
                assert status == 403, denied
            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
                futures = [executor.submit(call, confirm_route, 'POST', b'', admin) for _ in range(2)]
                confirmations = [future.result(timeout=90) for future in futures]
            successful = [(status, response) for status, response in confirmations if status == 200]
            assert successful, confirmations
            ids = [resource(response, kind) for _, response in successful]
            assert len(set(ids)) == 1 and ids[0] is not None, confirmations
            assert all(status in (200, 409) for status, _ in confirmations), confirmations
            repeated_http, repeated = preview(route, payload, boundary, admin)
            assert repeated_http == 202 and repeated['data']['taskId'] == task, repeated
            again_http, again = call(confirm_route, 'POST', b'', admin)
            assert again_http == 200 and resource(again, kind) == ids[0], again
            record['cases'].append({'kind': kind, 'status': 'PASS', 'sha256': sha, 'taskId': task,
                                    'resourceId': ids[0], 'previewHttp': http, 'crossUser': unauthorized,
                                    'concurrentConfirmations': confirmations, 'repeatUploadHttp': repeated_http,
                                    'repeatConfirmHttp': again_http})
            (OUT / 'progress.json').write_text(json.dumps(record['cases'], ensure_ascii=False, indent=2) + '\n')
        after = {table: count(table) for table in TABLES}
        record['after'] = after
        assert after['forecast_version'] == before['forecast_version'] + 1
        assert after['inventory_snapshot'] == before['inventory_snapshot'] + 1
        assert after['forecast_detail'] > before['forecast_detail']
        assert after['inventory_item'] > before['inventory_item']
        record['status'] = 'PASS'
    except Exception as error:
        record['status'] = 'FAIL'
        record['error'] = str(error)
        raise
    finally:
        if other_id is not None:
            http, removed = call('/users/' + str(other_id), 'DELETE', token=admin)
            record['fixtureCleanupHttp'] = http
            assert http == 200, removed
            record['fixtureRemaining'] = sql(f'SELECT COUNT(*) FROM user WHERE id={int(other_id)};')
            assert record['fixtureRemaining'] == ['0']
        record['afterSource'] = snapshot()
        record['protectedSourcesUnchanged'] = record['beforeSource']['tables'] == record['afterSource']['tables']
        if not record['protectedSourcesUnchanged']:
            record['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    assert record['status'] == 'PASS'
    print(json.dumps({'status': 'PASS', 'evidence': str(OUT),
                      'cases': [(case['kind'], case['taskId'], case['resourceId']) for case in record['cases']]},
                     ensure_ascii=False))


if __name__ == '__main__':
    main()
