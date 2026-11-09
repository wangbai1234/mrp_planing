import sys, json, statistics, time, hashlib
sys.path.insert(0,'test-kit/2026-10-16/tools')
from override_page_benchmark import META, ROOT, ORDER, renderer, candidate, sql
META=ROOT/'evidence/dense-override-fixture.json'
meta=json.loads(META.read_text()); output=ROOT/'evidence/dense-override-rank-sql-ab.json'; assert not output.exists()
p=dict(batchId=meta['batch'],runId=meta['run'],rolling=True,hasOverrides=True,startDate='2026-09-01',endDate='2027-03-31',factoryCode=None,rootMaterialCode=None,terminalMaterialCode=None,terminalType=None,offset=0,pageSize=100)
r=renderer(p); virtual=int(sql('SELECT COUNT(*) '+r('virtualFactSource'))[0]); total=int(sql(r('countDetails'))[0]); results=[]
keys=','.join('d.'+key for key in ORDER.split(',') if key!='schedule_date')
for offset in [0,total//2,total-100]:
 p['offset']=offset
 base=candidate(p,r)
 old='SELECT d.id detail_id,o.id override_id,0 is_virtual,d.schedule_date,'+keys+r('storedFactSource')+' UNION ALL SELECT d.id detail_id,o.id override_id,1 is_virtual,o.schedule_date,'+keys+r('virtualFactSource')
 stored='SELECT d.id detail_id,o.id override_id,0 is_virtual,d.schedule_date,'+keys+r('storedFactSource').replace('mrp_daily_plan_detail d LEFT JOIN','mrp_daily_plan_detail d FORCE INDEX (idx_mdpd_run_detail_order) LEFT JOIN')
 stored += ' ORDER BY '+','.join('d.'+key for key in ORDER.split(','))+f' LIMIT {max(0,offset-virtual)},{min(offset,virtual)+100}'
 new=f'SELECT * FROM ({stored}) stored_window UNION ALL SELECT d.id detail_id,o.id override_id,1 is_virtual,o.schedule_date,'+keys+r('virtualFactSource')
 opt=base.replace(old,new).replace(f'LIMIT {offset},100) p',f'LIMIT {min(offset,virtual)},100) p')
 opt=opt.replace('FROM (SELECT * FROM (',f'FROM (SELECT ids.*,ROW_NUMBER() OVER (ORDER BY {ORDER}) page_position FROM (')
 opt=opt.replace(f') ids ORDER BY {ORDER} LIMIT {min(offset,virtual)},100) p',') ids) p')
 opt=opt.replace('JOIN mrp_daily_plan_detail d ON d.id=p.detail_id','STRAIGHT_JOIN mrp_daily_plan_detail d ON d.id=p.detail_id')
 final_order='ORDER BY '+','.join('p.'+key for key in ORDER.split(','))
 opt=opt.replace(final_order,f'WHERE p.page_position>{min(offset,virtual)} AND p.page_position<={min(offset,virtual)+100} ORDER BY p.page_position')
 assert 'ROW_NUMBER()' in opt and 'LIMIT 2000,100) p' not in opt

 queries={'original':'SELECT * FROM ('+r('effectiveFacts')+') effective ORDER BY '+ORDER+f' LIMIT {offset},100','window':opt}
 rows={name:sql(q) for name,q in queries.items()}; assert rows['original']==rows['window'],offset
 entry=dict(offset=offset,virtual=virtual,sha256=hashlib.sha256('\n'.join(rows['original']).encode()).hexdigest(),explain={n:sql('EXPLAIN ANALYZE '+q) for n,q in queries.items()},samples={n:[] for n in queries})
 for cycle in range(5):
  for name in (list(queries) if cycle%2==0 else list(reversed(queries))):
   begin=time.monotonic(); data=sql(queries[name]); elapsed=round((time.monotonic()-begin)*1000); assert data==rows['original']; entry['samples'][name].append(elapsed); print(offset,cycle,name,elapsed,flush=True)
 entry['medianMs']={n:statistics.median(samples) for n,samples in entry['samples'].items()}; results.append(entry)
output.write_text(json.dumps(dict(status='PASS',fixture=meta,total=total,results=results),indent=2)+'\n')
