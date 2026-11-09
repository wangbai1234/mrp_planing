"""Current stock policy scope regression on temporary rows in the isolated database only."""
import argparse, io, json, os, subprocess, time, urllib.error, urllib.request
import openpyxl
import continuation_access as api
from continuation_publish import ROOT, sql

parser = argparse.ArgumentParser()
parser.add_argument('phase', choices=['before', 'after'])
parser.add_argument('--browser', action='store_true')
parser.add_argument('--evidence-tag', default='')
args = parser.parse_args()
after = args.phase == 'after'
out = ROOT / f'evidence/stock-scope-{args.phase}{args.evidence_tag}-runtime.json'
assert not out.exists(), 'Preserve existing evidence'
assert api.BASE == 'http://127.0.0.1:8180/api/v1'
stamp = 'stock_' + str(time.time_ns())
checks = []; user = role = None; passed = False; fixture_ids = []
api.token = api.ok('/auth/login', 'POST', {'username':'admin', 'password':api.password()})['token']

def quote(value): return "'" + value.replace("'", "''") + "'"

def request(path, auth, want, method='GET', body=None, upload=None):
    headers = {'Authorization':'Bearer '+auth, 'Content-Type':'application/json'}
    data = None if body is None else json.dumps(body).encode()
    if upload:
        headers['Content-Type'] = 'multipart/form-data; boundary=stock-scope-fixture'
        data = (b'--stock-scope-fixture\r\nContent-Disposition: form-data; name="file"; filename="scope.xlsx"\r\n'
                b'Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n'
                + upload + b'\r\n--stock-scope-fixture--\r\n')
    req = urllib.request.Request(api.BASE+path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as response: status, raw = response.status, response.read()
    except urllib.error.HTTPError as error: status, raw = error.code, error.read()
    checks.append({'path':path, 'method':method, 'status':status, 'expected':want})
    assert status == want, (path, status, want)
    return json.loads(raw).get('data') if status == 200 else None

def workbook(rows):
    book = openpyxl.Workbook(); sheet = book.active
    sheet.append(['仓库编码','物料料号','安全库存','预警库存']); sheet.append(['填写说明']*4)
    for row in rows: sheet.append(row)
    buffer = io.BytesIO(); book.save(buffer); return buffer.getvalue()

try:
    mappings = api.ok('/mrp/factory-warehouse-mappings')
    own = next(row['warehouseCode'] for row in mappings if row['factoryCode']=='爱培科' and row['enabled'])
    foreign = next(row['warehouseCode'] for row in mappings if row['factoryCode']=='永惠' and row['enabled'])
    # Existing lawful material is referenced only; master data is never changed.
    material = sql('SELECT m.material_code FROM material m WHERE m.is_active=1 AND m.is_deleted=0 '
        'AND NOT EXISTS(SELECT 1 FROM stock_policy p WHERE p.material_code=m.material_code) ORDER BY m.id LIMIT 1;')[0]
    for warehouse in [own, foreign]:
        fixture_ids.append(int(sql('INSERT INTO stock_policy(warehouse_code,material_code,safety_stock_qty,warning_stock_qty) '
            f'VALUES({quote(warehouse)},{quote(material)},2,20); SELECT LAST_INSERT_ID();')[-1]))
    catalog = api.ok('/permissions'); ids = {p['code']:p['id'] for p in (catalog if isinstance(catalog,list) else catalog['items'])}
    role = api.ok('/roles','POST',{'code':stamp,'name':'库存策略工厂域临时验收'})
    api.ok(f"/roles/{role['id']}/permissions",'PUT',{'permissionIds':[ids[c] for c in
        ['mrp_stock_policy:view','mrp_stock_policy:manage','audit:view']]})
    user = api.ok('/users','POST',{'username':stamp,'displayName':'策略临时验收','roleIds':[role['id']]})
    sql(f"INSERT INTO user_factory_scope(user_id,factory_code) VALUES({user['id']},'爱培科');")
    auth = api.ok('/auth/login','POST',{'username':stamp,'password':'Mrp@123456'})['token']
    from urllib.parse import urlencode
    query = urlencode({'keyword':material,'pageSize':1})
    page = request('/mrp/stock-policies/page?'+query,auth,200)
    assert page['total'] == (1 if after else 2)
    rows = request('/mrp/stock-policies',auth,200)
    assert all(r['warehouseCode']==own for r in rows) if after else any(r['warehouseCode']==foreign for r in rows)
    warehouses = request('/mrp/stock-policies/warehouses',auth,200)
    assert [r['code'] for r in warehouses] == ([own] if after else [r['warehouseCode'] for r in mappings if r['enabled']])
    filtered = request('/mrp/stock-policies/page?'+urlencode({'warehouseCode':foreign,'keyword':material}),auth,200)
    assert filtered['total'] == (0 if after else 1)
    request('/mrp/stock-policies/'+str(fixture_ids[1]),auth,403 if after else 200,'PUT',
        {'warehouseCode':own,'materialCode':material,'safetyStockQty':7,'warningStockQty':70})
    assert sql(f'SELECT safety_stock_qty FROM stock_policy WHERE id={fixture_ids[1]};') == ['2.000000' if after else '7.000000']
    payload = workbook([[own, material, 8, 80],[foreign, material, 9, 90]])
    request('/mrp/stock-policies/import/preview',auth,200,'POST',upload=payload)
    request('/mrp/stock-policies/import',auth,403 if after else 200,'POST',upload=payload)
    assert sql(f'SELECT safety_stock_qty FROM stock_policy WHERE id={fixture_ids[0]};') == ['2.000000' if after else '8.000000']
    if after:
        request('/mrp/stock-policies/import',auth,200,'POST',upload=workbook([[own,material,3,30]]))
        own_logs = request('/audit-logs?'+urlencode({'resourceType':'stock_policy','resourceId':own+'|'+material}),auth,200)
        assert any(r['factory_scope']=='爱培科' for r in own_logs['items'])
        assert request('/audit-logs?'+urlencode({'resourceType':'stock_policy','resourceId':foreign+'|'+material}),auth,200)['total']==0
        if args.browser:
            env = os.environ.copy(); env.update(UAT_API_BASE='http://127.0.0.1:8180',UAT_UI_BASE='http://localhost:5181',
                UAT_STOCK_USER=stamp,UAT_STOCK_MATERIAL=material,UAT_STOCK_OWN=own,UAT_STOCK_FOREIGN=foreign,
                PLAYWRIGHT_USE_SYSTEM_CHROME='1',UAT_E2E_REPORT=str(ROOT/f'evidence/stock-scope{args.evidence_tag}-browser-report'),
                UAT_E2E_OUTPUT=str(ROOT/f'evidence/stock-scope{args.evidence_tag}-browser-output'))
            with (ROOT/f'evidence/stock-scope{args.evidence_tag}-browser.log').open('w') as log:
                subprocess.run(['./node_modules/.bin/playwright','test','--config=../test-kit/2026-10-16/e2e/playwright.config.mjs',
                    'stock-scope.spec.mjs'],cwd=ROOT.parents[1]/'frontend',env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
        request('/mrp/stock-policies/'+str(fixture_ids[0]),auth,200,'PUT',
            {'warehouseCode':foreign,'materialCode':'FORGED','safetyStockQty':4,'warningStockQty':40})
        request('/mrp/stock-policies',auth,403,'POST',
            {'warehouseCode':foreign,'materialCode':material,'safetyStockQty':0,'warningStockQty':0})
        sql(f"DELETE FROM user_factory_scope WHERE user_id={user['id']};")
        assert request('/mrp/stock-policies',auth,200)==[]
        assert request('/mrp/stock-policies/warehouses',auth,200)==[]
        assert request('/mrp/stock-policies/page',auth,200)['total']==0
        request('/mrp/stock-policies/'+str(fixture_ids[0]),auth,403,'DELETE')
    request('/mrp/stock-policies/'+str(fixture_ids[1]),auth,403 if after else 200,'DELETE')
    passed = True
finally:
    if fixture_ids: sql('DELETE FROM stock_policy WHERE id IN ('+','.join(map(str,fixture_ids))+');')
    if user: api.ok('/users/'+str(user['id']),'DELETE')
    if role: api.ok('/roles/'+str(role['id']),'DELETE')
    out.write_text(json.dumps({'status':'PASS' if passed else 'FAIL','checks':checks,
        'remainingFixtures':sql('SELECT COUNT(*) FROM stock_policy WHERE id IN ('+','.join(map(str,fixture_ids))+');') if fixture_ids else []},ensure_ascii=False,indent=2)+'\n')
print(args.phase,'PASS',len(checks),'real HTTP checks')
