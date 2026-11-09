"""Concurrent first uploads against isolated live HTTP; only temporary owners are writable."""
import concurrent.futures
import datetime
import json
import threading
import time
import uuid

import continuation_import_owner as http
import import_confirmation_runtime as fixture
from continuation_access import password
from continuation_publish import ROOT, sql

OUT = ROOT / 'evidence/import-upload-identity-runtime.json'
results = []
users = []
role = None


def upload(flow, auth, payload):
    path = {'forecast': '/forecast-imports', 'items': '/inventory-imports/items',
            'legacy': '/inventory-imports?snapshotDate=2026-10-17'}[flow]
    boundary = 'identity' + uuid.uuid4().hex
    # Filename independence avoids coupling this DB identity test to same-path file writes.
    filename = 'identity-' + uuid.uuid4().hex + '.xlsx'
    raw = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
           'Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode()
    raw += payload + f'\r\n--{boundary}--\r\n'.encode()
    start = time.monotonic()
    status, body = http.req(path, 'POST', auth=auth, raw=raw, content_type='multipart/form-data; boundary=' + boundary)
    results.append({'flow': flow, 'operation': 'upload', 'status': status, 'expected': 202,
                    'ms': round((time.monotonic() - start) * 1000)})
    assert status == 202, (flow, status)
    data = json.loads(body)['data']
    assert data['errorRows'] == 0 and data['successRows'] > 0
    return data['taskId']


if __name__ == '__main__':
    assert not OUT.exists(), 'Do not overwrite evidence'
    before = fixture.protection()
    passed = False
    groups = []
    try:
        http.admin = http.ok('/auth/login', 'POST', {'username': 'admin', 'password': password()})['token']
        stamp = str(time.time_ns())
        role = http.ok('/roles', 'POST', {'code': 'upload_' + stamp, 'name': '上传身份临时验收'})
        ids = {p['code']: p['id'] for p in http.ok('/permissions')}
        codes = ['forecast:view', 'forecast:import', 'forecast:import_confirm', 'inventory:view', 'inventory:import']
        http.ok(f"/roles/{role['id']}/permissions", 'PUT', {'permissionIds': [ids[c] for c in codes]})
        auths = []
        for i in range(2):
            username = 'upload_' + stamp + '_' + str(i)
            user = http.ok('/users', 'POST', {'username': username, 'displayName': '上传身份验收', 'roleIds': [role['id']]})
            users.append(user)
            auths.append(http.ok('/auth/login', 'POST', {'username': username, 'password': 'Mrp@123456'})['token'])
        material = sql('SELECT material_code FROM material ORDER BY material_code LIMIT 1;')[0]
        for flow in ['forecast', 'items', 'legacy']:
            payload = fixture.workbook(flow, material)
            initial = [fixture.count(flow, u['id']) for u in users]
            barrier = threading.Barrier(12)

            def concurrent_upload(index):
                owner_index = 0 if index < 8 else 1
                barrier.wait(timeout=15)
                return owner_index, upload(flow, auths[owner_index], payload)

            with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
                futures = [pool.submit(concurrent_upload, i) for i in range(12)]
                responses = [f.result(timeout=90) for f in futures]
            task_ids = []
            for owner_index, user in enumerate(users):
                ids_for_owner = {task for index, task in responses if index == owner_index}
                assert len(ids_for_owner) == 1
                task = ids_for_owner.pop()
                task_ids.append(task)
                assert sql(f'SELECT status,version,created_by FROM import_task WHERE id={task};') == [f"PENDING\t0\t{user['id']}"]
                prefix = {'forecast': 'forecast:', 'items': 'inventory_item:', 'legacy': 'inventory:'}[flow]
                assert sql(f"SELECT COUNT(*) FROM import_task WHERE created_by={user['id']} AND request_key LIKE '{prefix}%';") == ['1']
                fixture.confirm(flow, task, auths[1-owner_index], 403)
                resource = fixture.confirm(flow, task, auths[owner_index])
                assert upload(flow, auths[owner_index], payload) == task
                assert fixture.confirm(flow, task, auths[owner_index]) == resource
                assert fixture.count(flow, user['id']) == initial[owner_index] + 1
                if flow == 'forecast':
                    quantity = sql(f'SELECT COUNT(*),SUM(forecast_qty) FROM forecast_detail WHERE version_id={resource};')
                    assert quantity == ['6\t738.000006']
                elif flow == 'items':
                    quantity = sql(f'SELECT COUNT(*),SUM(shipping_available_qty) FROM inventory_item WHERE snapshot_id={resource};')
                    assert quantity == ['1\t123.000001']
                else:
                    quantity = sql(f'SELECT COUNT(*),SUM(quantity) FROM inventory_detail WHERE snapshot_id={resource};')
                    assert quantity == ['1\t123']
                groups.append({'flow': flow, 'owner': user['id'], 'task': task, 'resource': resource,
                               'concurrentUploads': 8 if owner_index == 0 else 4, 'quantity': quantity})
            assert task_ids[0] != task_ids[1]
        passed = True
    finally:
        for user in users:
            uid = user['id']
            sql(f'''START TRANSACTION;
DELETE FROM forecast_detail WHERE version_id IN (SELECT id FROM forecast_version WHERE created_by={uid});
DELETE FROM inventory_item WHERE snapshot_id IN (SELECT id FROM inventory_snapshot WHERE created_by={uid});
DELETE FROM inventory_detail WHERE snapshot_id IN (SELECT id FROM inventory_snapshot WHERE created_by={uid});
DELETE FROM import_task WHERE created_by={uid};
DELETE FROM forecast_version WHERE created_by={uid};
DELETE FROM inventory_snapshot WHERE created_by={uid};
COMMIT;''')
            http.ok('/users/' + str(uid), 'DELETE')
        if role:
            http.ok('/roles/' + str(role['id']), 'DELETE')
        after = fixture.protection()
        cleanup = all(fixture.count(flow, user['id']) == 0 for user in users for flow in ['forecast', 'items'])
        tasks_cleared = all(sql(f"SELECT COUNT(*) FROM import_task WHERE created_by={user['id']};") == ['0'] for user in users)
        result = {'status': 'PASS' if passed and before == after and cleanup and tasks_cleared else 'FAIL',
                  'uploads': results, 'confirmations': fixture.results, 'groups': groups,
                  'protectionBefore': before, 'protectionAfter': after,
                  'temporaryFormalRowsCleared': cleanup, 'temporaryTasksCleared': tasks_cleared,
                  'finishedAt': datetime.datetime.now().astimezone().isoformat()}
        OUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    assert result['status'] == 'PASS'
    print('PASS', len(results), 'live uploads,', len(fixture.results), 'confirmation assertions; exact quantities and cleanup verified')
