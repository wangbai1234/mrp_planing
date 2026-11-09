"""Audit warehouse delimiter regression, using temporary isolated fixtures only."""
import argparse, io, json, time, urllib.error, urllib.parse, urllib.request
import openpyxl
import continuation_access as api
from continuation_publish import ROOT, sql

parser=argparse.ArgumentParser(); parser.add_argument('phase',choices=['before','after']); parser.add_argument('--evidence-tag',default=''); args=parser.parse_args()
out=ROOT/f'evidence/stock-audit-delimiter-{args.phase}{args.evidence_tag}-runtime.json'
assert not out.exists(), 'Preserve existing evidence'
assert api.BASE=='http://127.0.0.1:8180/api/v1'
api.token=api.ok('/auth/login','POST',{'username':'admin','password':api.password()})['token']
stamp='audit_'+str(time.time_ns()); checks=[]; audit_checks=[]; role=user=None; restored=False; passed=False
mappings=[]; warehouses=[]
def quote(value): return "'"+value.replace("'","''")+"'"
def request(path,auth,method='GET',body=None,upload=None):
    headers={'Authorization':'Bearer '+auth,'Content-Type':'application/json'}
    data=None if body is None else json.dumps(body).encode()
    if upload:
        headers['Content-Type']='multipart/form-data; boundary=audit-delimiter'
        data=(b'--audit-delimiter\r\nContent-Disposition: form-data; name="file"; filename="scope.xlsx"\r\n'
              b'Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n'+upload+b'\r\n--audit-delimiter--\r\n')
    try:
        with urllib.request.urlopen(urllib.request.Request(api.BASE+path,headers=headers,data=data,method=method),timeout=60) as response:
            status,raw=response.status,response.read()
    except urllib.error.HTTPError as error: status,raw=error.code,error.read()
    checks.append({'path':path,'method':method,'status':status,'expected':200})
    assert status==200,(path,status)
    return json.loads(raw).get('data')
def payload(warehouse,material,safety):
    return dict(warehouseCode=warehouse,materialCode=material,safetyStockQty=safety,warningStockQty=80)
def upload(warehouse,material):
    book=openpyxl.Workbook(); sheet=book.active
    sheet.append(['仓库编码','物料料号','安全库存','预警库存']); sheet.append(['说明']*4)
    sheet.append([warehouse,material,5,50]); buffer=io.BytesIO(); book.save(buffer); return buffer.getvalue()
try:
    mappings=[r for r in api.ok('/mrp/factory-warehouse-mappings') if r['factoryCode'] in ['爱培科','永惠']]
    assert len(mappings)==2 and all(r['enabled'] for r in mappings)
    material=sql('SELECT m.material_code FROM material m WHERE m.is_active=1 AND m.is_deleted=0 ORDER BY m.id LIMIT 1;')[0]
    catalog=api.ok('/permissions'); ids={p['code']:p['id'] for p in (catalog if isinstance(catalog,list) else catalog['items'])}
    role=api.ok('/roles','POST',{'code':stamp,'name':'审计分隔符隔离验收'})
    api.ok(f"/roles/{role['id']}/permissions",'PUT',{'permissionIds':[ids[c] for c in ['mrp_stock_policy:view','mrp_stock_policy:manage','audit:view']]})
    user=api.ok('/users','POST',{'username':stamp,'displayName':'审计隔离验收','roleIds':[role['id']]})
    sql(f"INSERT INTO user_factory_scope(user_id,factory_code) VALUES({user['id']},'爱培科');")
    auth=api.ok('/auth/login','POST',{'username':stamp,'password':'Mrp@123456'})['token']
    for prefix_exists in [True,False]:
        prefix=stamp+('_f' if prefix_exists else '_u'); warehouse=prefix+'|X'; warehouses.append(warehouse)
        foreign=prefix if prefix_exists else stamp+'_other'
        sql(f"UPDATE factory_warehouse_mapping SET warehouse_code={quote(warehouse)} WHERE factory_code='爱培科';"
            f"UPDATE factory_warehouse_mapping SET warehouse_code={quote(foreign)} WHERE factory_code='永惠';"
            f"UPDATE user_factory_scope SET factory_code='爱培科' WHERE user_id={user['id']};")
        row=request('/mrp/stock-policies',auth,'POST',payload(warehouse,material,3)); identity=row['id']
        query=urllib.parse.urlencode({'resourceType':'stock_policy','resourceId':warehouse+'|'+material})
        expected='爱培科' if args.phase=='after' else ('永惠' if prefix_exists else 'ALL')
        scope=sql(f"SELECT factory_scope FROM audit_log WHERE operator_id={user['id']} AND resource_id={quote(warehouse+'|'+material)} ORDER BY id DESC LIMIT 1;")[0]
        assert scope==expected,(scope,expected)
        if args.phase=='after':
            request('/mrp/stock-policies/'+str(identity),auth,'PUT',payload(foreign,material,4))
            request('/mrp/stock-policies/import',auth,'POST',upload=upload(warehouse,material))
            request('/mrp/stock-policies/'+str(identity),auth,'DELETE')
            request('/mrp/stock-policies/import',auth,'POST',upload=upload(warehouse,material))
            assert request('/audit-logs?'+query,auth)['total']==5
            scopes=sql(f"SELECT factory_scope FROM audit_log WHERE operator_id={user['id']} AND resource_id={quote(warehouse+'|'+material)} ORDER BY id;")
            assert scopes==['爱培科']*5,scopes
        sql(f"UPDATE user_factory_scope SET factory_code='永惠' WHERE user_id={user['id']};")
        visible=request('/audit-logs?'+query,auth)['total']
        assert visible==(0 if args.phase=='after' or not prefix_exists else 1),visible
        audit_checks.append({'warehouseCode':warehouse,'prefixMappedToOtherFactory':prefix_exists,
                             'actualCreateScope':scope,'expectedCreateScope':expected,'otherFactoryVisibleAuditRows':visible})
    passed=True
finally:
    if warehouses: sql('DELETE FROM stock_policy WHERE warehouse_code IN ('+','.join(map(quote,warehouses))+');')
    for row in mappings:
        sql(f"UPDATE factory_warehouse_mapping SET warehouse_code={quote(row['warehouseCode'])} WHERE id={row['id']};")
    if mappings:
        actual=api.ok('/mrp/factory-warehouse-mappings')
        restored=all(next(r for r in actual if r['id']==m['id'])==m for m in mappings)
        assert restored, 'Mapping restoration failed'
    if user: api.ok('/users/'+str(user['id']),'DELETE')
    if role: api.ok('/roles/'+str(role['id']),'DELETE')
    remaining=sql('SELECT COUNT(*) FROM stock_policy WHERE warehouse_code IN ('+','.join(map(quote,warehouses))+');') if warehouses else []
    out.write_text(json.dumps({'status':'PASS' if passed else 'FAIL','phase':args.phase,'checks':checks,'auditChecks':audit_checks,
        'mappingsRestored':restored,'remainingFixtures':remaining},ensure_ascii=False,indent=2)+'\n')
print(args.phase,'PASS',len(checks),'real HTTP checks; mappings restored',restored)
