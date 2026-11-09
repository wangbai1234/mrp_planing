"""Trigger a new invalid-input task; never mutate retained batch/run/task data."""
import argparse, datetime, json, time
from continuation_publish import sql, ROOT
p=argparse.ArgumentParser();p.add_argument('phase',choices=['before','after']);args=p.parse_args()
out=ROOT/f'evidence/worker-failure-{args.phase}-runtime.json'
assert not out.exists()
assert sql('SELECT COUNT(*) FROM mrp_calc_batch WHERE id=9223372036854775806;')==['0']
task=int(sql("INSERT INTO calc_task(task_type,business_scope,request_key,status,priority,attempt_count,max_attempts,input_payload,phase) VALUES('MRP_CALCULATION','mrp','failure-probe-"+str(time.time_ns())+"','PENDING',0,0,3,'{\"batchId\":9223372036854775806,\"bomSnapshotId\":1,\"inventorySnapshotId\":1}','QUEUED');SELECT LAST_INSERT_ID();")[0])
samples=[]
try:
    deadline=time.monotonic()+18
    while time.monotonic()<deadline:
        row=sql(f"SELECT status,version,attempt_count,IFNULL(error_code,''),IF(worker_id IS NULL,0,1) FROM calc_task WHERE id={task};")[0].split('\t')
        samples.append({'status':row[0],'version':int(row[1]),'attempts':int(row[2]),'error':row[3],'hasOwner':row[4]=='1'})
        if row[0]=='FAILED':break
        time.sleep(.5)
    expected='RUNNING' if args.phase=='before' else 'FAILED'
    assert samples[-1]['status']==expected,samples[-1]
    assert samples[-1]['version']==(1 if args.phase=='before' else 2)
    if args.phase=='after':assert samples[-1]['error']=='CALC_ERROR'
finally:
    sql(f'DELETE FROM calc_task WHERE id={task};')
    out.write_text(json.dumps({'status':'REPRODUCED' if args.phase=='before' else 'PASS','task':task,'samples':samples,'remaining':sql(f'SELECT COUNT(*) FROM calc_task WHERE id={task};'),'finishedAt':datetime.datetime.now().astimezone().isoformat()},indent=2)+'\n')
print(args.phase,samples[-1])
