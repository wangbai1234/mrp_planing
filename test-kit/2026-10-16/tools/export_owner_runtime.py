"""Temporary legacy export fixtures in isolated MySQL; no MRP batch writes."""
import argparse
import json
import os
import pathlib
import subprocess
import time
import urllib.error
import urllib.request
import zipfile
import io
import xml.etree.ElementTree as ET

import continuation_access as api
from continuation_publish import sql, ROOT

parser = argparse.ArgumentParser()
parser.add_argument('phase', choices=['before', 'after'])
parser.add_argument('--browser', action='store_true')
args = parser.parse_args()
after = args.phase == 'after'
checks, users, roles, plans, files = [], [], [], [], set()
forecast = None
passed = False
marker = 'export_' + str(time.time_ns())
api.token = api.ok('/auth/login', 'POST', {'username': 'admin', 'password': api.password()})['token']

def response(path, auth, method='GET', body=None):
    req = urllib.request.Request(api.BASE + path, method=method,
        headers={'Authorization': 'Bearer ' + auth, 'Content-Type': 'application/json'},
        data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req, timeout=60) as res:
            return res.status, res.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()

def check(name, path, auth, want, method='GET', body=None):
    status, data = response(path, auth, method, body)
    checks.append({'name': name, 'path': path, 'method': method, 'status': status, 'expected': want})
    assert status == want, (name, status, want)
    return data

def create(plan, auth, extra=None, want=202, name='create'):
    body = {'fields': ['整机料号', '合计', '2026-10-05']}
    body.update(extra or {})
    raw = check(name, f'/plans/{plan}/exports', auth, want, 'POST', body)
    if want != 202:
        return None
    data = json.loads(raw)['data']
    if data.get('filePath'):
        files.add(data['filePath'])
    return data

try:
    catalog = api.ok('/permissions')
    ids = {p['code']: p['id'] for p in (catalog if isinstance(catalog, list) else catalog['items'])}
    for index, permissions in enumerate([['schedule:export', 'schedule:edit'], ['schedule:edit']]):
        role = api.ok('/roles', 'POST', {'code': marker + '_r' + str(index), 'name': '导出授权临时验收'})
        roles.append(role['id'])
        api.ok(f"/roles/{role['id']}/permissions", 'PUT', {'permissionIds': [ids[c] for c in permissions]})
    tokens = []
    for index in range(3):
        username = marker + '_u' + str(index)
        user = api.ok('/users', 'POST', {'username': username, 'displayName': '导出临时验收', 'roleIds': [roles[0 if index < 2 else 1]]})
        users.append(user['id'])
        tokens.append(api.ok('/auth/login', 'POST', {'username': username, 'password': 'Mrp@123456'})['token'])
        sql(f"INSERT INTO user_factory_scope(user_id,factory_code) VALUES({user['id']},'爱培科');")
    owner, other, editor = tokens
    forecast = int(sql(f"INSERT INTO forecast_version(version_no,file_name,file_checksum,status) VALUES(1,'{marker}','{marker}','IMPORTED');SELECT LAST_INSERT_ID();")[0])
    for factory in ['爱培科', '永惠', 'ALL']:
        plan = int(sql(f"INSERT INTO plan_version(version_no,factory_code,forecast_version_id,rule_version,current_week_start,status) VALUES(1,'{factory}',{forecast},'v1','2026-10-05','PUBLISHED');SELECT LAST_INSERT_ID();")[0])
        plans.append(plan)
        actual = '爱培科' if factory == 'ALL' else factory
        sql(f"INSERT INTO plan_detail(plan_version_id,factory_code,material_id,week_start_date,physical_month,source_month,slot,system_quantity,effective_quantity) VALUES({plan},'{actual}','EXPORT-TEST','2026-10-05','2026-10-01','2026-10-01',1,17,17);")
    own, foreign, combined = plans
    task = create(own, owner)
    check('owner status', f"/export-tasks/{task['id']}", owner, 200)
    content = check('owner download', f"/export-tasks/{task['id']}/download", owner, 200)
    with zipfile.ZipFile(io.BytesIO(content)) as book:
        sheet = ET.fromstring(book.read('xl/worksheets/sheet1.xml'))
        ns = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
        assert sheet.find(".//s:c[@r='B2']/s:v", ns).text == '17.0'
        assert sheet.find(".//s:c[@r='C2']/s:v", ns).text == '17.0'
    for suffix in ['', '/download']:
        check('other user', f"/export-tasks/{task['id']}" + suffix, other, 403 if after else 200)
    second = create(own, other)
    assert (second['id'] != task['id']) == after
    checks.append({'name': 'same input owner isolation', 'distinctTasks': second['id'] != task['id'], 'expectedDistinct': after})
    priority = create(own, owner, {'includePriority': True})
    actual = create(own, owner, {'includeActual': True})
    assert (len({task['id'], priority['id'], actual['id']}) == 3) == after
    normalized = create(own, owner, {'factoryCode': '爱培科'})
    assert (normalized['id'] == task['id']) == after
    create(own, editor, want=403 if after else 202, name='edit without export')
    create(foreign, owner, want=403 if after else 202, name='foreign version')
    create(own, owner, {'factoryCode': '永惠'}, want=403 if after else 202, name='mismatched factory')
    sql(f"DELETE FROM user_factory_scope WHERE user_id={users[0]};")
    for suffix in ['', '/download']:
        check('revoked factory', f"/export-tasks/{task['id']}" + suffix, owner, 403 if after else 200)
    sql(f"INSERT INTO user_factory_scope(user_id,factory_code) VALUES({users[0]},'爱培科'),({users[0]},'永惠');")
    all_task = create(combined, owner)
    sql(f"DELETE FROM user_factory_scope WHERE user_id={users[0]} AND factory_code='永惠';")
    check('combined requires all factories', f"/export-tasks/{all_task['id']}", owner, 403 if after else 200)
    check('missing task compatibility', '/export-tasks/9223372036854775807', owner, 400)
    if args.browser:
        assert after
        env = os.environ.copy()
        env.update(UAT_API_BASE='http://127.0.0.1:8180', UAT_UI_BASE='http://localhost:5181',
            UAT_EXPORT_OWNER=marker+'_u0', UAT_EXPORT_OTHER=marker+'_u1', UAT_EXPORT_TASK=str(task['id']),
            PLAYWRIGHT_USE_SYSTEM_CHROME='1',
            UAT_E2E_REPORT=str(ROOT/'evidence/export-owner-browser-report'), UAT_E2E_OUTPUT=str(ROOT/'evidence/export-owner-browser-output'))
        with (ROOT/'evidence/export-owner-browser.log').open('w') as log:
            subprocess.run(['./node_modules/.bin/playwright', 'test', '--config=../test-kit/2026-10-16/e2e/playwright.config.mjs', 'export-owner.spec.mjs'],
                cwd=ROOT.parents[1]/'frontend', env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
    passed = True
finally:
    # Remove only fixture tasks/plans/users. Large Phase2 batches are never selected or changed.
    if plans:
        for path in sql("SELECT file_path FROM export_task WHERE created_by IN (" + ','.join(map(str, users)) + ") AND file_path IS NOT NULL;"):
            files.add(path)
    for path in files:
        if path != 'NULL': pathlib.Path(path).unlink(missing_ok=True)
    if users: sql('DELETE FROM export_task WHERE created_by IN (' + ','.join(map(str, users)) + ');')
    for plan in plans: sql(f'DELETE FROM plan_detail WHERE plan_version_id={plan};DELETE FROM plan_version WHERE id={plan};')
    if forecast: sql(f'DELETE FROM forecast_version WHERE id={forecast};')
    for user in users:
        sql(f'DELETE FROM user_factory_scope WHERE user_id={user};')
        api.ok('/users/' + str(user), 'DELETE')
    for role in roles: api.ok('/roles/' + str(role), 'DELETE')
    remaining = sql(f"SELECT COUNT(*) FROM forecast_version WHERE file_name='{marker}';SELECT COUNT(*) FROM user WHERE username LIKE '{marker}%';")
    (ROOT/f'evidence/export-owner-{args.phase}-runtime.json').write_text(json.dumps({'status': 'PASS' if passed else 'FAIL', 'phase': args.phase,
        'checks': checks, 'fixturePlans': plans, 'remainingFixtureCounts': remaining}, ensure_ascii=False, indent=2)+'\n')
print(args.phase, 'PASS', len(checks), 'real HTTP export checks')
