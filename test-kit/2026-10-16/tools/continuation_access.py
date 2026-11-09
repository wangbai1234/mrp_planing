"""Live HTTP authorization regression in isolated database with temporary identities/fixtures."""
import ast, json, pathlib, time, urllib.error, urllib.request
from continuation_publish import sql, ROOT
BASE='http://127.0.0.1:8180/api/v1'
token=None; results=[]
def req(path,method='GET',body=None,auth=None):
    headers={'Content-Type':'application/json'}
    if auth or token:headers['Authorization']='Bearer '+(auth or token)
    try:
        with urllib.request.urlopen(urllib.request.Request(BASE+path,headers=headers,method=method,data=None if body is None else json.dumps(body).encode()),timeout=60) as r:return r.status,json.load(r)
    except urllib.error.HTTPError as e:return e.code,json.load(e)
def ok(path,method='GET',body=None,auth=None):
    status,data=req(path,method,body,auth);assert status<300 and data['success'],(path,status);return data.get('data')
def check(path,want,auth,method='GET',body=None):
    status,data=req(path,method,body,auth);results.append({'path':path,'method':method,'status':status,'expected':want});assert status==want,(path,status,want)
def password():
    for node in ast.walk(ast.parse(pathlib.Path('/tmp/mrp-daily-api-uat.py').read_text())):
        if isinstance(node,ast.Dict):
            for k,v in zip(node.keys,node.values):
                if isinstance(k,ast.Constant) and k.value=='password' and isinstance(v,ast.Constant):return v.value
    raise AssertionError('Missing test credential')
if __name__=='__main__':
    token=ok('/auth/login','POST',{'username':'admin','password':password()})['token']
    stamp=str(time.time_ns()); role=None; user=None; batches=[]
    try:
        role=ok('/roles','POST',{'code':'scope_'+stamp,'name':'工厂域验收临时角色'})
        catalog=ok('/permissions'); catalog=catalog if isinstance(catalog,list) else catalog['items']; ids={p['code']:p['id'] for p in catalog}
        ok(f"/roles/{role['id']}/permissions",'PUT',{'permissionIds':[ids[c] for c in ['mrp_batch:list','mrp_batch:calculate','mrp_batch:publish','mrp_inventory:allocate','mrp_decision:manage']]})
        user=ok('/users','POST',{'username':'scope_'+stamp,'displayName':'临时工厂域验收','roleIds':[role['id']]})
        auth=ok('/auth/login','POST',{'username':'scope_'+stamp,'password':'Mrp@123456'})['token']
        for factory in ['爱培科','永惠','ALL']:
            batches.append(int(sql("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version) VALUES('"+factory+"','READY',1,'v4-rolling-business-weeks');SELECT LAST_INSERT_ID();")[0]))
        own,foreign,combined=batches
        sql("INSERT INTO user_factory_scope(user_id,factory_code) VALUES("+str(user['id'])+",'爱培科');")
        check(f'/mrp/batches/{own}',200,auth)
        visible=ok('/mrp/batches','GET',auth=auth); found={b['id'] for b in visible};assert own in found and not found.intersection({foreign,combined})
        for batch in [foreign,combined]:
            for suffix in ['', '/summary','/daily-plans','/plans/weekly','/plans/monthly','/plans/conservation','/rolling-grid','/capacity-review','/execution','/requirements','/terminal-demands']:
                check(f'/mrp/batches/{batch}'+suffix,403,auth)
            for suffix in ['/publish','/calculate','/cancel','/revise']:
                check(f'/mrp/batches/{batch}'+suffix,403,auth,'POST',{})
        pool=int(sql(f"INSERT INTO mrp_inventory_pool(batch_id,snapshot_id,factory_code,inventory_org,material_code) VALUES({foreign},1,'永惠','test','TEST');SELECT LAST_INSERT_ID();")[0])
        check(f'/mrp/inventory/pools/detail/{pool}',403,auth)
        check('/mrp/inventory/allocations/commit',403,auth,'POST',{'poolId':pool,'grossQty':100})
        run=int(sql(f"INSERT INTO mrp_calc_run(batch_id,status) VALUES({foreign},'ACTIVE');SELECT LAST_INSERT_ID();")[0])
        check(f'/mrp/inventory/stock-risks/{own}?runId={run}',403,auth)
        sql(f"INSERT INTO user_factory_scope(user_id,factory_code) VALUES({user['id']},'永惠');")
        check(f'/mrp/batches/{combined}',200,auth)
        check(f'/mrp/inventory/stock-risks/{own}?runId={run}',403,auth)
        sql(f"DELETE FROM user_factory_scope WHERE user_id={user['id']};INSERT INTO role_data_scope(role_id,scope_type,scope_value) VALUES({role['id']},'ALL',NULL);")
        check(f'/mrp/batches/{combined}',200,auth)
        sql(f"UPDATE role SET status='DISABLED' WHERE id={role['id']};")
        check(f'/mrp/batches/{combined}',403,auth)
        assert sql(f'SELECT COUNT(*) FROM mrp_inventory_allocation WHERE pool_id={pool};')==['0']
    finally:
        for batch in batches:
            sql(f'DELETE FROM mrp_inventory_pool WHERE batch_id={batch};DELETE FROM mrp_calc_run WHERE batch_id={batch};DELETE FROM mrp_calc_batch WHERE id={batch};')
        if user:ok('/users/'+str(user['id']),'DELETE')
        if role:ok('/roles/'+str(role['id']),'DELETE')
        (ROOT/'evidence/continuation-access-runtime.json').write_text(json.dumps({'results':results,'status':'PASS' if results and all(r['status']==r['expected'] for r in results) else 'FAIL'},ensure_ascii=False,indent=2)+'\n')
    print('PASS',len(results),'live HTTP factory-scope checks')
