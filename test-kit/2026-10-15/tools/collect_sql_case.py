#!/usr/bin/env python3
"""Read-only persisted fact check on fixed isolated DB, before any manual adjustments.
Does not replace API pagination verification. Retains separate facts-sql.json.
"""
import pathlib,json,subprocess,sys,datetime
folder=pathlib.Path(sys.argv[1]).resolve()
m=json.loads((folder/'manifest.json').read_text())
if m.get('baseURL')!='http://127.0.0.1:8180' or type(m.get('batchId')) is not int:raise SystemExit('Invalid isolated manifest')
bid=m['batchId']
def sql(q):
 if not q.startswith('SELECT'):raise ValueError('Read-only only')
 return subprocess.run(['docker','exec','-i','mrp-testkit-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 --raw -u"$MYSQL_USER" -N -B mrp_testkit'],input=q,text=True,capture_output=True,check=True).stdout
b=json.loads(sql(f"SELECT JSON_OBJECT('status',status,'run',active_run_id) FROM mrp_calc_batch WHERE id={bid};"))
if b['status'] not in ['READY','PUBLISHED'] or type(b['run']) is not int:raise SystemExit('No ready active run')
if int(sql(f'SELECT COUNT(*) FROM mrp_daily_plan_override WHERE batch_id={bid};').strip()):raise SystemExit('Adjusted batch: this collector supports unadjusted initial facts only')
run=b['run']
q=f"""SELECT JSON_OBJECT('factoryCode',factory_code,'rootMaterialCode',root_material_code,
'terminalMaterialCode',terminal_material_code,'terminalType',terminal_type,'supplyMode',supply_mode,
'pathGroupKey',path_group_key,'sourceMonth',CAST(source_month AS CHAR),'sourceDate',CAST(source_date AS CHAR),
'scheduleDate',CAST(schedule_date AS CHAR),'systemQuantity',CAST(system_quantity AS CHAR),
'effectiveQuantity',CAST(system_quantity AS CHAR)) FROM mrp_daily_plan_detail WHERE run_id={run} AND batch_id={bid} ORDER BY id;"""
facts=[json.loads(line) for line in sql(q).splitlines()]
(folder/'facts-sql.json').write_text(json.dumps(facts,ensure_ascii=False,separators=(',',':')))
(folder/'sql-collection-result.json').write_text(json.dumps({'status':'COLLECTED_AWAITING_ORACLE','method':'SELECT isolated DB; zero overrides required; not HTTP coverage','batchId':bid,'runId':run,'rows':len(facts),'time':datetime.datetime.now().astimezone().isoformat()},indent=2)+'\n')
print('Read-only collected',len(facts),'persisted daily facts')
