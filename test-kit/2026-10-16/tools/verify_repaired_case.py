"""Independent Decimal oracle using frozen Shanghai edges; SELECT-only plus authenticated reads."""
import collections,datetime,gzip,json,os,pathlib,subprocess,sys,urllib.request
from decimal import Decimal as D,ROUND_HALF_UP,ROUND_FLOOR,getcontext
getcontext().prec=60
folder=pathlib.Path(sys.argv[1]); fixture=pathlib.Path(sys.argv[2]) if len(sys.argv)>2 else folder/'fixture.json'
cfg=json.loads(fixture.read_text()); manifest=json.loads((folder/'manifest.json').read_text())
bid=int(manifest['batchId']); sid=int(manifest['bomSnapshotId']); run=int(manifest['runId'])
data=pathlib.Path('test-kit/2026-10-15/data'); out=folder/'repair-oracle.json'
def sql(q):
 assert q.startswith('SELECT')
 raw=subprocess.run(['docker','exec','-i','mrp-testkit-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 --raw -u"$MYSQL_USER" -NB mrp_testkit'],input=q,text=True,capture_output=True,check=True).stdout
 return [json.loads(l) for l in raw.splitlines()]
edges=sql(f"SELECT JSON_OBJECT('parent',parent_code,'child',child_code,'pq',CAST(parent_qty AS CHAR),'cq',CAST(child_qty AS CHAR),'org',organization_code) FROM bom_snapshot_detail WHERE snapshot_id={sid} ORDER BY parent_code,child_code;")
assert edges and all(e['org']=='上海七十迈数字科技有限公司' for e in edges)
assert len(edges)==len({(e['parent'],e['child']) for e in edges})
adj=collections.defaultdict(list)
for e in edges:adj[e['parent']].append(e)
roles={r['categoryCode']:r['role'] for r in json.loads((data/'category-roles.json').read_text())}
materials={r['code']:r for r in json.load(gzip.open(data/'source-materials.json.gz','rt'))}
policy={r['materialCode']:r for r in cfg['materialPolicies']}; wh={'爱培科':'UAT_APK','永惠':'UAT_YH'}
stock=collections.defaultdict(D)
for r in cfg['inventory']:
 if r['quality']=='GOOD':stock[(r['warehouseCode'],r['materialCode'])]+=D(r['quantity'])
safety={(r['warehouseCode'],r['materialCode']):D(r['safetyStockQty']) for r in cfg['stockPolicies']}
used=collections.defaultdict(D); expected_paths={}; demands=collections.defaultdict(D)
for f in wh:
 for mi,month in enumerate(cfg['months']):
  for row in cfg['forecast']:
   if row['factoryCode']!=f or row['quantities'][mi]<=0:continue
   root=row['materialCode']; queue=collections.deque([(root,D(str(row['quantities'][mi])),(root,))])
   while queue:
    code,gross,path=queue.popleft(); k=(wh[f],code)
    before=max(D(0),stock[k]-safety.get(k,D(0))-used[k]); take=min(gross,before); net=gross-take;used[k]+=take
    expected_paths[(f,root,month,'>'.join(path),code)]=(gross,before,take,net)
    if len(path)==1 and net>0:demands[(f,root,month,root,'MACHINE_OUTPUT')]+=net
    if net<=0:continue
    if policy.get(code,{}).get('explosionPolicy')=='STOP' or not adj[code]:
     demands[(f,root,month,code,roles[materials[code]['category']])]+=net;continue
    for e in adj[code]:
     assert e['child'] not in path,'cycle in oracle'
     ratio=(D(e['cq'])/D(e['pq'])).quantize(D('1e-18'),rounding=ROUND_HALF_UP)
     queue.append((e['child'],net*ratio,path+(e['child'],)))
weeks=json.loads((data/'business-weeks.json').read_text()); calendar=json.loads((data/'production-calendars.json').read_text())
days={(r['factoryCode'],r['calendarDate']) for r in calendar if r['productionDay']}
daily=collections.defaultdict(D)
for (f,root,month,code,typ),qty in demands.items():
 src=datetime.date.fromisoformat(month); prev=(src-datetime.timedelta(days=1)).replace(day=1).isoformat()
 targets=[w for w in weeks if w['businessMonth']==prev][-1:]+[w for w in weeks if w['businessMonth']==month][:2]
 assert len(targets)==3
 scale=D(1) if typ=='MACHINE_OUTPUT' or qty==qty.to_integral_value() else D('1e-6')
 qty=qty.quantize(scale,rounding=ROUND_HALF_UP); part=(qty/3/scale).to_integral_value(rounding=ROUND_FLOOR)*scale
 for w,n in zip(targets,[qty-2*part,part,part]):
  dates=sorted(d for ff,d in days if ff==f and w['startDate']<=d<=w['endDate']);assert dates
  each=(n/len(dates)/scale).to_integral_value(rounding=ROUND_FLOOR)*scale;extra=int((n-each*len(dates))/scale)
  for i,day in enumerate(dates):
   amount=each+(scale if i<extra else 0)
   if amount:daily[(f,root,month,code,typ,day)]+=amount
actual=collections.defaultdict(D)
for r in json.loads((folder/('facts.json' if (folder/'facts.json').exists() else 'facts-sql.json')).read_text()):
 actual[tuple(r[x] for x in ['factoryCode','rootMaterialCode','sourceMonth','terminalMaterialCode','terminalType','scheduleDate'])]+=D(r['effectiveQuantity'])
diff=[{'key':k,'expected':str(daily.get(k,D(0))),'actual':str(actual.get(k,D(0)))} for k in daily.keys()|actual.keys() if daily.get(k,D(0))!=actual.get(k,D(0))]
paths=sql(f"SELECT JSON_OBJECT('factory',factory_code,'root',root_material_code,'month',CAST(source_month AS CHAR),'path',path_text,'code',current_material_code,'gross',CAST(gross_qty AS CHAR),'before',CAST(inventory_before_qty AS CHAR),'used',CAST(inventory_used_qty AS CHAR),'net',CAST(net_qty AS CHAR)) FROM mrp_requirement_detail WHERE batch_id={bid} AND run_id={run};")
actual_paths={}; path_errors=[]
for r in paths:
 key=(r['factory'],r['root'],r['month'],r['path'].replace('→','>'),r['code'])
 if key in actual_paths:path_errors.append({'key':key,'error':'duplicate persisted path'})
 actual_paths[key]=tuple(D(r[x]) for x in ['gross','before','used','net'])
for k in expected_paths.keys()|actual_paths.keys():
 if k not in actual_paths or k not in expected_paths or any(abs(a-b)>D('1e-6') for a,b in zip(expected_paths.get(k,()),actual_paths.get(k,()))):path_errors.append({'key':k,'expected':str(expected_paths.get(k)),'actual':str(actual_paths.get(k))})
base='http://127.0.0.1:8180/api/v1';token=None
def request(p,body=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 with urllib.request.urlopen(urllib.request.Request(base+p,data=None if body is None else json.dumps(body).encode(),headers=h),timeout=180) as response:j=json.load(response,parse_float=D)
 assert j['success'];return j['data']
token=request('/auth/login',{'username':'admin','password':os.environ['UAT_PASSWORD']})['token'];grid_errors=[];checked=0
# Validate current effective facts including manual transfers, separately from the initial independent oracle.
effective=request(f'/mrp/batches/{bid}/daily-plans'); grouped=collections.defaultdict(lambda:collections.defaultdict(D))
for r in effective:
 key=tuple(r[x] for x in ['factoryCode','rootMaterialCode','terminalMaterialCode','terminalType','supplyMode']);grouped[key][r['scheduleDate']]+=D(str(r['effectiveQuantity']))
for view in ['TERMINAL']:
 seen=set();page=1
 while True:
  grid=request(f'/mrp/batches/{bid}/rolling-grid?view={view}&page={page}&pageSize=100')
  for r in grid['rows']:
   key=tuple(r[x] for x in ['factoryCode','rootMaterialCode','terminalMaterialCode','terminalType','supplyMode']);want={k:v for k,v in grouped[key].items() if v};seen.add(key);checked+=1
   if {k:D(v) for k,v in r['dayQuantities'].items() if D(v)}!=want:grid_errors.append({'key':key,'error':'daily grid'})
   if D(r['rowTotal'])!=sum(want.values(),D(0)):grid_errors.append({'key':key,'error':'total'})
   week={w['weekKey']:sum((v for d,v in want.items() if w['weekStart']<=d<=w['weekEnd']),D(0)) for w in grid['weekGroups']}
   if {k:v for k,v in week.items() if v}!={k:D(v) for k,v in r['weekTotals'].items() if D(v)}:grid_errors.append({'key':key,'error':'weekly grid'})
  if len(seen)>=grid['totalRows']:break
  assert grid['rows'];page+=1
 selected={k for k,v in grouped.items() if (k[3]=='MACHINE_OUTPUT')==(view=='MACHINE_OUTPUT') and any(v.values())}
 if seen!=selected:grid_errors.append({'view':view,'error':'row coverage','expected':len(selected),'actual':len(seen)})
# The rolling main grid exposes terminal rows; machine output appears in capacity/drawer.
loads=collections.defaultdict(D)
selected={k for k in grouped if k[3]=='MACHINE_OUTPUT'}
for k in selected:
 for day,q in grouped[k].items():loads[(k[0],day)]+=q
for r in grid['capacityLoads']:
 if D(r['load'])!=loads[(r['factoryCode'],r['date'])]:grid_errors.append({'error':'machine capacity load','factory':r['factoryCode'],'date':r['date']})
# Preserve quantity units in both summary views; never add unlike units together.
calendar_id=sql(f"SELECT JSON_OBJECT('id',production_calendar_version_id) FROM mrp_calc_batch WHERE id={bid};")[0]['id']
units={r['code']:r['unit'] for r in sql(f"SELECT JSON_OBJECT('code',material_code,'unit',unit) FROM mrp_calendar_material_unit WHERE calendar_version_id={calendar_id};")}
summary_checks=0
for view in ['MACHINE_OUTPUT','TERMINAL']:
 for period,keyfield in [('weekly','weekKey'),('monthly','monthKey')]:
  expected=collections.defaultdict(D)
  for k,day_values in grouped.items():
   if (k[3]=='MACHINE_OUTPUT')!=(view=='MACHINE_OUTPUT'):continue
   unit='台' if view=='MACHINE_OUTPUT' else units.get(k[2])
   groupkey='UNIT|'+unit if unit and unit.strip() else 'MATERIAL|'+k[2]
   for day,q in day_values.items():
    periodkey=day[:7] if period=='monthly' else next(w['weekKey'] for w in grid['weekGroups'] if w['weekStart']<=day<=w['weekEnd'])
    expected[(periodkey,groupkey)]+=q
  received=collections.defaultdict(D)
  for r in request(f'/mrp/batches/{bid}/plans/{period}?view={view}'):
   received[(r[keyfield],r['aggregationKey'])]+=D(r['totalQuantity']);summary_checks+=1
  if {k:v for k,v in expected.items() if v}!={k:v for k,v in received.items() if v}:grid_errors.append({'error':view+' '+period+' aggregate','expected':str(dict(expected)),'actual':str(dict(received))})
result={'status':'FAIL' if diff or path_errors or grid_errors else 'PASS','batchId':bid,'snapshotId':sid,'snapshotEdges':len(edges),'duplicateEdges':0,'dailyExpectedKeys':len(daily),'dailyActualKeys':len(actual),'dailyMismatches':len(diff),'pathCount':len(paths),'pathMismatches':len(path_errors),'gridRowsChecked':checked,'summaryGroupsChecked':summary_checks,'gridMismatches':len(grid_errors),'dailySamples':diff[:10],'pathSamples':path_errors[:10],'gridSamples':grid_errors[:10],'limitations':['Path quantities compared with 0.000001 tolerance; daily and grid values compared exactly as Decimal.','Fixtures use the configured weekday calendar; not a holiday/reassignment case.']}
out.write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False));sys.exit(result['status']!='PASS')
