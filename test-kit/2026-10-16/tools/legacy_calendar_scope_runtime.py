"""Legacy calendar authorization with temporary versions in the isolated DB only."""
import argparse, json, time, os, subprocess
from urllib.parse import urlencode
import continuation_access as api
from continuation_publish import ROOT, sql

parser=argparse.ArgumentParser(); parser.add_argument('phase',choices=['before','after']); parser.add_argument('--browser',action='store_true'); args=parser.parse_args()
after=args.phase=='after'; out=ROOT/f'evidence/legacy-calendar-scope-{args.phase}-runtime.json'
assert not out.exists(), 'Preserve existing evidence'
assert api.BASE=='http://127.0.0.1:8180/api/v1'
api.token=api.ok('/auth/login','POST',{'username':'admin','password':api.password()})['token']
stamp='lc_'+str(time.time_ns()); ids=[]; checks=[]; user=role=None; passed=False

def check(path,auth,want,method='GET',body=None):
    status,data=api.req(path,method,body,auth)
    checks.append({'path':path,'method':method,'status':status,'expected':want})
    assert status==want,(path,status,want)
    return data.get('data')

def fixture(factory,days):
    vid=int(sql(f"INSERT INTO production_calendar_version(version_no,factory_code,status) VALUES({time.time_ns()%2000000000},'{factory}','DRAFT'); SELECT LAST_INSERT_ID();")[-1]);ids.append(vid)
    for i,day in enumerate(days):
        sql(f"INSERT INTO production_calendar_day(version_id,factory_code,calendar_date,is_production_day,allocation_weight,daily_capacity,revision) VALUES({vid},'{day}','2035-01-{i+1:02}',1,1,100,0);")
    return vid

try:
    own=fixture('爱培科',['爱培科']); foreign=fixture('永惠',['永惠']); delete_id=fixture('永惠',[])
    mixed=fixture('ALL',['爱培科','永惠']); mismatch=fixture('爱培科',['永惠']); empty=fixture('ALL',[])
    role=api.ok('/roles','POST',{'code':stamp,'name':'历史日历域临时验收'})
    catalog=api.ok('/permissions'); catalog=catalog if isinstance(catalog,list) else catalog['items']; permissions={p['code']:p['id'] for p in catalog}
    api.ok(f"/roles/{role['id']}/permissions",'PUT',{'permissionIds':[permissions[c] for c in ['mrp_calendar:view','mrp_calendar:manage','mrp_batch:list']]})
    user=api.ok('/users','POST',{'username':stamp,'displayName':'临时历史日历验收','roleIds':[role['id']]})
    sql(f"INSERT INTO user_factory_scope(user_id,factory_code) VALUES({user['id']},'爱培科');")
    auth=api.ok('/auth/login','POST',{'username':stamp,'password':'Mrp@123456'})['token']
    root='/mrp/production-calendar-versions'
    listed=check(root,auth,200); visible={r['id'] for r in listed}
    assert own in visible and (not visible.intersection({foreign,mixed,mismatch,empty}) if after else foreign in visible)
    choices=check('/mrp/batch-creation-versions',auth,200)
    assert {r['id'] for r in choices['productionCalendarVersions']}==visible
    for vid in [foreign,mixed,mismatch,empty]:
        for suffix in ['', '/days']: check(f'{root}/{vid}'+suffix,auth,403 if after else 200)
    check(f'{root}/{foreign}/days',auth,403 if after else 200,'POST',[])
    check(f'{root}/{foreign}/publish',auth,403 if after else 200,'POST')
    check(f'{root}/{delete_id}',auth,403 if after else 200,'DELETE')
    if after:
        check(f'{root}/{own}',auth,200)
        check(f'{root}/{own}/days',auth,200,'POST',[{'factoryCode':'永惠','calendarDate':'2035-01-03','isProductionDay':True,'allocationWeight':1,'dailyCapacity':120}])
        assert sql(f'SELECT DISTINCT factory_code FROM production_calendar_day WHERE version_id={own};')==['爱培科']
        created=check(root+'?'+urlencode({'factoryCode':'爱培科'}),auth,200,'POST'); ids.append(created['id'])
        check(f"{root}/{created['id']}",auth,200,'DELETE')
        check(root+'?'+urlencode({'factoryCode':'永惠'}),auth,403,'POST')
        check(root+'?'+urlencode({'factoryCode':'ALL'}),auth,403,'POST')
        if args.browser:
            env=os.environ.copy(); env.update(UAT_API_BASE='http://127.0.0.1:8180',UAT_UI_BASE='http://localhost:5181',
                UAT_CALENDAR_USER=stamp,UAT_LEGACY_CALENDAR_OWN=str(own),UAT_LEGACY_CALENDAR_FOREIGN=str(foreign),
                PLAYWRIGHT_USE_SYSTEM_CHROME='1',UAT_E2E_REPORT=str(ROOT/'evidence/legacy-calendar-scope-browser-report'),
                UAT_E2E_OUTPUT=str(ROOT/'evidence/legacy-calendar-scope-browser-output'))
            with (ROOT/'evidence/legacy-calendar-scope-browser.log').open('x') as log:
                subprocess.run(['./node_modules/.bin/playwright','test','--config=../test-kit/2026-10-16/e2e/playwright.config.mjs','legacy-calendar-scope.spec.mjs'],
                    cwd=ROOT.parents[1]/'frontend',env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
        sql(f"INSERT INTO user_factory_scope(user_id,factory_code) VALUES({user['id']},'永惠');")
        check(f'{root}/{mixed}/days',auth,200)
        check(f'{root}/{mixed}/days',auth,403,'POST',[])
        check(f'{root}/{empty}',auth,403)
        sql(f"DELETE FROM user_factory_scope WHERE user_id={user['id']};")
        assert check(root,auth,200)==[]
        check(f'{root}/{own}',auth,403)
        sql(f"INSERT INTO role_data_scope(role_id,scope_type) VALUES({role['id']},'ALL');")
        check(f'{root}/{empty}',auth,200)
    passed=True
finally:
    if ids: sql('DELETE FROM production_calendar_day WHERE version_id IN ('+','.join(map(str,ids))+'); DELETE FROM production_calendar_version WHERE id IN ('+','.join(map(str,ids))+');')
    if user: api.ok('/users/'+str(user['id']),'DELETE')
    if role: api.ok('/roles/'+str(role['id']),'DELETE')
    out.write_text(json.dumps({'status':'PASS' if passed else 'FAIL','checks':checks,'fixtureIds':ids,'remainingFixtures':sql('SELECT COUNT(*) FROM production_calendar_version WHERE id IN ('+','.join(map(str,ids))+');') if ids else []},ensure_ascii=False,indent=2)+'\n')
print(args.phase,'PASS',len(checks),'real HTTP checks')
