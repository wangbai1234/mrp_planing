"""Disposable >page-sized virtual override evidence; never changes retained batches."""
import argparse, hashlib, json, statistics, time
from urllib.parse import urlencode
import continuation_access as api
import override_page_benchmark as fixture
from continuation_publish import ROOT, sql, digest

META = ROOT / 'evidence/dense-override-fixture.json'
fixture.META = META
parser = argparse.ArgumentParser()
parser.add_argument('action', choices=['create', 'before', 'after', 'cleanup'])
args = parser.parse_args()
if args.action == 'create':
    fixture.create()
    meta = json.loads(META.read_text()); batch, run = meta['batch'], meta['run']
    identity = 'd.factory_code,d.root_material_code,d.terminal_material_code,d.terminal_type,d.supply_mode,d.path_group_key'
    fields = 'batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity,revision,reason'
    # First 30 groups already have a virtual target. Add independent groups, with a
    # target before their earliest stored day; do not invent cross-month group keys.
    sql(f"""INSERT INTO mrp_daily_plan_override({fields})
        SELECT {batch},{identity},DATE_SUB(MIN(d.schedule_date),INTERVAL 1 DAY),1.000001,3,'disposable dense fixture'
        FROM mrp_daily_plan_detail d WHERE d.run_id={run} AND d.terminal_type!='MACHINE_OUTPUT'
        AND NOT EXISTS(SELECT 1 FROM mrp_daily_plan_override o WHERE o.batch_id={batch}
          AND o.factory_code=d.factory_code AND o.root_material_code=d.root_material_code
          AND o.terminal_material_code=d.terminal_material_code AND o.terminal_type=d.terminal_type
          AND o.supply_mode=d.supply_mode AND o.path_group_key=d.path_group_key)
        GROUP BY {identity} ORDER BY MIN(d.id) LIMIT 1970;""")
    assert digest('mrp_daily_plan_detail', 13) == meta['sourceFacts']
    print('Dense fixture ready', batch, run)
elif args.action == 'cleanup':
    meta = json.loads(META.read_text()); batch = meta['batch']; assert batch not in [10,13]
    assert sql(f'SELECT revision_of_batch_id FROM mrp_calc_batch WHERE id={batch}') == ['13']
    tables = ['mrp_daily_plan_override','mrp_daily_plan_detail','mrp_batch_factory_scope','mrp_batch_business_week_scope','mrp_calc_run']
    sql('START TRANSACTION;\n'+'\n'.join(f'DELETE FROM {t} WHERE batch_id={batch};' for t in tables)+f'\nDELETE FROM mrp_calc_batch WHERE id={batch};\nCOMMIT;')
    remaining = {t:int(sql(f'SELECT COUNT(*) FROM {t} WHERE batch_id={batch};')[0]) for t in tables}
    remaining['mrp_calc_batch'] = int(sql(f'SELECT COUNT(*) FROM mrp_calc_batch WHERE id={batch};')[0])
    unchanged = digest('mrp_daily_plan_detail',13) == meta['sourceFacts']
    assert unchanged and not any(remaining.values())
    out = ROOT/'evidence/dense-override-cleanup.json'; assert not out.exists()
    out.write_text(json.dumps(dict(status='PASS',remaining=remaining,sourceUnchanged=unchanged),indent=2)+'\n')
    print('Cleanup PASS', remaining)
else:
    meta = json.loads(META.read_text()); batch = meta['batch']; assert batch not in [10,13]
    out = ROOT/f'evidence/dense-override-{args.action}-runtime.json'; assert not out.exists()
    common = dict(startDate='2026-09-01',endDate='2027-03-31',pageSize=100)
    params = dict(batchId=batch,runId=meta['run'],hasOverrides=True,rolling=True,
        factoryCode=None,rootMaterialCode=None,terminalMaterialCode=None,terminalType=None,
        offset=0,**common)
    render = fixture.renderer(params)
    virtual = int(sql(render('countVirtualDetails'))[0]); assert virtual == 2000
    api.token = api.ok('/auth/login','POST',{'username':'admin','password':api.password()})['token']
    endpoint = f'/mrp/batches/{batch}/rolling-details?'
    first = api.ok(endpoint+urlencode(common)); assert first['total'] > 90000
    sample = first['items'][0]
    selectors = [dict(common,page=1),dict(common,page=first['total']//200+1),
        dict(common,page=(first['total']+99)//100),dict(common,terminalType='MACHINE_OUTPUT'),
        dict(common,factoryCode=sample['factoryCode'],rootMaterialCode=sample['rootMaterialCode'],
             terminalMaterialCode=sample['terminalMaterialCode'],startDate=sample['scheduleDate'],endDate=sample['scheduleDate']),
        dict(common,factoryCode='TEST_NONEXISTENT')]
    results = []
    for selector in selectors:
        previous = None; samples = []
        for _ in range(5):
            start = time.monotonic(); data = api.ok(endpoint+urlencode(selector)); samples.append(round((time.monotonic()-start)*1000))
            assert previous is None or previous == data; previous = data
        results.append(dict(selector=selector,samplesMs=samples,medianMs=statistics.median(samples),response=previous,
            sha256=hashlib.sha256(json.dumps(previous,sort_keys=True).encode()).hexdigest()))
        print(args.action, selector, samples, flush=True)
    if args.action == 'after':
        before = json.loads((ROOT/'evidence/dense-override-before-runtime.json').read_text())
        assert before['fixture'] == meta
        for old,new in zip(before['results'],results,strict=True):
            assert old['selector'] == new['selector'] and old['response'] == new['response']
            new['fullResponseUnchanged'] = True
    assert digest('mrp_daily_plan_detail',13) == meta['sourceFacts']
    out.write_text(json.dumps(dict(status='PASS',fixture=meta,virtualCount=virtual,results=results),ensure_ascii=False,indent=2)+'\n')
    print(args.action,'PASS')
