"""Compare old/new live snapshot authorization using disposable isolated fixtures only."""
import argparse, json, time, os, subprocess
import continuation_access as api
from continuation_publish import sql, ROOT

def run(phase):
    api.token=api.ok('/auth/login','POST',{'username':'admin','password':api.password()})['token']
    marker='iscope_'+hex(time.time_ns())[2:]
    role=user=None
    snapshots=[]
    checks=[]
    def check(path,want,auth,method='GET'):
        status,data=api.req(path,method,auth=auth)
        checks.append({'path':path,'method':method,'status':status,'expected':want})
        assert status==want,(path,status,want)
        return data.get('data')
    def snapshot(*warehouses):
        sid=int(sql(f"INSERT INTO inventory_snapshot(snapshot_date,status) VALUES('2026-10-16','{marker}');SELECT LAST_INSERT_ID();")[0])
        snapshots.append(sid)
        for warehouse in warehouses:
            sql(f"INSERT INTO inventory_item(snapshot_id,inventory_category,material_id,supplier_name,warehouse_code,inventory_quality,shipping_available_qty) VALUES({sid},'TEST','{marker}','TEST','{warehouse}','GOOD',10);")
        return sid
    passed=False
    try:
        role=api.ok('/roles','POST',{'code':marker,'name':'库存快照工厂域临时验收'})
        catalog=api.ok('/permissions');catalog=catalog if isinstance(catalog,list) else catalog['items']
        ids={p['code']:p['id'] for p in catalog}
        api.ok(f"/roles/{role['id']}/permissions",'PUT',{'permissionIds':[ids[c] for c in ['inventory:view','mrp_workspace:view','mrp_batch:list','mrp_inventory:allocate']]})
        user=api.ok('/users','POST',{'username':marker,'displayName':'库存快照工厂域临时验收','roleIds':[role['id']]})
        auth=api.ok('/auth/login','POST',{'username':marker,'password':'Mrp@123456'})['token']
        a,b,wa,wb=[marker+x for x in ['_A','_B','_WA','_WB']]
        sql(f"INSERT INTO factory_warehouse_mapping(factory_code,warehouse_code) VALUES('{a}','{wa}'),('{b}','{wb}');INSERT INTO user_factory_scope(user_id,factory_code) VALUES({user['id']},'{a}');")
        own,foreign,mixed,unknown,empty=snapshot(wa),snapshot(wb),snapshot(wa,wb),snapshot(marker+'_unknown'),snapshot()
        expected=200 if phase=='before' else 403
        for sid in [foreign,mixed,unknown,empty]:
            check(f'/inventory-snapshots/{sid}',expected,auth)
            check(f'/mrp/inventory/balances/{sid}',expected,auth)
        check(f'/inventory-snapshots/{own}',200,auth)
        listing=check('/inventory-snapshots/page?keyword='+marker+'&pageSize=100',200,auth)
        assert listing['total']==(5 if phase=='before' else 1)
        assert {x['id'] for x in listing['items']}==(set(snapshots) if phase=='before' else {own})
        filtered=check('/mrp/inventory/snapshots?warehouseCode='+wa,200,auth)
        assert {x['id'] for x in filtered}==({own,mixed} if phase=='before' else {own})
        check(f'/mrp/inventory/normalize/{mixed}?factoryCode={a}&inventoryOrg={wa}',expected,auth,'POST')
        assert sql(f'SELECT COUNT(*) FROM mrp_inventory_balance WHERE inventory_snapshot_id={mixed};')==[('2' if phase=='before' else '0')]
        sql(f"INSERT INTO user_factory_scope(user_id,factory_code) VALUES({user['id']},'{b}');")
        balances=check(f'/mrp/inventory/normalize/{mixed}?factoryCode={a}&inventoryOrg={wa}',200,auth,'POST')
        assert {x['factoryCode'] for x in balances}=={a,b}
        assert sql(f'SELECT COUNT(*) FROM inventory_item WHERE snapshot_id={mixed} AND shipping_available_qty=10;')==['2']
        sql(f"DELETE FROM user_factory_scope WHERE user_id={user['id']};INSERT INTO role_data_scope(role_id,scope_type) VALUES({role['id']},'ALL');")
        check(f'/inventory-snapshots/{unknown}',200,auth)
        sql(f"UPDATE role SET status='DISABLED' WHERE id={role['id']};")
        check(f'/inventory-snapshots/{own}',expected,auth)
        # Function permissions still apply independently of factory scope.
        check(f'/inventory-snapshots/{own}',200,api.token)
        api.ok(f"/roles/{role['id']}/permissions",'PUT',{'permissionIds':[]})
        check(f'/inventory-snapshots/{own}',403,auth)
        if phase=='after':
            sql(f"UPDATE role SET status='ACTIVE' WHERE id={role['id']};DELETE FROM role_data_scope WHERE role_id={role['id']};INSERT INTO user_factory_scope(user_id,factory_code) VALUES({user['id']},'{a}');")
            api.ok(f"/roles/{role['id']}/permissions",'PUT',{'permissionIds':[ids['inventory:view']]})
            env=os.environ.copy();env.update({'UAT_API_BASE':'http://127.0.0.1:8180','UAT_UI_BASE':'http://localhost:5181',
                'UAT_SCOPE_USERNAME':marker,'UAT_SCOPE_MARKER':marker,'UAT_SCOPE_OWN':str(own),'UAT_SCOPE_FOREIGN':str(foreign),
                'PLAYWRIGHT_USE_SYSTEM_CHROME':'1','UAT_E2E_REPORT':str(ROOT/'evidence/inventory-scope-browser-report'),
                'UAT_E2E_OUTPUT':str(ROOT/'evidence/inventory-scope-browser-results')})
            with (ROOT/'evidence/inventory-scope-browser.log').open('w') as output:
                subprocess.run(['npx','playwright','test','-c',str(ROOT/'e2e/playwright.config.mjs'),'inventory-scope.spec.mjs'],cwd=ROOT.parents[1]/'frontend',env=env,stdout=output,stderr=subprocess.STDOUT,check=True)
        passed=True
    finally:
        for sid in snapshots:
            sql(f'DELETE FROM mrp_inventory_balance WHERE inventory_snapshot_id={sid};DELETE FROM inventory_item WHERE snapshot_id={sid};DELETE FROM inventory_snapshot WHERE id={sid};')
        sql(f"DELETE FROM factory_warehouse_mapping WHERE factory_code LIKE '{marker}%';")
        if user:
            sql(f"DELETE FROM user_factory_scope WHERE user_id={user['id']};")
            api.ok('/users/'+str(user['id']),'DELETE')
        if role:api.ok('/roles/'+str(role['id']),'DELETE')
        remaining=sql(f"SELECT COUNT(*) FROM inventory_snapshot WHERE status='{marker}';")
        (ROOT/f'evidence/inventory-scope-{phase}-runtime.json').write_text(json.dumps({'status':'PASS' if passed else 'FAIL','phase':phase,'checks':checks,'fixtureSnapshots':snapshots,'remainingSnapshots':remaining},ensure_ascii=False,indent=2)+'\n')
    print('PASS',phase,len(checks),'live HTTP checks; temporary snapshots cleaned')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['before','after']);run(parser.parse_args().phase)
