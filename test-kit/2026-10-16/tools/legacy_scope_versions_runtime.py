"""Disposable historical mapping/stock version HTTP checks in isolated MySQL only."""
import argparse, json, time
import continuation_access as api
from continuation_publish import ROOT, sql
p=argparse.ArgumentParser();p.add_argument('phase',choices=['before','after']);a=p.parse_args();after=a.phase=='after'
out=ROOT/f'evidence/legacy-scope-versions-{a.phase}-runtime.json';assert not out.exists()
assert api.BASE=='http://127.0.0.1:8180/api/v1'
api.token=api.ok('/auth/login','POST',{'username':'admin','password':api.password()})['token']
checks=[];maps=[];stocks=[];batches=[];user=role=None;passed=False;stamp='ls_'+str(time.time_ns())
M='/mrp/factory-warehouse-mapping-versions';S='/mrp/stock-policy-versions'
def check(path,auth,want=200,method='GET',body=None):
 status,data=api.req(path,method,body,auth);checks.append(dict(path=path,method=method,status=status,expected=want));assert status==want,(path,status,want);return data.get('data')
def version(table,rows):
 vid=int(sql(f"INSERT INTO {table}_version(version_no,status) SELECT COALESCE(MAX(version_no),0)+1,'DRAFT' FROM {table}_version;SELECT LAST_INSERT_ID();")[-1]);(maps if table=='factory_warehouse_mapping' else stocks).append(vid)
 for row in rows:
  if table=='factory_warehouse_mapping':sql(f"INSERT INTO {table}_detail(version_id,factory_code,warehouse_code,enabled) VALUES({vid},'{row[0]}','{row[1]}',1);")
  else:sql(f"INSERT INTO stock_policy_detail(version_id,warehouse_code,material_code,safety_stock_qty,warning_stock_qty) VALUES({vid},'{row[0]}','{row[1]}',1,2);")
 return vid
try:
 own=version('factory_warehouse_mapping',[('爱培科','LS_APK')]);foreign=version('factory_warehouse_mapping',[('永惠','LS_YH')]);mixed=version('factory_warehouse_mapping',[('爱培科','LS_APK'),('永惠','LS_YH')]);empty=version('factory_warehouse_mapping',[])
 stock=version('stock_policy',[('LS_APK','LS_M')]);unbound=version('stock_policy',[('LS_YH','LS_M')]);unknown=version('stock_policy',[('LS_UNKNOWN','LS_M')])
 batch=int(sql(f"INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version,stock_policy_version_id,factory_warehouse_mapping_version_id) VALUES('爱培科','CANCELLED',1,'v4-rolling-business-weeks',{stock},{own});SELECT LAST_INSERT_ID();")[-1]);batches.append(batch)
 sql(f"INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version,stock_policy_version_id,factory_warehouse_mapping_version_id) VALUES('爱培科','CANCELLED',1,'v4-rolling-business-weeks',{unknown},{own});");batches.append(int(sql('SELECT MAX(id) FROM mrp_calc_batch;')[0]))
 role=api.ok('/roles','POST',{'code':stamp,'name':'历史配置域临时验收'})
 catalog=api.ok('/permissions');catalog=catalog if isinstance(catalog,list) else catalog['items'];ids={r['code']:r['id'] for r in catalog}
 api.ok(f"/roles/{role['id']}/permissions",'PUT',{'permissionIds':[ids[x] for x in ['mrp_warehouse_mapping:view','mrp_warehouse_mapping:manage','mrp_stock_policy:view','mrp_stock_policy:manage']]})
 user=api.ok('/users','POST',{'username':stamp,'displayName':'临时历史配置域','roleIds':[role['id']]})
 sql(f"INSERT INTO user_factory_scope(user_id,factory_code) VALUES({user['id']},'爱培科');")
 auth=api.ok('/auth/login','POST',{'username':stamp,'password':'Mrp@123456'})['token']
 listed={r['id'] for r in check(M,auth)};assert own in listed and (not listed.intersection({foreign,mixed,empty}) if after else foreign in listed)
 policies={r['id'] for r in check(S,auth)};assert stock in policies and (not policies.intersection({unbound,unknown}) if after else unbound in policies)
 check(f'{M}/{own}/details',auth);check(f'{S}/{stock}/details',auth)
 for vid in [foreign,mixed,empty]:check(f'{M}/{vid}/details',auth,403 if after else 200)
 for vid in [unbound,unknown]:check(f'{S}/{vid}/details',auth,403 if after else 200)
 check(f'{M}/{foreign}/details',auth,403 if after else 200,'POST',[{'factoryCode':'永惠','warehouseCode':'LS_YH','enabled':True}])
 check(f'{M}/{foreign}/publish',auth,403 if after else 200,'POST')
 check(f'{S}/{unbound}/details',auth,403 if after else 200,'POST',[])
 check(f'{S}/{unbound}/publish',auth,403 if after else 200,'POST')
 if after:
  check(f'{M}/{own}/details',auth,403,'POST',[{'factoryCode':'永惠','warehouseCode':'LS_YH','enabled':True}]);assert sql(f'SELECT factory_code FROM factory_warehouse_mapping_detail WHERE version_id={own}')==['爱培科']
  for root,vid in [(M,empty),(S,unknown)]:check(f'{root}/{vid}',auth,403,'DELETE')
  check(M,auth,403,'POST',{});check(S,auth,403,'POST',{})
  check(f'{S}/{stock}/incremental',auth,403,'POST',{'overrides':[{'warehouseCode':'LS_APK','materialCode':'LS_M'}]})
  check(f'{S}/{stock}/details',auth,403,'POST',[])
  sql(f"INSERT INTO user_factory_scope(user_id,factory_code) VALUES({user['id']},'永惠');")
  check(f'{M}/{mixed}/details',auth);check(f'{S}/{unbound}/details',auth,403)
  sql(f"DELETE FROM user_factory_scope WHERE user_id={user['id']};")
  assert check(M,auth)==[] and check(S,auth)==[]
  check(f'{M}/{own}/details',auth,403);check(f'{S}/{stock}/details',auth,403)
  sql(f"INSERT INTO role_data_scope(role_id,scope_type) VALUES({role['id']},'ALL');")
  check(f'{M}/{empty}/details',auth);check(f'{S}/{unknown}/details',auth)
  created=check(S,auth,200,'POST',{});stocks.append(created['id']);check(f"{S}/{created['id']}",auth,200,'DELETE')
 passed=True
finally:
 for bid in batches:sql(f'DELETE FROM mrp_calc_batch WHERE id={bid};')
 for table,values in [('factory_warehouse_mapping',maps),('stock_policy',stocks)]:
  for vid in values:sql(f'DELETE FROM {table}_detail WHERE version_id={vid};DELETE FROM {table}_version WHERE id={vid};')
 if user:api.ok(f"/users/{user['id']}",'DELETE')
 if role:api.ok(f"/roles/{role['id']}",'DELETE')
 remaining={table:int(sql(f'SELECT COUNT(*) FROM {table}_version WHERE id IN ('+','.join(map(str,values))+');')[0]) for table,values in [('factory_warehouse_mapping',maps),('stock_policy',stocks)] if values}
 out.write_text(json.dumps(dict(status='PASS' if passed else 'FAIL',checks=checks,remainingFixtures=remaining),ensure_ascii=False,indent=2)+'\n')
print(a.phase,'PASS',len(checks),'HTTP checks; fixtures cleaned')
