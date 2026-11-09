#!/usr/bin/env python3
"""Two-phase isolated API restart test: prepare, restart service, verify."""
import datetime
import hashlib
import io
import json
import pathlib
import sys

import openpyxl

from current_material_cell_matrix import BASE, SEED, TABLES, call, count

ROOT = pathlib.Path(__file__).resolve().parents[1]
RUNS = ROOT / 'evidence/runs'


def upload(route, name, blob, token):
    boundary = '----MRPImportRestart20261103'
    header = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + name +
              '"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n')
    return call(route, 'POST', header.encode() + blob + ('\r\n--' + boundary + '--\r\n').encode(),
                token, 'multipart/form-data; boundary=' + boundary)


def login():
    status, response = call('/auth/login', 'POST', json.dumps({'username': 'admin', 'password': 'admin123'}).encode())
    assert status == 200, response
    return response['data']['token']


def main():
    phase = sys.argv[1]
    if phase == 'prepare':
        out = RUNS / ('2026-11-03-import-restart-' + datetime.datetime.now().strftime('%H%M%S'))
        out.mkdir(parents=True)
        token = login()
        before = {table: count(table) for table in TABLES}
        cases = []
        for kind, route, seed_file, marker_cell in (
                ('forecast', '/forecast-imports', 'forecast-current.xlsx', 'E3'),
                ('inventory', '/inventory-imports/items', 'inventory-current.xlsx', 'G3')):
            wb = openpyxl.load_workbook(SEED / seed_file)
            wb.active[marker_cell] = out.name
            buffer = io.BytesIO()
            wb.save(buffer)
            blob = buffer.getvalue()
            name = kind + '-restart.xlsx'
            (out / name).write_bytes(blob)
            status, response = upload(route, name, blob, token)
            data = response.get('data') or {}
            assert status == 202 and data.get('errorRows') == 0, response
            cases.append({'kind': kind, 'route': route, 'file': name, 'sha256': hashlib.sha256(blob).hexdigest(),
                          'taskId': data['taskId'], 'previewHttp': status})
        after = {table: count(table) for table in TABLES}
        assert before == after
        result = {'status': 'PREPARED', 'base': BASE, 'before': before, 'afterPreview': after, 'cases': cases}
        (out / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'status': 'PREPARED', 'evidence': str(out),
                          'tasks': [(case['kind'], case['taskId']) for case in cases]}, ensure_ascii=False))
    elif phase == 'verify':
        out = pathlib.Path(sys.argv[2]).resolve()
        assert out.is_relative_to(RUNS) and (out / 'result.json').exists()
        result = json.loads((out / 'result.json').read_text())
        assert result['status'] == 'PREPARED'
        token = login()
        before = {table: count(table) for table in TABLES}
        assert before == result['before'], (before, result['before'])
        try:
            for case in result['cases']:
                task = case['taskId']
                route = case['route']
                staging = '/import-tasks/' if case['kind'] == 'forecast' else '/inventory-import-tasks/'
                stale_http, stale = call(staging + str(task), token=token)
                confirm_http, denied = call(route + '/' + str(task) + '/confirm', 'POST', b'', token)
                case['afterRestartStagingHttp'] = stale_http
                case['afterRestartConfirmHttp'] = confirm_http
                case['afterRestartResponse'] = {'staging': stale, 'confirm': denied}
                assert stale_http == 400 and confirm_http == 400, case
                blob = (out / case['file']).read_bytes()
                assert hashlib.sha256(blob).hexdigest() == case['sha256']
                reupload_http, restaged = upload(route, case['file'], blob, token)
                assert reupload_http == 202 and restaged['data']['taskId'] == task
                assert restaged['data']['errorRows'] == 0
                case['reuploadHttp'] = reupload_http
                committed_http, committed = call(route + '/' + str(task) + '/confirm', 'POST', b'', token)
                case['confirmHttp'] = committed_http
                case['resourceId'] = (committed.get('data') or {}).get(
                    'id' if case['kind'] == 'forecast' else 'snapshotId')
                assert committed_http == 200 and case['resourceId'] is not None, committed
                case['status'] = 'PASS'
                (out / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
            after = {table: count(table) for table in TABLES}
            result['after'] = after
            assert after['forecast_version'] == before['forecast_version'] + 1
            assert after['inventory_snapshot'] == before['inventory_snapshot'] + 1
            result['status'] = 'PASS'
        except Exception as error:
            result['status'] = 'FAIL'
            result['error'] = str(error)
            raise
        finally:
            result['after'] = {table: count(table) for table in TABLES}
            (out / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'status': result['status'], 'evidence': str(out),
                          'resources': [(case['kind'], case.get('resourceId')) for case in result['cases']]},
                         ensure_ascii=False))
    else:
        raise SystemExit('usage: current_import_restart.py prepare | verify evidence-directory')


if __name__ == '__main__':
    main()
