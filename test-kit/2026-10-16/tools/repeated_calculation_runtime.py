"""Three independent async calculations with frozen large inputs, isolated MySQL only."""
import argparse, datetime, decimal, hashlib, json, re, subprocess, threading, time, urllib.request
import psutil
import continuation_access as api
from continuation_publish import sql, columns, clone, digest, ROOT
parser=argparse.ArgumentParser()
parser.add_argument('--evidence-name',default='repeated-calculation')
args=parser.parse_args()
assert re.fullmatch(r'[a-z0-9-]+',args.evidence_name)
SOURCE=13;BASE='http://127.0.0.1:8180/api/v1';OUT=ROOT/f'evidence/{args.evidence_name}-runtime.json'
FACTS=['mrp_requirement_detail','mrp_terminal_demand','mrp_daily_plan_detail']
assert not OUT.exists()
assert sql('SELECT status FROM mrp_calc_batch WHERE id=13;')==['PUBLISHED']
assert sql('SELECT COUNT(*) FROM mrp_decision_issue WHERE batch_id=13;')==['0']
assert sql('SELECT COUNT(*) FROM mrp_inventory_pool p JOIN mrp_inventory_allocation a ON a.pool_id=p.id WHERE p.batch_id=13;')==['0']

def guard():
    result={}
    for batch in [10,13]:
        for table in FACTS+['mrp_calc_batch','mrp_calc_run','mrp_batch_factory_scope','mrp_batch_business_week_scope']:
            where='id='+str(batch) if table=='mrp_calc_batch' else 'batch_id='+str(batch)
            rows=sql(f'SELECT * FROM {table} WHERE {where} ORDER BY id;')
            result[f'{batch}:{table}']={'rows':len(rows),'sha256':hashlib.sha256('\n'.join(rows).encode()).hexdigest()}
    return result

timings=[]
def request(path,body=None):
    headers={'Content-Type':'application/json'}
    if 'token' in globals():headers['Authorization']='Bearer '+token
    begin=time.monotonic()
    with urllib.request.urlopen(urllib.request.Request(BASE+path,headers=headers,data=None if body is None else json.dumps(body).encode()),timeout=180) as response:
        content=response.read();status=response.status
    result=json.loads(content,parse_float=decimal.Decimal);assert result['success'],(path,status)
    timings.append({'path':path,'status':status,'ms':round((time.monotonic()-begin)*1000),'bytes':len(content)})
    return result['data']

def invariants(batch):
    mismatches=int(sql(f'SELECT COUNT(*) FROM mrp_requirement_detail WHERE batch_id={batch} AND (gross_qty<>inventory_used_qty+net_qty OR inventory_after_qty<>inventory_before_qty-inventory_used_qty OR inventory_used_qty<0 OR net_qty<0 OR inventory_after_qty<0);')[0])
    assert mismatches==0,mismatches
    noninteger=int(sql(f"SELECT COUNT(*) FROM mrp_daily_plan_detail WHERE batch_id={batch} AND terminal_type='MACHINE_OUTPUT' AND system_quantity<>FLOOR(system_quantity);")[0]);assert noninteger==0
    c=request(f'/mrp/batches/{batch}/plans/conservation');assert c['mismatchCount']==0,c
    summaries={}
    for view,op in [('MACHINE_OUTPUT','='),('TERMINAL','<>')]:
        total=decimal.Decimal(sql(f"SELECT COALESCE(SUM(system_quantity),0) FROM mrp_daily_plan_detail WHERE batch_id={batch} AND terminal_type{op}'MACHINE_OUTPUT';")[0])
        totals={}
        for period in ['weekly','monthly']:
            rows=request(f'/mrp/batches/{batch}/plans/{period}?view={view}')
            value=sum((decimal.Decimal(str(row['totalQuantity'])) for row in rows),decimal.Decimal(0))
            assert value==total,(view,period,value,total)
            totals[period]={'rows':len(rows),'total':str(value)}
        summaries[view]={'dailyTotal':str(total),**totals}
    return {'requirementMismatches':mismatches,'fractionalMachineFacts':noninteger,'conservation':c,'summaries':summaries}

protected=guard();baseline={t:digest(t,SOURCE) for t in FACTS};batches=[];tasks=[];rounds=[];samples=[]
pid=int(subprocess.run(['lsof','-t','-iTCP:8180','-sTCP:LISTEN'],text=True,capture_output=True,check=True).stdout.strip())
proc=psutil.Process(pid);stop=threading.Event()
def monitor():
    while not stop.is_set():
        samples.append({'seconds':time.monotonic(),'rss':proc.memory_info().rss,'cpuSeconds':sum(proc.cpu_times()[:2])})
        stop.wait(.5)
thread=threading.Thread(target=monitor,daemon=True);thread.start()
result={'status':'RUNNING','sourceBatch':SOURCE,'sourceBaseline':baseline,'rounds':rounds,'timings':timings,'serverPid':pid}
try:
    token=request('/auth/login',{'username':'admin','password':api.password()})['token']
    for index in range(3):
        q=['START TRANSACTION;',clone('mrp_calc_batch','id=13',{'status':"'READY_TO_CALCULATE'",'active_run_id':'NULL','published_at':'NULL','active_request_key':'NULL','revision_of_batch_id':'13','superseded_by_batch_id':'NULL','result_checksum':'NULL','version':'0'}),'SET @newBatch=LAST_INSERT_ID();']
        for table in ['mrp_batch_factory_scope','mrp_batch_business_week_scope']:
            q.append(clone(table,'batch_id=13',{'batch_id':'@newBatch'}))
        q+=['COMMIT;','SELECT @newBatch;']
        batch=int(sql('\n'.join(q))[-1]);assert batch not in [10,13];batches.append(batch)
        started=time.monotonic();task=request(f'/mrp/batches/{batch}/calculate',{});taskId=task['id'];tasks.append(taskId)
        deadline=time.monotonic()+600
        while True:
            state=sql(f'SELECT status,IFNULL(active_run_id,0) FROM mrp_calc_batch WHERE id={batch};')[0].split('\t')
            taskState=sql(f"SELECT status,version,IFNULL(error_code,''),IFNULL(result_resource_id,0) FROM calc_task WHERE id={taskId};")[0].split('\t')
            if taskState[0] in ['SUCCEEDED','FAILED']:break
            assert time.monotonic()<deadline,('timeout',batch,taskId,state,taskState)
            time.sleep(1)
        elapsed=round((time.monotonic()-started)*1000)
        assert taskState[0]=='SUCCEEDED' and state[0]=='READY',(batch,taskId,state,taskState)
        assert state[1]==taskState[3]
        actual={t:digest(t,batch) for t in FACTS}
        entry={'round':index+1,'batch':batch,'task':taskId,'run':int(state[1]),'elapsedMs':elapsed,'facts':actual,'sameAsSource':actual==baseline}
        rounds.append(entry)
        assert actual==baseline,entry
        entry['invariants']=invariants(batch)
        print('round',index+1,'PASS',elapsed,'ms',actual['mrp_daily_plan_detail']['rows'],'daily facts',flush=True)
    result['status']='PASS'
except Exception as error:
    result['status']='FAIL';result['failure']=str(error);raise
finally:
    stop.set();thread.join(timeout=2)
    # Only delete newly created fixtures after their worker has reached a terminal state.
    cleanup=[]
    for batch,taskId in zip(batches,tasks):
        taskStatus=sql(f'SELECT status FROM calc_task WHERE id={taskId};')
        if taskStatus and taskStatus[0] not in ['SUCCEEDED','FAILED','CANCELLED']:
            cleanup.append({'batch':batch,'retainedReason':'worker still active'});continue
        statements=['START TRANSACTION;']
        cleanupTables=['mrp_stock_risk','mrp_material_policy_notice','mrp_daily_plan_override','mrp_daily_plan_detail','mrp_terminal_demand','mrp_requirement_detail','mrp_unallocated_quantity','mrp_decision_issue','mrp_batch_factory_scope','mrp_batch_business_week_scope','mrp_calc_run']
        for table in cleanupTables:
            statements.append(f'DELETE FROM {table} WHERE batch_id={batch};')
        statements += [f'DELETE FROM calc_task WHERE id={taskId};',f'DELETE FROM mrp_calc_batch WHERE id={batch};','COMMIT;']
        sql('\n'.join(statements))
        remaining={table:int(sql(f'SELECT COUNT(*) FROM {table} WHERE batch_id={batch};')[0]) for table in cleanupTables}
        remaining['calc_task']=int(sql(f'SELECT COUNT(*) FROM calc_task WHERE id={taskId};')[0])
        remaining['mrp_calc_batch']=int(sql(f'SELECT COUNT(*) FROM mrp_calc_batch WHERE id={batch};')[0])
        cleanup.append({'batch':batch,'remaining':remaining['mrp_calc_batch'],'remainingByTable':remaining})
        if any(remaining.values()):result['status']='FAIL'
    after=guard();result.update(cleanup=cleanup,sourceGuardBefore=protected,sourceGuardAfter=after,sourceUnchanged=protected==after,peakRssBytes=max((s['rss'] for s in samples),default=0),cpuSeconds=round(samples[-1]['cpuSeconds']-samples[0]['cpuSeconds'],2) if samples else 0,samples=samples,finishedAt=datetime.datetime.now().astimezone().isoformat())
    if protected!=after:result['status']='FAIL'
    OUT.write_text(json.dumps(result,ensure_ascii=False,indent=2,default=str)+'\n')
assert result['status']=='PASS'
print('PASS three rounds; source unchanged:',result['sourceUnchanged'],'peak RSS:',result['peakRssBytes'])
