"""Verify legacy distribution guards over real HTTP using expendable isolated fixtures."""
import datetime, json, urllib.parse
import continuation_access as http
from continuation_publish import sql, clone, digest, ROOT

results=[]
def check(batch,run,calendar,policy,want,label):
    query=urllib.parse.urlencode({'runId':run,'calendarVersionId':calendar,'materialPolicyVersionId':policy})
    status,body=http.req(f'/mrp/batches/{batch}/distribute?'+query,'POST')
    results.append({'case':label,'status':status,'expected':want})
    assert status==want,(label,status,want)
    return body

if __name__=='__main__':
    http.token=http.ok('/auth/login','POST',{'username':'admin','password':http.password()})['token']
    source=13
    original=digest('mrp_daily_plan_detail',source)
    batch=None
    try:
        statements=['START TRANSACTION;',f'SET @sourceRun=(SELECT active_run_id FROM mrp_calc_batch WHERE id={source});',
            clone('mrp_calc_batch',f'id={source}',{'status':"'WAITING_DAILY_DISTRIBUTION'",'active_run_id':'NULL','published_at':'NULL','active_request_key':'NULL','revision_of_batch_id':'NULL','superseded_by_batch_id':'NULL','result_checksum':'NULL'}),
            'SET @newBatch=LAST_INSERT_ID();',
            clone('mrp_calc_run','id=@sourceRun',{'batch_id':'@newBatch','status':"'COMPLETED'"}),
            'SET @newRun=LAST_INSERT_ID();','UPDATE mrp_calc_batch SET active_run_id=@newRun WHERE id=@newBatch;']
        for table in ['mrp_batch_factory_scope','mrp_batch_business_week_scope']:
            statements.append(clone(table,f'batch_id={source}',{'batch_id':'@newBatch'}))
        statements.append(clone('mrp_terminal_demand',"id=(SELECT selected_id FROM (SELECT MIN(id) selected_id FROM mrp_terminal_demand WHERE run_id=@sourceRun AND terminal_type='MACHINE_OUTPUT') x)",{'batch_id':'@newBatch','run_id':'@newRun','quantity':'5'}))
        statements+=['COMMIT;','SELECT @newBatch,@newRun;']
        batch,run=map(int,sql('\n'.join(statements))[-1].split('\t'))
        calendar,policy=map(int,sql(f'SELECT production_calendar_version_id,material_policy_version_id FROM mrp_calc_batch WHERE id={batch};')[0].split('\t'))
        check(batch,run,calendar+100000,policy,400,'foreign frozen calendar')
        check(batch,run,calendar,policy+100000,400,'foreign frozen policy')
        assert sql(f'SELECT COUNT(*) FROM mrp_daily_plan_detail WHERE batch_id={batch};')==['0']
        body=check(batch,run,calendar,policy,200,'first valid distribution')
        assert body['success'] and body['data']['details'] and not body['data']['unallocated']
        allocated=digest('mrp_daily_plan_detail',batch)
        assert allocated['rows']>0
        check(batch,run,calendar,policy,400,'repeat distribution')
        assert digest('mrp_daily_plan_detail',batch)==allocated
        sql(f"UPDATE mrp_calc_batch SET status='PUBLISHED' WHERE id={batch};")
        check(batch,run,calendar,policy,400,'published fixture immutable')
        assert digest('mrp_daily_plan_detail',batch)==allocated
        source_run,source_cal,source_policy=map(int,sql(f'SELECT active_run_id,production_calendar_version_id,material_policy_version_id FROM mrp_calc_batch WHERE id={source};')[0].split('\t'))
        check(source,source_run,source_cal,source_policy,400,'retained large published batch immutable')
        assert digest('mrp_daily_plan_detail',source)==original
        conservation=http.ok(f'/mrp/batches/{batch}/plans/conservation')
        assert conservation['mismatchCount']==0
        result={'status':'PASS','results':results,'fixtureBatch':batch,'fixtureRun':run,'allocated':allocated,'source13Before':original,'source13After':digest('mrp_daily_plan_detail',source),'conservationMismatchCount':0,'checkedAt':datetime.datetime.now().astimezone().isoformat()}
        (ROOT/'evidence/continuation-distribution-runtime.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
        print('PASS',len(results),'real HTTP distribution checks; conservation=0; retained source unchanged')
    finally:
        if batch:
            sql('\n'.join(f'DELETE FROM {table} WHERE batch_id={batch};' for table in ['mrp_daily_plan_detail','mrp_unallocated_quantity','mrp_terminal_demand','mrp_batch_factory_scope','mrp_batch_business_week_scope','mrp_decision_issue','mrp_calc_run'])+f'\nDELETE FROM mrp_calc_batch WHERE id={batch};')
