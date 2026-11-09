"""Disposable isolated tasks must never demote an already valid batch result on failure."""
import argparse
import datetime
import json
import time
import uuid

import continuation_access as api
from continuation_publish import ROOT, clone, digest, sql


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('phase', choices=['before', 'after'])
    args = parser.parse_args()
    out = ROOT / f'evidence/worker-batch-state-{args.phase}-runtime.json'
    assert not out.exists()
    guard = sql('SELECT id,status,active_run_id,version FROM mrp_calc_batch WHERE id IN (10,13) ORDER BY id;')
    facts = digest('mrp_daily_plan_detail', 13)
    api.token = api.ok('/auth/login', 'POST', {'username': 'admin', 'password': api.password()})['token']
    result = {'status': 'FAIL', 'phase': args.phase, 'cases': []}
    try:
        cases = [('PUBLISHED', False), ('READY', True)] if args.phase == 'before' else [
            ('PUBLISHED', False), ('PUBLISHED', True), ('READY', False), ('READY', True),
            ('WAITING_DECISION', False), ('STALE', True), ('CALCULATING', False), ('CALCULATING', True)]
        for initial, same_run in cases:
            batch = task = None
            case = {'initialStatus': initial, 'payloadHasActiveRun': same_run}
            result['cases'].append(case)
            try:
                statements = ['START TRANSACTION;', clone('mrp_calc_batch', 'id=13', {
                    'factory_code': "'永惠'", 'status': f"'{initial}'", 'active_run_id': 'NULL',
                    'active_request_key': 'NULL', 'revision_of_batch_id': '13',
                    'superseded_by_batch_id': 'NULL'}), 'SET @newBatch=LAST_INSERT_ID();',
                    clone('mrp_calc_run', 'id=(SELECT active_run_id FROM mrp_calc_batch WHERE id=13)',
                          {'batch_id': '@newBatch'}), 'SET @newRun=LAST_INSERT_ID();',
                    'UPDATE mrp_calc_batch SET active_run_id=@newRun WHERE id=@newBatch;',
                    'COMMIT;', 'SELECT @newBatch,@newRun;']
                batch, run = map(int, sql('\n'.join(statements))[-1].split('\t'))
                case.update(batch=batch, run=run)
                if args.phase == 'after':
                    # A real active daily row must survive both cleanup branches, not just its run header.
                    sql(clone('mrp_daily_plan_detail',
                              'id=(SELECT id FROM mrp_daily_plan_detail WHERE batch_id=13 ORDER BY id LIMIT 1)',
                              {'batch_id': str(batch), 'run_id': str(run)}))
                    assert int(sql(f'SELECT COUNT(*) FROM mrp_daily_plan_detail WHERE batch_id={batch};')[0]) == 1
                daily_before = sql(f'SELECT * FROM mrp_daily_plan_detail WHERE batch_id={batch} ORDER BY id;')
                original = sql(f'SELECT * FROM mrp_calc_batch WHERE id={batch};')
                header = sql(f'SELECT * FROM mrp_calc_run WHERE id={run};')
                original_http = api.ok(f'/mrp/batches/{batch}')
                assert original_http['status'] == initial
                payload = {'batchId': batch, 'bomSnapshotId': 9223372036854775806, 'inventorySnapshotId': 1}
                if same_run: payload['runId'] = run
                key = 'batch-state-runtime-' + uuid.uuid4().hex
                task = int(sql("INSERT INTO calc_task(task_type,business_scope,request_key,status,priority,input_payload,max_attempts) "
                               f"VALUES('MRP_CALCULATION','mrp','{key}','PENDING',-100,'{json.dumps(payload)}',3); SELECT LAST_INSERT_ID();")[0])
                case['task'] = task
                deadline = time.monotonic() + 12
                while time.monotonic() < deadline:
                    state = sql(f'SELECT status,version,error_code FROM calc_task WHERE id={task};')[0].split('\t')
                    current = api.ok(f'/mrp/batches/{batch}')
                    if state[0] == 'FAILED' and (args.phase == 'after' or current['status'] == 'READY_TO_CALCULATE'): break
                    time.sleep(.2)
                assert state == ['FAILED', '2', 'CALC_ERROR']
                # Allow the separate cleanup operation to finish; observe stability after one poll interval.
                time.sleep(1)
                current = api.ok(f'/mrp/batches/{batch}')
                case.update(taskState=state, finalStatus=current['status'], activeRunId=current['activeRunId'], httpStatus=200)
                assert current['activeRunId'] == run
                assert header == sql(f'SELECT * FROM mrp_calc_run WHERE id={run};')
                assert daily_before == sql(f'SELECT * FROM mrp_daily_plan_detail WHERE batch_id={batch} ORDER BY id;')
                case['activeDailyRowsUnchanged'] = True
                if args.phase == 'before': assert current['status'] == 'READY_TO_CALCULATE'
                elif initial == 'CALCULATING':
                    assert current['status'] == 'READY_TO_CALCULATE'
                    assert current['version'] == original_http['version'] + 1
                else:
                    assert current['status'] == initial
                    assert original == sql(f'SELECT * FROM mrp_calc_batch WHERE id={batch};')
            finally:
                if task:
                    assert sql(f'SELECT status FROM calc_task WHERE id={task};') != ['RUNNING']
                    sql(f'DELETE FROM calc_task WHERE id={task};')
                if batch:
                    assert batch not in (10, 13, 91)
                    sql(f'DELETE FROM mrp_daily_plan_detail WHERE batch_id={batch}; '
                        f'DELETE FROM mrp_calc_run WHERE batch_id={batch}; DELETE FROM mrp_calc_batch WHERE id={batch};')
                    case['cleanup'] = {table: int(sql(f'SELECT COUNT(*) FROM {table} WHERE ' +
                        (f'id={batch}' if table == 'mrp_calc_batch' else f'id={task}' if table == 'calc_task' else f'batch_id={batch}') + ';')[0])
                        for table in ['calc_task', 'mrp_calc_batch', 'mrp_calc_run', 'mrp_requirement_detail', 'mrp_terminal_demand', 'mrp_daily_plan_detail']}
                    assert not any(case['cleanup'].values())
        result['status'] = 'PASS'
    finally:
        result['protectedBatchesUnchanged'] = guard == sql('SELECT id,status,active_run_id,version FROM mrp_calc_batch WHERE id IN (10,13) ORDER BY id;')
        result['sourceFactsUnchanged'] = facts == digest('mrp_daily_plan_detail', 13)
        assert result['protectedBatchesUnchanged'] and result['sourceFactsUnchanged']
        result['finishedAt'] = datetime.datetime.now().astimezone().isoformat()
        with out.open('x') as f: json.dump(result, f, ensure_ascii=False, indent=2)
    print(json.dumps(result, ensure_ascii=False))
