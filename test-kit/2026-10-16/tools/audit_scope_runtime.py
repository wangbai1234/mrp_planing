"""Audit HTTP data scope: temporary isolated identities/log rows, no planning writes."""
import argparse, json, os, subprocess, time, urllib.request, urllib.error
from urllib.parse import urlencode
import continuation_access as api
from continuation_publish import ROOT, sql

parser = argparse.ArgumentParser()
parser.add_argument('phase', choices=['before', 'after'])
parser.add_argument('--browser', action='store_true')
args = parser.parse_args()
after = args.phase == 'after'
stamp = 'audit_' + str(time.time_ns())
user = role = None
checks = []
passed = False
api.token = api.ok('/auth/login', 'POST', {'username': 'admin', 'password': api.password()})['token']

def read(auth, **query):
    req = urllib.request.Request(api.BASE + '/audit-logs?' + urlencode(query),
                                 headers={'Authorization': 'Bearer ' + auth})
    with urllib.request.urlopen(req, timeout=60) as res:
        data = json.load(res)['data']
        checks.append({'query': query, 'status': res.status, 'total': data['total'],
                       'ids': [row['id'] for row in data['items']]})
        return data

try:
    catalog = api.ok('/permissions')
    ids = {p['code']: p['id'] for p in (catalog if isinstance(catalog, list) else catalog['items'])}
    role = api.ok('/roles', 'POST', {'code': stamp, 'name': '审计域临时验收'})
    api.ok(f"/roles/{role['id']}/permissions", 'PUT', {'permissionIds': [ids['audit:view'], ids['audit:list']]})
    user = api.ok('/users', 'POST', {'username': stamp, 'displayName': '审计临时验收', 'roleIds': [role['id']]})
    auth = api.ok('/auth/login', 'POST', {'username': stamp, 'password': 'Mrp@123456'})['token']
    sql(f"INSERT INTO user_factory_scope(user_id,factory_code) VALUES({user['id']},'爱培科');")
    rows = [
        ('factory_warehouse_mapping', '爱培科', None),
        ('factory_warehouse_mapping', '永惠', None),
        ('mrp_production_calendar', '爱培科|2088-10-01', None),
        ('mrp_production_calendar', '永惠|2088-10-01', None),
        ('mrp_daily_plan_override', '1', '爱培科'),
        ('mrp_daily_plan_override', '2', '永惠'),
        ('mrp_daily_plan_override', '3', 'ALL'),
        ('factory_warehouse_mapping', 'IMPORT', None),
        ('stock_policy', 'WH|SKU', None),
        ('unrecognized', 'unknown', None),
        ('business_week', '2088-10', None),
        ('factory_warehouse_mapping', 'IMPORT', '爱培科'),
    ]
    for kind, resource, scope in rows:
        scope_sql = 'NULL' if scope is None else "'" + scope + "'"
        sql(f"INSERT INTO audit_log(operator_id,action,resource_type,resource_id,factory_scope,trace_id,after_value) VALUES({user['id']},'{stamp}','{kind}','{resource}',{scope_sql},'{stamp}','{{\"test\":true}}');")
    for query, expected in [
        ({'action': stamp}, 5),
        ({'action': stamp, 'resourceType': 'factory_warehouse_mapping'}, 2),
        ({'action': stamp, 'resourceType': 'mrp_production_calendar'}, 1),
        ({'action': stamp, 'resourceType': 'factory_warehouse_mapping', 'resourceId': '永惠'}, 0),
        ({'action': stamp, 'resourceType': 'mrp_production_calendar', 'resourceId': '永惠|2088-10-01'}, 0),
        ({'action': stamp, 'resourceType': 'stock_policy'}, 0),
    ]:
        data = read(auth, **query)
        assert data['total'] == (expected if after else sum(1 for k, r, s in rows
            if (not query.get('resourceType') or k == query['resourceType'])
            and (not query.get('resourceId') or r == query['resourceId'])))
    if after:
        # No resource filters: no external payload and count uses the same domain as items.
        data = read(auth, pageSize=100)
        assert all(row['resource_type'] in ['USER', 'ROLE', 'PERMISSION', 'material_policy', 'business_week']
                   or '永惠' not in json.dumps(row, ensure_ascii=False) for row in data['items'])
        all_ids = []
        for page in range(1, 6):
            data = read(auth, action=stamp, page=page, pageSize=1)
            assert data['total'] == 5 and len(data['items']) == 1
            all_ids += [data['items'][0]['id']]
        assert len(set(all_ids)) == 5
        assert read(auth, action=stamp, keyword="' OR 1=1 --")['total'] == 0
        sql(f"DELETE FROM user_factory_scope WHERE user_id={user['id']};")
        assert read(auth, action=stamp)['total'] == 1  # global business week only
        sql(f"INSERT INTO role_data_scope(role_id,scope_type,scope_value) VALUES({role['id']},'FACTORY','永惠');")
        assert read(auth, action=stamp)['total'] == 4
        sql(f"UPDATE role_data_scope SET scope_type='ALL',scope_value=NULL WHERE role_id={role['id']};")
        assert read(auth, action=stamp)['total'] == len(rows)
        sql(f"DELETE FROM role_data_scope WHERE role_id={role['id']};")
        sql(f"INSERT INTO user_factory_scope(user_id,factory_code) VALUES({user['id']},'爱培科');")
        if args.browser:
            env = os.environ.copy()
            env.update(UAT_API_BASE='http://127.0.0.1:8180', UAT_UI_BASE='http://localhost:5181',
                UAT_AUDIT_USER=stamp, UAT_AUDIT_ACTION=stamp, PLAYWRIGHT_USE_SYSTEM_CHROME='1',
                UAT_E2E_REPORT=str(ROOT/'evidence/audit-scope-browser-report'),
                UAT_E2E_OUTPUT=str(ROOT/'evidence/audit-scope-browser-output'))
            with (ROOT/'evidence/audit-scope-browser.log').open('w') as log:
                subprocess.run(['./node_modules/.bin/playwright', 'test',
                    '--config=../test-kit/2026-10-16/e2e/playwright.config.mjs', 'audit-scope.spec.mjs'],
                    cwd=ROOT.parents[1]/'frontend', env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
    assert read(api.token, action=stamp)['total'] == len(rows)
    passed = True
finally:
    sql(f"DELETE FROM audit_log WHERE trace_id='{stamp}';")
    if user: api.ok('/users/' + str(user['id']), 'DELETE')
    if role: api.ok('/roles/' + str(role['id']), 'DELETE')
    remaining = sql(f"SELECT COUNT(*) FROM audit_log WHERE trace_id='{stamp}';")
    (ROOT/f'evidence/audit-scope-{args.phase}-runtime.json').write_text(json.dumps({
        'status': 'PASS' if passed else 'FAIL', 'checks': checks, 'remainingLogFixtures': remaining
    }, ensure_ascii=False, indent=2) + '\n')
print(args.phase, 'PASS', len(checks), 'real HTTP checks')
