"""Exact current Mapper SQL vs retained legacy projection, isolated fixture only."""
import sys,json,statistics,time,hashlib
sys.path.insert(0,'test-kit/2026-10-16/tools')
from override_page_benchmark import ROOT,ORDER,renderer,sql
from continuation_publish import digest
meta=json.loads((ROOT/'evidence/dense-override-fixture.json').read_text())
output=ROOT/'evidence/dense-override-final-sql-ab.json';assert not output.exists()
p=dict(batchId=meta['batch'],runId=meta['run'],rolling=True,hasOverrides=True,startDate='2026-09-01',endDate='2027-03-31',factoryCode=None,rootMaterialCode=None,terminalMaterialCode=None,terminalType=None,offset=0,pageSize=100,virtualCount=None)
r=renderer(p);virtual=int(sql(r('countVirtualDetails'))[0]);total=int(sql(r('countDetails'))[0]);results=[]
assert virtual==2000
for offset in [0,total//2,total-100]:
 p.update(offset=offset,virtualCount=virtual,storedOffset=max(0,offset-virtual),mergeOffset=min(offset,virtual),windowSize=min(offset,virtual)+100)
 queries={'original':'SELECT * FROM ('+r('effectiveFacts')+') effective ORDER BY '+ORDER+f' LIMIT {offset},100','window':r('selectDetails')}
 assert 'WEIGHT_STRING' in queries['window'] and 'STRAIGHT_JOIN' in queries['window']
 rows={name:sql(query) for name,query in queries.items()};assert rows['original']==rows['window'],offset
 entry=dict(offset=offset,virtual=virtual,sha256=hashlib.sha256('\n'.join(rows['original']).encode()).hexdigest(),sql=queries,explain={name:sql('EXPLAIN ANALYZE '+query) for name,query in queries.items()},samples={name:[] for name in queries})
 for cycle in range(5):
  for name in (list(queries) if cycle%2==0 else list(reversed(queries))):
   begin=time.monotonic();data=sql(queries[name]);elapsed=round((time.monotonic()-begin)*1000);assert data==rows['original'];entry['samples'][name].append(elapsed);print(offset,cycle,name,elapsed,flush=True)
 entry['medianMs']={name:statistics.median(samples) for name,samples in entry['samples'].items()};results.append(entry)
assert digest('mrp_daily_plan_detail',meta['source'])==meta['sourceFacts']
output.write_text(json.dumps(dict(status='PASS',fixture=meta,total=total,results=results),indent=2)+'\n')
