"""Confirm valid imports over live HTTP in the isolated database; clean only owned fixtures."""
import concurrent.futures
import datetime
import io
import json
import threading
import time
import uuid
import zipfile
from xml.sax.saxutils import escape

import continuation_import_owner as http
from continuation_access import password
from continuation_publish import ROOT, digest, sql

OUT = ROOT / 'evidence/import-confirmation-runtime.json'
TABLES = ['forecast_version', 'forecast_detail', 'inventory_snapshot', 'inventory_item', 'inventory_detail']
results = []
users = []
role = None


def protection():
    return {
        'formalCounts': sql('SELECT ' + ','.join('(SELECT COUNT(*) FROM ' + t + ')' for t in TABLES) + ';'),
        'batches': sql('SELECT id,status,active_run_id,version,result_checksum FROM mrp_calc_batch WHERE id IN (10,13) ORDER BY id;'),
        'facts13': {t: digest(t, 13) for t in ['mrp_requirement_detail', 'mrp_terminal_demand', 'mrp_daily_plan_detail']},
    }


def workbook(flow, material):
    marker = uuid.uuid4().hex
    if flow == 'forecast':
        headers = ['工厂', '形态', '料号', '模具', '状态', '2026-10', '2026-11', '2026-12', '2027-01', '2027-02', '2027-03', '总量']
        rows = [headers, [marker], ['爱培科', '整机', material, 'test', 'test'] + ['123.000001'] * 6 + ['738.000006']]
    elif flow == 'items':
        rows = [['仓库编码', '仓库名称', '库存分类', '料号', '品质', '供应商名称', '库存数量'],
                [marker], ['CONFIRM-TEST', '确认验收仓', 'test', material, 'GOOD', 'test', '123.000001']]
    else:
        rows = [[marker, '仓库编码', '仓库名称', '仓库类型', '工厂', '数量'],
                [material, 'CONFIRM-TEST', '确认验收仓', 'GOOD', '爱培科', '123']]
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('[Content_Types].xml', '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>')
        z.writestr('_rels/.rels', '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>')
        z.writestr('xl/workbook.xml', '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="confirm" sheetId="1" r:id="rId1"/></sheets></workbook>')
        z.writestr('xl/_rels/workbook.xml.rels', '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>')
        sheet = []
        for n, values in enumerate(rows, 1):
            cells = ''.join(f'<c r="{chr(65+i)}{n}" t="inlineStr"><is><t>{escape(v)}</t></is></c>' for i, v in enumerate(values))
            sheet.append(f'<row r="{n}">{cells}</row>')
        z.writestr('xl/worksheets/sheet1.xml', '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>' + ''.join(sheet) + '</sheetData></worksheet>')
    return output.getvalue()


def upload(flow, auth, payload):
    path = {'forecast': '/forecast-imports', 'items': '/inventory-imports/items', 'legacy': '/inventory-imports?snapshotDate=2026-10-17'}[flow]
    boundary = 'confirm' + uuid.uuid4().hex
    raw = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="confirmation.xlsx"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode() + payload + f'\r\n--{boundary}--\r\n'.encode()
    status, body = http.req(path, 'POST', auth=auth, raw=raw, content_type='multipart/form-data; boundary=' + boundary)
    parsed = json.loads(body)
    assert status == 202 and parsed['success'], (flow, status)
    data = parsed['data']
    assert data['errorRows'] == 0 and data['successRows'] > 0, (flow, data)
    return data['taskId']


def confirm(flow, task, auth, expected=200):
    path = {'forecast': f'/forecast-imports/{task}/confirm', 'items': f'/inventory-imports/items/{task}/confirm',
            'legacy': f'/inventory-imports/{task}/confirm?snapshotDate=2026-10-17'}[flow]
    start = time.monotonic()
    status, body = http.req(path, 'POST', auth=auth)
    results.append({'flow': flow, 'task': task, 'status': status, 'expected': expected, 'ms': round((time.monotonic()-start)*1000)})
    assert status == expected, (flow, task, status, expected)
    if status == 200:
        data = json.loads(body)['data']
        return data['snapshotId' if flow == 'items' else 'id']


def count(flow, owner):
    table = 'forecast_version' if flow == 'forecast' else 'inventory_snapshot'
    return int(sql(f'SELECT COUNT(*) FROM {table} WHERE created_by={owner};')[0])


if __name__ == '__main__':
    assert not OUT.exists(), 'Do not overwrite evidence'
    before = protection()
    passed = False
    try:
        http.admin = http.ok('/auth/login', 'POST', {'username': 'admin', 'password': password()})['token']
        stamp = str(time.time_ns())
        role = http.ok('/roles', 'POST', {'code': 'confirm_' + stamp, 'name': '确认幂等临时验收'})
        ids = {p['code']: p['id'] for p in http.ok('/permissions')}
        codes = ['forecast:view', 'forecast:import', 'forecast:import_confirm', 'inventory:view', 'inventory:import']
        http.ok(f"/roles/{role['id']}/permissions", 'PUT', {'permissionIds': [ids[c] for c in codes]})
        auths = []
        for i in range(2):
            username = 'confirm_' + stamp + '_' + str(i)
            user = http.ok('/users', 'POST', {'username': username, 'displayName': '确认幂等验收', 'roleIds': [role['id']]})
            users.append(user)
            auths.append(http.ok('/auth/login', 'POST', {'username': username, 'password': 'Mrp@123456'})['token'])
        owner = users[0]['id']
        material = sql('SELECT material_code FROM material ORDER BY material_code LIMIT 1;')[0]
        for flow in ['forecast', 'items', 'legacy']:
            payload = workbook(flow, material)
            task = upload(flow, auths[0], payload)
            confirm(flow, task, auths[1], 403)
            initial = count(flow, owner)
            resource = confirm(flow, task, auths[0])
            assert confirm(flow, task, auths[0]) == resource
            assert upload(flow, auths[0], payload) == task
            assert confirm(flow, task, auths[0]) == resource
            assert count(flow, owner) == initial + 1
            if flow == 'forecast':
                assert sql(f'SELECT COUNT(*),SUM(forecast_qty) FROM forecast_detail WHERE version_id={resource};') == ['6\t738.000006']
            elif flow == 'items':
                assert sql(f'SELECT COUNT(*),SUM(shipping_available_qty) FROM inventory_item WHERE snapshot_id={resource};') == ['1\t123.000001']
            else:
                assert sql(f'SELECT COUNT(*),SUM(quantity) FROM inventory_detail WHERE snapshot_id={resource};') == ['1\t123']
            task = upload(flow, auths[0], workbook(flow, material))
            initial = count(flow, owner)
            barrier = threading.Barrier(2)
            def concurrent_confirm():
                barrier.wait(timeout=10)
                return confirm(flow, task, auths[0])
            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
                futures = [pool.submit(concurrent_confirm) for _ in range(2)]
                resources = [f.result(timeout=90) for f in futures]
            assert resources[0] == resources[1] and count(flow, owner) == initial + 1
            assert sql(f'SELECT status,version,result_resource_id FROM import_task WHERE id={task};') == [f'SUCCEEDED\t1\t{resources[0]}']
            for terminal in ['FAILED', 'CANCELLED', 'RUNNING']:
                task = upload(flow, auths[0], workbook(flow, material))
                sql(f"UPDATE import_task SET status='{terminal}' WHERE id={task} AND created_by={owner};")
                initial = count(flow, owner)
                confirm(flow, task, auths[0], 409)
                assert count(flow, owner) == initial
            task = upload(flow, auths[0], workbook(flow, material))
            sql(f"UPDATE import_task SET status='SUCCEEDED',result_resource_id=9223372036854775806 WHERE id={task} AND created_by={owner};")
            initial = count(flow, owner)
            confirm(flow, task, auths[0], 409)
            assert count(flow, owner) == initial
            table = 'forecast_version' if flow == 'forecast' else 'inventory_snapshot'
            foreign = sql(f'SELECT id FROM {table} WHERE created_by<>{owner} ORDER BY id LIMIT 1;')
            assert foreign, 'Retained fixture must supply a foreign resource for ownership verification'
            task = upload(flow, auths[0], workbook(flow, material))
            sql(f"UPDATE import_task SET status='SUCCEEDED',result_resource_id={int(foreign[0])} WHERE id={task} AND created_by={owner};")
            initial = count(flow, owner)
            confirm(flow, task, auths[0], 409)
            assert count(flow, owner) == initial
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
        after = protection()
        cleanup = all(count(flow, user['id']) == 0 for user in users for flow in ['forecast', 'items'])
        result = {'status': 'PASS' if passed and before == after and cleanup else 'FAIL', 'results': results,
                  'protectionBefore': before, 'protectionAfter': after, 'temporaryFormalRowsCleared': cleanup,
                  'finishedAt': datetime.datetime.now().astimezone().isoformat()}
        OUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    assert result['status'] == 'PASS'
    print('PASS', len(results), 'live confirmation assertions; exact quantities and protected data verified')
