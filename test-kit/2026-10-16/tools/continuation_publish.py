"""Expendable publication fixture in isolated MySQL only; main batch 91 is never mutated."""
import argparse, ast, datetime, hashlib, json, pathlib, subprocess, time, urllib.request, psutil, threading

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/continuation-publish-runtime.json'
CONTAINER, DATABASE = 'mrp-testkit-mysql', 'mrp_testkit'
BASE = 'http://127.0.0.1:8180/api/v1'
SOURCE = 10

def sql(query):
    return subprocess.run(['docker','exec','-i',CONTAINER,'sh','-c',
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 --raw -u"$MYSQL_USER" -NB '+DATABASE],
        input=query,text=True,capture_output=True,check=True).stdout.splitlines()

def columns(table):
    return [line.split('\t')[0] for line in sql('SHOW COLUMNS FROM `'+table+'`;')]

def clone(table,where,replacements):
    cols=[c for c in columns(table) if c!='id']
    return 'INSERT INTO `'+table+'` ('+','.join('`'+c+'`' for c in cols)+') SELECT '+','.join(replacements.get(c,'`'+c+'`') for c in cols)+' FROM `'+table+'` WHERE '+where+';'

def digest(table,batch):
    cols=[c for c in columns(table) if c not in {'id','batch_id','run_id','created_at','updated_at'}]
    expressions=','.join("IFNULL(HEX(`"+c+"`),'NULL')" for c in cols)
    values=sql("SELECT CONCAT_WS('|',"+expressions+") v FROM `"+table+'` WHERE batch_id='+str(batch)+' ORDER BY v;')
    return {'rows':len(values),'sha256':hashlib.sha256('\n'.join(values).encode()).hexdigest()}

timings=[]
def request(path,body=None):
    headers={'Content-Type':'application/json'}
    if 'token' in globals():headers['Authorization']='Bearer '+token
    begin=time.monotonic()
    with urllib.request.urlopen(urllib.request.Request(BASE+path,headers=headers,data=None if body is None else json.dumps(body).encode()),timeout=240) as r:
        data=r.read();status=r.status
    result=json.loads(data);assert result['success'],path
    timings.append({'path':path,'status':status,'ms':round((time.monotonic()-begin)*1000),'bytes':len(data)})
    return result['data']

if __name__=='__main__':
    assert sql('SELECT status FROM mrp_calc_batch WHERE id=10;')==['PUBLISHED']
    parser=argparse.ArgumentParser();parser.add_argument('--working-batch',type=int);args=parser.parse_args()
    if args.working_batch:
        batch=args.working_batch
        state=sql('SELECT status,revision_of_batch_id,active_run_id FROM mrp_calc_batch WHERE id='+str(batch)+';')[0].split('\t')
        assert state[:2]==['READY','10']
        run=int(state[2])
    else:
        statements=['START TRANSACTION;', 'SET @sourceRun=(SELECT active_run_id FROM mrp_calc_batch WHERE id=10);',
            clone('mrp_calc_batch','id=10',{'status':"'READY'",'active_run_id':'NULL','published_at':'NULL',
                'active_request_key':'NULL','revision_of_batch_id':'10','superseded_by_batch_id':'NULL','result_checksum':'NULL'}),
            'SET @newBatch=LAST_INSERT_ID();',
            clone('mrp_calc_run','id=@sourceRun',{'batch_id':'@newBatch','status':"'ACTIVE'"}),
            'SET @newRun=LAST_INSERT_ID();','UPDATE mrp_calc_batch SET active_run_id=@newRun WHERE id=@newBatch;']
        for table in ['mrp_batch_factory_scope','mrp_batch_business_week_scope']:
            statements.append(clone(table,'batch_id=10',{'batch_id':'@newBatch'}))
        fact_tables=['mrp_requirement_detail','mrp_terminal_demand','mrp_daily_plan_detail']
        for table in fact_tables+['mrp_material_policy_notice']:
            statements.append(clone(table,'run_id=@sourceRun',{'batch_id':'@newBatch','run_id':'@newRun'}))
        statements+=['COMMIT;','SELECT @newBatch,@newRun;']
        batch,run=map(int,sql('\n'.join(statements))[-1].split('\t'))
    password=None
    for node in ast.walk(ast.parse(pathlib.Path('/tmp/mrp-daily-api-uat.py').read_text())):
        if isinstance(node,ast.Dict):
            for key,value in zip(node.keys,node.values):
                if isinstance(key,ast.Constant) and key.value=='password' and isinstance(value,ast.Constant):password=value.value
    assert password
    token=request('/auth/login',{'username':'admin','password':password})['token']
    fact_tables=['mrp_requirement_detail','mrp_terminal_demand','mrp_daily_plan_detail']
    before={table:digest(table,batch) for table in fact_tables}
    review=request(f'/mrp/batches/{batch}/capacity-review')
    samples=[];stop=threading.Event()
    def monitor():
        while not stop.is_set():
            for proc in psutil.process_iter(['pid','ppid','name']):
                try:
                    if proc.info['name']=='java' and proc.net_connections(kind='inet') and any(c.laddr.port==8180 and c.status=='LISTEN' for c in proc.net_connections(kind='inet')):
                        samples.append({'time':time.time(),'pid':proc.pid,'rss':proc.memory_info().rss,'cpuSeconds':sum(proc.cpu_times()[:2])})
                except (psutil.Error,PermissionError):pass
            stop.wait(.5)
    thread=threading.Thread(target=monitor,daemon=True);thread.start()
    try:published=request(f'/mrp/batches/{batch}/publish',{'capacityFingerprint':review['fingerprint']})
    finally:stop.set();thread.join(timeout=2)
    after={table:digest(table,published['id']) for table in fact_tables}
    assert before==after
    conservation=request(f"/mrp/batches/{published['id']}/plans/conservation")
    assert conservation['mismatchCount']==0
    result={'status':'PASS','fixtureSource':SOURCE,'workingBatch':batch,'run':run,'publishedBatch':published['id'],
        'publishedRun':published['activeRunId'],'before':before,'after':after,'conservation':conservation,
        'timings':timings,'peakRssBytes':max((s['rss'] for s in samples),default=0),'samples':samples,
        'finishedAt':datetime.datetime.now().astimezone().isoformat()}
    OUT.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k not in {'samples','before','after','conservation'}},ensure_ascii=False))
