#!/usr/bin/env python3
"""Deterministic fixtures + independent Decimal oracle; never connects to a database."""
import json,gzip,pathlib,calendar,collections,csv,hashlib
from decimal import Decimal,ROUND_HALF_UP,ROUND_FLOOR,getcontext
from datetime import date,timedelta
getcontext().prec=60
P=pathlib.Path(__file__).resolve().parents[1];D=P/'data';E=P/'expected'
def load(n):return json.load(gzip.open(D/n,'rt',encoding='utf8'))
def write(n,v): (D/n).write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
def dec(v):return Decimal(str(v))
def s(v):return format(v,'f') if isinstance(v,Decimal) else v
def monthadd(d,n):return date(d.year+(d.month-1+n)//12,(d.month-1+n)%12+1,1)
MONTHS=[monthadd(date(2026,11,1),i) for i in range(6)]
FACTORIES=['爱培科','永惠']; WH={'爱培科':'UAT_APK','永惠':'UAT_YH'}
materials={x['code']:x for x in load('source-materials.json.gz') if not x['deleted']}
cats=load('source-categories.json.gz');edges=load('source-bom.json.gz');adj=collections.defaultdict(list)
for x in edges:
 if dec(x['cq'] or 0)>0:adj[x['parent']].append(x)
for v in adj.values():v.sort(key=lambda e:(e['child'],e['version'] is None,e['version'] or ''))
frontier=set(json.loads((D/'stop-frontier.json').read_text()))
def traverse(r,stops=frozenset(),path=()):
 if r in path:raise ValueError('cycle')
 if r not in materials or not materials[r]['category']:raise ValueError('no calculation category mapping')
 out=[r]
 if r not in stops:
  for e in adj[r]:out+=traverse(e['child'],stops,path+(r,))
 return out
eligible=json.loads((D/'eligible-roots.json').read_text());full=[x[0] for x in eligible if x[1]>1]
small=full[-5:][::-1]
wide=[]
for c,v in materials.items():
 if (v['category'] or '').startswith('09') and c in adj:
  try:traverse(c,frontier);wide.append(c)
  except ValueError:pass
wide.sort()
# Category-role config, not material-category management. Existing secondary codes only.
roles=[]
for c in cats:
 if c['enabled'] and not c['deleted']:
  role='FINISHED_GOOD' if c['code'].startswith('09') else 'RAW_MATERIAL' if c['code'].startswith('08') else 'ASSEMBLY'
  roles.append({'categoryCode':c['code'],'role':role,'enabled':True})
write('category-roles.json',roles)
weeks=[]
for i in range(7):
 m=monthadd(MONTHS[0],i-1);last=calendar.monthrange(m.year,m.month)[1]
 for w,start in enumerate(range(1,last+1,7),1):
  weeks.append({'businessMonth':m.isoformat(),'weekCode':f'W{w}','startDate':m.replace(day=start).isoformat(),'endDate':m.replace(day=min(start+6,last)).isoformat(),'sortNo':w})
calendarrows=[]
d=monthadd(MONTHS[0],-1);end=monthadd(MONTHS[-1],1)
while d<end:
 for f in FACTORIES:
  work=d.weekday()<5
  calendarrows.append({'factoryCode':f,'calendarDate':str(d),'productionDay':work,'dailyCapacity':'10' if work else '0','capacityUnit':'台','allocationWeight':'1','reason':'UAT：周末休息；低产能用于验证超限提示'})
 d+=timedelta(days=1)
write('business-weeks.json',weeks);write('production-calendars.json',calendarrows)
write('factory-warehouse-mappings.json',[{'factoryCode':f,'factoryName':f,'warehouseCode':WH[f],'warehouseName':f+'测试仓','enabled':True} for f in FACTORIES])
books=[]
def book(name,headers,tips,rows):
 books.append({'name':name,'headers':headers,'tips':tips,'rows':rows})
FH=['工厂','形态','料号','模具','状态']+[m.strftime('%Y-%m') for m in MONTHS]+['fcst-total']
FT=['必填：爱培科或永惠','整机','真实已有料号；文本','可空','可空']+['整数台数']*6+['仅校验展示，不参与计算']
IH=['仓库编码*','仓库名称','库存分类*','料号*','品质*','供应商名称*','库存数量*']
IT=['与工厂映射一致','测试仓','整机/结构件/原材料','文本真实料号','良品/不良品','必填','数量；0表示已导入零库存']
configs={}
for key,roots,stopset in [('accuracy',small,set()),('all-leaf',full,set()),('load-frontier',wide,frontier)]:
 nodes=sorted({v for r in roots for v in traverse(r,stopset)})
 forecasts=[]
 for f in FACTORIES:
  for i,r in enumerate(roots):forecasts.append({'factoryCode':f,'materialCode':r,'quantities':[1001,1000,2,1,0,10001] if key=='accuracy' else [1001+i%7]*6})
 policies=[{'materialCode':c,'supplyMode':'SELF_MADE','explosionPolicy':'STOP' if c in stopset else 'EXPLODE'} for c in nodes]
 stock=[{'warehouseCode':WH[f],'materialCode':c,'safetyStockQty':'0','warningStockQty':'0'} for f in FACTORIES for c in nodes]
 inv=[]
 for f in FACTORIES:
  for c in nodes:inv.append({'warehouseCode':WH[f],'materialCode':c,'quality':'GOOD','quantity':'0'})
 # Every reachable node has an explicit inventory record, including zero.
 config={'key':key,'months':[str(m) for m in MONTHS],'roots':roots,'reachableNodes':nodes,'forecast':forecasts,'materialPolicies':policies,'stockPolicies':stock,'inventory':inv,'stopNodes':sorted(set(nodes)&stopset)}
 configs[key]=config;write(key+'.json',config)
 book('forecast-'+key,FH,FT,[[r['factoryCode'],'整机',r['materialCode'],'','UAT']+r['quantities']+[sum(r['quantities'])] for r in forecasts])
 book('inventory-'+key,IH,IT,[[r['warehouseCode'],'测试仓','结构库存',r['materialCode'],'良品','UAT供应商',r['quantity']] for r in inv])
 if key=='accuracy':
  mixed=json.loads(json.dumps(config));mixed['key']='accuracy-stock'
  for r in mixed['inventory']:r['quantity']='511' if r['materialCode']==roots[0] else '3'
  mixed['inventory'] += [{'warehouseCode':WH[f],'materialCode':r,'quality':'DEFECTIVE','quantity':'999999'} for f in FACTORIES for r in roots]
  for r in mixed['stockPolicies']:
   if r['materialCode']==roots[0]:r['safetyStockQty']='10';r['warningStockQty']='20'
  configs['accuracy-stock']=mixed;write('accuracy-stock.json',mixed)
  book('inventory-accuracy-stock',IH,IT,[[r['warehouseCode'],'测试仓','结构库存',r['materialCode'],'良品' if r['quality']=='GOOD' else '不良品','UAT供应商',r['quantity']] for r in mixed['inventory']])
book('config-factory',['工厂编码*','仓库编码*','仓库名称','启用'],['必填','唯一仓库','选填','启用/停用'],[[f,WH[f],f+'测试仓','启用'] for f in FACTORIES])
book('config-business-weeks',['业务月份*','周编码*','开始日期*','结束日期*'],['整月连续覆盖','月内唯一','yyyy-MM-dd','yyyy-MM-dd'],[[x['businessMonth'][:7],x['weekCode'],x['startDate'],x['endDate']] for x in weeks])
book('config-calendar',['工厂编码*','日期*','生产日*','日产能*','单位','备注'],['爱培科/永惠','yyyy-MM-dd','是/否','整机整数台/日','台','选填'],[[x['factoryCode'],x['calendarDate'],'是' if x['productionDay'] else '否',x['dailyCapacity'],'台',x['reason']] for x in calendarrows])
for key in ['accuracy','accuracy-stock','load-frontier']:
 cfg=configs[key]
 book('config-stock-'+key,['仓库编码*','物料料号*','安全库存*','预警库存*'],['映射仓','已有料号','硬保底','不阻塞排产'],[[r['warehouseCode'],r['materialCode'],r['safetyStockQty'],r['warningStockQty']] for r in cfg['stockPolicies']])
 book('config-material-'+key,['物料料号*','供应方式*','下钻策略*'],['已有料号','自制/外购/委外','继续下钻/停止下钻'],[[r['materialCode'],'自制','停止下钻' if r['explosionPolicy']=='STOP' else '继续下钻'] for r in cfg['materialPolicies']])
# Diagnostic fixtures retain both factories but assign each root to one factory.
# They bypass the known forecast unique-key defect; never replace the required original cases.
def unique_paths(root):
 paths=[];q=[(root,)]
 while q:
  path=q.pop();paths.append(path)
  for edge in adj[path[-1]]:q.append(path+(edge['child'],))
 return len(paths)==len(set(paths))
safe=sorted([r for r in full if unique_paths(r)],key=lambda r:(len(traverse(r)),r))[-5:][::-1]
duplicate_parents={c for c,es in adj.items() if len([e['child'] for e in es])!=len({e['child'] for e in es})}
for original,newkey in [('accuracy-stock','accuracy-distinct'),('load-frontier','load-distinct')]:
 cfg=json.loads(json.dumps(configs[original]));cfg['key']=newkey
 if newkey=='accuracy-distinct':
  cfg['roots']=safe
  cfg['forecast']=[dict(factoryCode=f,materialCode=r,quantities=[1001,1000,2,1,0,10001]) for f in FACTORIES for r in safe]
 else:
  cfg['stopNodes']=sorted(set(cfg['stopNodes'])|duplicate_parents)
  # Active-only diagnostic inputs; original fixtures preserve source status unchanged.
  cfg['roots']=[r for r in cfg['roots'] if all(materials[c]['active'] for c in traverse(r,set(cfg['stopNodes'])))]
  cfg['forecast']=[r for r in cfg['forecast'] if r['materialCode'] in cfg['roots']]
 assignment={r:FACTORIES[i%2] for i,r in enumerate(cfg['roots'])}
 cfg['forecast']=[r for r in cfg['forecast'] if r['factoryCode']==assignment[r['materialCode']]]
 nodes=sorted({v for r in cfg['roots'] for v in traverse(r,set(cfg['stopNodes']))});cfg['reachableNodes']=nodes
 cfg['stopNodes']=sorted(set(nodes)&set(cfg['stopNodes']))
 cfg['materialPolicies']=[dict(materialCode=c,supplyMode='SELF_MADE',explosionPolicy='STOP' if c in cfg['stopNodes'] else 'EXPLODE') for c in nodes]
 cfg['stockPolicies']=[dict(warehouseCode=WH[f],materialCode=c,safetyStockQty='10' if newkey=='accuracy-distinct' and c==safe[0] else '0',warningStockQty='20' if newkey=='accuracy-distinct' and c==safe[0] else '0') for f in FACTORIES for c in nodes]
 cfg['inventory']=[dict(warehouseCode=WH[f],materialCode=c,quality='GOOD',quantity=('511' if c==safe[0] else '3') if newkey=='accuracy-distinct' else '0') for f in FACTORIES for c in nodes]
 if newkey=='accuracy-distinct':cfg['inventory'] += [dict(warehouseCode=WH[f],materialCode=r,quality='DEFECTIVE',quantity='999999') for f in FACTORIES for r in safe]
 configs[newkey]=cfg;write(newkey+'.json',cfg)
 book('forecast-'+newkey,FH,FT,[[r['factoryCode'],'整机',r['materialCode'],'','UAT']+r['quantities']+[sum(r['quantities'])] for r in cfg['forecast']])
 book('inventory-'+newkey,IH,IT,[[r['warehouseCode'],'测试仓','结构库存',r['materialCode'],'良品' if r['quality']=='GOOD' else '不良品','UAT供应商',r['quantity']] for r in cfg['inventory']])
 book('config-stock-'+newkey,['仓库编码*','物料料号*','安全库存*','预警库存*'],['']*4,[[r['warehouseCode'],r['materialCode'],r['safetyStockQty'],r['warningStockQty']] for r in cfg['stockPolicies']])
 book('config-material-'+newkey,['物料料号*','供应方式*','下钻策略*'],['']*3,[[r['materialCode'],'自制','停止下钻' if r['explosionPolicy']=='STOP' else '继续下钻'] for r in cfg['materialPolicies']])
# Supplemental configs for full-leaf case (no STOP).
cfg=configs['all-leaf']
book('config-stock-all-leaf',['仓库编码*','物料料号*','安全库存*','预警库存*'],['']*4,[[r['warehouseCode'],r['materialCode'],r['safetyStockQty'],r['warningStockQty']] for r in cfg['stockPolicies']])
book('config-material-all-leaf',['物料料号*','供应方式*','下钻策略*'],['']*3,[[r['materialCode'],'自制','继续下钻'] for r in cfg['materialPolicies']])
# 10k/100k repeated physical import rows stress parser/import only; roots aggregate by factory/material/month.
for count in [10000,100000]:
 rows=[];src=books[0]['rows']
 p=D/f'forecast-import-{count}.csv'
 with p.open('w',encoding='utf-8-sig',newline='') as f:
  w=csv.writer(f);w.writerow(FH);w.writerow(FT)
  for i in range(count):
   row=list(src[i%len(src)]);row[5:11]=[1]*6;row[11]=6;w.writerow(row)
   if count==10000:rows.append(row)
 if count==10000:book('forecast-import-10000',FH,FT,rows)
# Intentionally invalid files. Unknown code must fail, not lead anyone to create a material.
bad=list(books[0]['rows'][0]);bad[2]='UAT_NOT_EXIST_DO_NOT_CREATE';bad[5]='not-a-number'
book('negative-forecast',FH,FT,[bad])
badcal=list(books[9]['rows'][0]) if False else ['爱培科','2026-11-02','是','83.5','台','应被拒绝：小数日产能']
book('negative-calendar',['工厂编码*','日期*','生产日*','日产能*','单位','备注'],['']*6,[badcal])
write('workbooks.json',books)
# Independent netting oracle. Shared stock consumed per factory, chronological month then input order, BFS children.
def oracle(cfg):
 used=collections.defaultdict(Decimal);inventory={(r['warehouseCode'],r['materialCode']):dec(r['quantity']) for r in cfg['inventory'] if r['quality']=='GOOD'}
 safety={(r['warehouseCode'],r['materialCode']):dec(r['safetyStockQty']) for r in cfg['stockPolicies']};stops=set(cfg['stopNodes']);details=[];demand=collections.defaultdict(Decimal)
 for f in FACTORIES:
  for mi,m in enumerate(MONTHS):
   for r in cfg['forecast']:
    if r['factoryCode']!=f or r['quantities'][mi]<=0:continue
    root=r['materialCode'];q=collections.deque([(root,dec(r['quantities'][mi]),(root,))])
    while q:
     c,gross,path=q.popleft();k=(WH[f],c);before=max(dec(0),inventory[k]-safety.get(k,dec(0))-used[k]);take=min(gross,before);net=gross-take;used[k]+=take
     details.append([f,root,str(m),'>'.join(path),c,s(gross),s(before),s(take),s(net)])
     if len(path)==1 and net>0:demand[(f,root,str(m),root,'MACHINE_OUTPUT')]+=net
     if net<=0:continue
     if c in stops or not adj[c]:demand[(f,root,str(m),c,'TERMINAL')]+=net;continue
     for edge in adj[c]:
      ratio=(dec(edge['cq'])/dec(edge['pq'])).quantize(dec('0.000000000000000001'),rounding=ROUND_HALF_UP)
      q.append((edge['child'],net*ratio,path+(edge['child'],)))
 # Group by source month/material/type then split three; this avoids per-path rounding errors.
 daily=collections.defaultdict(Decimal);weekly=collections.defaultdict(Decimal);monthly=collections.defaultdict(Decimal)
 for (f,root,m,c,typ),quantity in sorted(demand.items()):
  src=date.fromisoformat(m);prev=monthadd(src,-1)
  targets=[[w for w in weeks if w['businessMonth']==str(prev)][-1]]+[w for w in weeks if w['businessMonth']==m][:2]
  integral=quantity==quantity.to_integral_value();scale=dec(1) if typ=='MACHINE_OUTPUT' or integral else dec('0.000001')
  quantity=quantity.quantize(scale,rounding=ROUND_HALF_UP);base=(quantity/3/scale).to_integral_value(rounding=ROUND_FLOOR)*scale
  parts=[quantity-2*base,base,base]
  for w,part in zip(targets,parts):
   days=[];t=date.fromisoformat(w['startDate']);end=date.fromisoformat(w['endDate'])
   while t<=end:
    if t.weekday()<5:days.append(t)
    t+=timedelta(days=1)
   each=(part/len(days)/scale).to_integral_value(rounding=ROUND_FLOOR)*scale;extra=int((part-each*len(days))/scale)
   for i,t in enumerate(days):
    qty=each+(scale if i<extra else 0)
    if qty:
     daily[(f,root,m,c,typ,str(t))]+=qty
     weekly[(f,root,c,typ,w['businessMonth'][:7]+'-'+w['weekCode'])]+=qty
     monthly[(f,root,c,typ,t.strftime('%Y-%m'))]+=qty
 def csvout(name,header,rows):
  with gzip.open(E/(cfg['key']+'-'+name+'.csv.gz'),'wt',encoding='utf8',newline='') as file:
   w=csv.writer(file);w.writerow(header);w.writerows(rows)
 csvout('details',['factory','root','sourceMonth','path','material','gross','availableBefore','used','net'],details)
 csvout('demand',['factory','root','sourceMonth','material','type','quantity'],[list(k)+[s(v)] for k,v in sorted(demand.items())])
 for name,obj,head in [('daily',daily,['factory','root','sourceMonth','material','type','date','quantity']),('weekly',weekly,['factory','root','material','type','businessWeek','quantity']),('monthly',monthly,['factory','root','material','type','actualMonth','quantity'])]:csvout(name,head,[list(k)+[s(v)] for k,v in sorted(obj.items())])
 return {'rootDemands':sum(1 for r in cfg['forecast'] for q in r['quantities'] if q>0),'expandedDetails':len(details),'demandGroups':len(demand),'dailyRows':len(daily),'forecastFileRows':len(cfg['forecast']),'rootSkuCount':len(cfg['roots']),'reachableMaterials':len(cfg['reachableNodes']),'stopNodes':len(stops)}
stats={k:oracle(v) for k,v in configs.items()}
write('dataset-manifest.json',{'months':[m.strftime('%Y-%m') for m in MONTHS],'firstGridDate':'2026-10-29','lastGridDate':'2027-04-30','snapshotId':33,'datasets':stats,'workbooks':len(books),'rootExamples':small,'limitations':['100000 CSV is import IO stress, not 100000 unique roots; importer accepts xlsx only, convert with provided artifact tool exporter.', 'load-frontier deliberately STOPs before unclassified descendants; all-leaf and accuracy fixtures exercise full-leaf expansion on unchanged master data.']})
print(json.dumps(stats,ensure_ascii=False,indent=2))
