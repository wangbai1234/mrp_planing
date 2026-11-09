"""Read-only main batch91 exact grid/capacity and concurrent reads."""
import json,urllib.request,os,time,pathlib,collections,decimal,concurrent.futures
D=decimal.Decimal
root=pathlib.Path(__file__).resolve().parents[1]; folder=next((root/'evidence/main-runs').glob('load-distinct-*'))
base='http://127.0.0.1:8080/api/v1'; times=[]
def request(p,body=None):
 start=time.monotonic(); data=None if body is None else json.dumps(body).encode()
 h={'Content-Type':'application/json'}
 if 'token' in globals():h['Authorization']='Bearer '+token
 with urllib.request.urlopen(urllib.request.Request(base+p,data=data,headers=h),timeout=180) as r:raw=r.read()
 j=json.loads(raw); assert j['success']; times.append({'path':p,'ms':round((time.monotonic()-start)*1000),'bytes':len(raw)}); return j['data']
token=request('/auth/login',{'username':'admin','password':os.environ['UAT_PASSWORD']})['token']
p='/mrp/batches/91'; before=request(p); facts=json.load(open(folder/'facts-sql.json')); expected=collections.defaultdict(lambda:collections.defaultdict(D)); loads=collections.defaultdict(D)
for f in facts:
 if f['terminalType']=='MACHINE_OUTPUT':loads[(f['factoryCode'],f['scheduleDate'])]+=D(f['effectiveQuantity']);continue
 k='|'.join(str(f[x]) for x in ['factoryCode','rootMaterialCode','terminalMaterialCode','terminalType','supplyMode']);expected[k][f['scheduleDate']]+=D(f['effectiveQuantity'])
errors=[];seen=set(); g=request(p+'/rolling-grid?page=1&pageSize=100'); groups=g['weekGroups']; total=g['totalRows']
for page in range(1,(total+99)//100+1):
 grid=g if page==1 else request(p+f'/rolling-grid?page={page}&pageSize=100')
 for row in grid['rows']:
  k=row['rowKey']; actual={day:D(q) for day,q in row['dayQuantities'].items()}; want=dict(expected[k]);seen.add(k)
  if actual!=want:errors.append({'key':k,'error':'daily quantities'})
  if D(row['rowTotal'])!=sum(want.values(),D(0)):errors.append({'key':k,'error':'rowTotal'})
  weeks={w['weekKey']:sum((q for day,q in want.items() if w['weekStart']<=day<=w['weekEnd']),D(0)) for w in groups};weeks={k:v for k,v in weeks.items() if v}
  actualweek={k:D(v) for k,v in row['weekTotals'].items() if D(v)}
  if weeks!=actualweek:errors.append({'key':k,'error':'weekly totals'})
assert seen==set(expected), (len(seen),len(expected))
cap=request(p+'/capacity-loads?startDate=2026-10-29&endDate=2027-04-30');caperrors=[]
for c in cap:
 want=loads[(c['factoryCode'],c['date'])]
 if D(c['load'])!=want:caperrors.append({'factory':c['factoryCode'],'date':c['date'],'error':'load'})
 if c['configured'] and c['productionDay']:
  if D(c['excess'])!=max(D(0),want-D(c['dailyCapacity'])):caperrors.append({'date':c['date'],'error':'excess'})
  if want>D(c['dailyCapacity']) and c['status']!='OVERLOAD':caperrors.append({'date':c['date'],'error':'status'})
filtered=[]
for factory in ['爱培科','永惠']:
 q=request(p+'/rolling-grid?page=1&pageSize=100&factoryCode='+urllib.parse.quote(factory));filtered.append({'factory':factory,'total':q['totalRows'],'factories':sorted(set(r['factoryCode'] for r in q['rows']))})
 if any(r['factoryCode']!=factory for r in q['rows']):errors.append({'factory':factory,'error':'filter'})
concurrency=[]
for n in [1,2,5]:
 begin=time.monotonic()
 with concurrent.futures.ThreadPoolExecutor(max_workers=n) as pool:results=list(pool.map(lambda _:request(p+'/rolling-grid?page=1&pageSize=20'),range(n)))
 concurrency.append({'requests':n,'wallMs':round((time.monotonic()-begin)*1000),'consistent':all(r==results[0] for r in results)})
after=request(p);assert (before['activeRunId'],before['version'])==(after['activeRunId'],after['version'])
result={'status':'PASS' if not errors and not caperrors else 'FAIL','batchId':91,'visualRows':len(seen),'dailyKeys':sum(len(x) for x in expected.values()),'gridErrors':errors[:20],'capacityErrors':caperrors[:20],'capacityDays':len(cap),'filters':filtered,'concurrentRead':concurrency,'timings':times}
(root/'evidence/grid-exact.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps({k:v for k,v in result.items() if k!='timings'},ensure_ascii=False))
