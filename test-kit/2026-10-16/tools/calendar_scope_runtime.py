"""Isolated current configuration scope checks; temporary identity/calendar rows only."""
import argparse,json,time,urllib.request,urllib.error,os,subprocess
import continuation_access as api
from continuation_publish import ROOT,sql
parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['before','after']);parser.add_argument('--browser',action='store_true');args=parser.parse_args()
after=args.phase=='after';stamp='cal_'+str(time.time_ns());user=role=None;checks=[];passed=False
api.token=api.ok('/auth/login','POST',{'username':'admin','password':api.password()})['token']
def request(path,auth,want,method='GET',body=None):
 req=urllib.request.Request(api.BASE+path,method=method,headers={'Authorization':'Bearer '+auth,'Content-Type':'application/json'},data=None if body is None else json.dumps(body).encode())
 try:
  with urllib.request.urlopen(req,timeout=60) as res:status,data=res.status,res.read()
 except urllib.error.HTTPError as error:status,data=error.code,error.read()
 checks.append({'path':path,'method':method,'status':status,'expected':want});assert status==want,(path,status,want)
 return json.loads(data)['data'] if status==200 and not path.startswith('/mrp/production-calendars/export') else data
try:
 catalog=api.ok('/permissions');ids={p['code']:p['id'] for p in (catalog if isinstance(catalog,list) else catalog['items'])}
 role=api.ok('/roles','POST',{'code':stamp,'name':'日历工厂域临时验收'})
 api.ok(f"/roles/{role['id']}/permissions",'PUT',{'permissionIds':[ids[c] for c in ['mrp_calendar:view','mrp_calendar:manage','mrp_warehouse_mapping:view','mrp_warehouse_mapping:manage']]})
 user=api.ok('/users','POST',{'username':stamp,'displayName':'日历临时验收','roleIds':[role['id']]})
 auth=api.ok('/auth/login','POST',{'username':stamp,'password':'Mrp@123456'})['token']
 sql(f"INSERT INTO user_factory_scope(user_id,factory_code) VALUES({user['id']},'爱培科');")
 factories=request('/mrp/factories',auth,200);assert factories==(['爱培科'] if after else ['爱培科','永惠'])
 mappings=request('/mrp/factory-warehouse-mappings',auth,200)
 assert all(row['factoryCode']=='爱培科' for row in mappings) if after else any(row['factoryCode']=='永惠' for row in mappings)
 for factory in ['爱培科','永惠']:
  want=403 if after and factory=='永惠' else 200
  from urllib.parse import urlencode
  for suffix,query in [('',{'factoryCode':factory}),('/month',{'factoryCode':factory,'month':'2088-10'}),('/page',{'factoryCode':factory,'month':'2088-10'}),('/export',{'factoryCode':factory,'month':'2088-10'})]:
   request('/mrp/production-calendars'+suffix+'?'+urlencode(query),auth,want)
 if after:
  foreign=next(row for row in api.ok('/mrp/factory-warehouse-mappings') if row['factoryCode']=='永惠')
  for method in ['PUT','DELETE']:
   request('/mrp/factory-warehouse-mappings/'+str(foreign['id']),auth,403,method,{'factoryCode':'爱培科','warehouseCode':'FORGED'} if method=='PUT' else None)
  request('/mrp/factory-warehouse-mappings',auth,403,'POST',{'factoryCode':'永惠','warehouseCode':'FORGED'})
  body={'factoryCode':'爱培科','startDate':'2088-10-01','endDate':'2088-10-01','weekdays':[5],'copyFromFactory':'永惠','overwrite':True}
  request('/mrp/production-calendars/batch/preview',auth,403,'POST',body)
  request('/mrp/production-calendars/batch',auth,403,'POST',body)
  request('/mrp/production-calendars',auth,403,'POST',{'factoryCode':'永惠','calendarDate':'2088-10-01','productionDay':True,'dailyCapacity':100,'capacityUnit':'台'})
  if args.browser:
   env=os.environ.copy()
   env.update(UAT_API_BASE='http://127.0.0.1:8180',UAT_UI_BASE='http://localhost:5181',UAT_CALENDAR_USER=stamp,
    PLAYWRIGHT_USE_SYSTEM_CHROME='1',UAT_E2E_REPORT=str(ROOT/'evidence/calendar-scope-browser-report'),UAT_E2E_OUTPUT=str(ROOT/'evidence/calendar-scope-browser-output'))
   with (ROOT/'evidence/calendar-scope-browser.log').open('w') as log:
    subprocess.run(['./node_modules/.bin/playwright','test','--config=../test-kit/2026-10-16/e2e/playwright.config.mjs','calendar-scope.spec.mjs'],
     cwd=ROOT.parents[1]/'frontend',env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
  sql(f"DELETE FROM user_factory_scope WHERE user_id={user['id']};")
  assert request('/mrp/factories',auth,200)==[]
  assert request('/mrp/factory-warehouse-mappings',auth,200)==[]
  request('/mrp/production-calendars/month?factoryCode=%E7%88%B1%E5%9F%B9%E7%A7%91&month=2088-10',auth,403)
 passed=True
finally:
 if user:api.ok('/users/'+str(user['id']),'DELETE')
 if role:api.ok('/roles/'+str(role['id']),'DELETE')
 remaining=sql(f"SELECT COUNT(*) FROM user WHERE username='{stamp}';")
 (ROOT/f'evidence/calendar-scope-{args.phase}-runtime.json').write_text(json.dumps({'status':'PASS' if passed else 'FAIL','checks':checks,'remainingFixtures':remaining},ensure_ascii=False,indent=2)+'\n')
print(args.phase,'PASS',len(checks),'real HTTP checks')
