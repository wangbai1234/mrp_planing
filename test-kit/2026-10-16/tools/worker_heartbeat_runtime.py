"""Hold only a disposable isolated batch row to observe the real Worker's lease scheduler."""
import argparse
import datetime
import json
import selectors
import subprocess
import time
import uuid

import continuation_access as api
from continuation_publish import ROOT, clone, digest, sql


def task_state(task):
    return json.loads(sql(f"SELECT JSON_OBJECT('status',status,'worker',worker_id,'version',version,"
                          f"'attempts',attempt_count,'heartbeat',CAST(heartbeat_at AS CHAR),"
                          f"'lease',CAST(lease_until AS CHAR),'error',error_code) FROM calc_task WHERE id={task};")[0])


def protected():
    return {'batches': sql('SELECT id,status,active_run_id,version FROM mrp_calc_batch WHERE id IN (10,13) ORDER BY id;'),
            'facts13': digest('mrp_daily_plan_detail', 13)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('phase', choices=['before', 'after'])
    args = parser.parse_args()
    out = ROOT / f'evidence/worker-heartbeat-{args.phase}-runtime.json'
    assert not out.exists(), 'Evidence must not be overwritten'
    before = protected()
    batch = task = locker = None
    result = {'phase': args.phase, 'status': 'FAIL', 'protectedBefore': before, 'samples': []}
    api.token = api.ok('/auth/login', 'POST', {'username': 'admin', 'password': api.password()})['token']
    try:
        batch = int(sql(clone('mrp_calc_batch', 'id=13', {
            'factory_code': "'永惠'", 'status': "'CALCULATING'", 'active_run_id': 'NULL',
            'active_request_key': 'NULL', 'published_at': 'NULL', 'result_checksum': 'NULL',
            'revision_of_batch_id': '13', 'superseded_by_batch_id': 'NULL'}) + 'SELECT LAST_INSERT_ID();')[0])
        result['fixtureBatch'] = batch
        # Only this newly created row is locked; calculation blocks when writing batch status.
        locker = subprocess.Popen(['docker', 'exec', '-i', 'mrp-testkit-mysql', 'sh', '-c',
            'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 --raw --unbuffered -u"$MYSQL_USER" -NB mrp_testkit'],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        locker.stdin.write(f"START TRANSACTION; SELECT id FROM mrp_calc_batch WHERE id={batch} FOR UPDATE; SELECT 'LOCK_ACQUIRED';\n")
        locker.stdin.flush()
        selector = selectors.DefaultSelector()
        selector.register(locker.stdout, selectors.EVENT_READ)
        assert selector.select(8), 'Lock session did not respond'
        assert locker.stdout.readline().strip() == str(batch)
        assert locker.stdout.readline().strip() == 'LOCK_ACQUIRED'
        selector.close()
        key = 'heartbeat-runtime-' + uuid.uuid4().hex
        payload = json.dumps({'batchId': batch, 'bomSnapshotId': 9223372036854775806,
                              'inventorySnapshotId': 1, 'rootDemands': []})
        task = int(sql("INSERT INTO calc_task(task_type,business_scope,request_key,status,priority,input_payload,max_attempts) "
                       f"VALUES('MRP_CALCULATION','mrp','{key}','PENDING',-100,'{payload}',3); SELECT LAST_INSERT_ID();")[0])
        result['fixtureTask'] = task
        deadline = time.monotonic() + 9
        while time.monotonic() < deadline:
            state = task_state(task)
            if state['status'] == 'RUNNING': break
            time.sleep(.2)
        assert state['status'] == 'RUNNING', 'Worker did not claim task'
        baseline = state
        result['samples'].append({'elapsed': 0, **state})
        batch_http = api.ok(f'/mrp/batches/{batch}')
        assert batch_http['status'] == 'CALCULATING'
        result['runningHttp'] = {'status': 200, 'batchStatus': batch_http['status']}
        start = time.monotonic()
        while time.monotonic() - start < 34:
            time.sleep(.5)
            state = task_state(task)
            result['samples'].append({'elapsed': round(time.monotonic() - start, 3), **state})
            assert state['status'] == 'RUNNING'
            assert (state['worker'], state['version'], state['attempts']) == (baseline['worker'], 1, 1)
        advanced = state['lease'] > baseline['lease']
        result['leaseAdvancedDuringBlockedCalculation'] = advanced
        assert advanced == (args.phase == 'after'), 'Unexpected scheduler lease behavior'
        locker.stdin.write('COMMIT;\n'); locker.stdin.flush(); locker.stdin.close()
        assert locker.wait(timeout=8) == 0
        locker = None
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            state = task_state(task)
            batch_http = api.ok(f'/mrp/batches/{batch}')
            if state['status'] == 'FAILED' and batch_http['status'] == 'READY_TO_CALCULATE': break
            time.sleep(.2)
        assert state['status'] == 'FAILED' and state['version'] == 2
        assert state['error'] == 'CALC_ERROR'
        assert batch_http['status'] == 'READY_TO_CALCULATE' and batch_http['activeRunId'] is None
        result['terminal'] = state
        result['terminalHttp'] = {'status': 200, 'batchStatus': batch_http['status'], 'activeRunId': None}
        result['status'] = 'PASS'
    finally:
        if locker is not None:
            locker.stdin.write('ROLLBACK;\n'); locker.stdin.flush(); locker.stdin.close()
            locker.wait(timeout=10)
        if task:
            deadline = time.monotonic() + 10
            while task_state(task)['status'] == 'RUNNING' and time.monotonic() < deadline: time.sleep(.2)
            assert task_state(task)['status'] != 'RUNNING', 'Do not delete executing task'
            sql(f'DELETE FROM calc_task WHERE id={task};')
        if batch:
            assert batch not in (10, 13, 91)
            sql(f'DELETE FROM mrp_calc_run WHERE batch_id={batch}; DELETE FROM mrp_calc_batch WHERE id={batch};')
            result['cleanup'] = {table: int(sql(f'SELECT COUNT(*) FROM {table} WHERE ' +
                (f'id={batch}' if table == 'mrp_calc_batch' else f'id={task}' if table == 'calc_task' else f'batch_id={batch}') + ';')[0])
                for table in ['calc_task', 'mrp_calc_batch', 'mrp_calc_run', 'mrp_daily_plan_detail', 'mrp_terminal_demand', 'mrp_requirement_detail']}
            assert all(count == 0 for count in result['cleanup'].values())
        result['protectedAfter'] = protected()
        assert before == result['protectedAfter']
        result['finishedAt'] = datetime.datetime.now().astimezone().isoformat()
        with out.open('x') as f: json.dump(result, f, ensure_ascii=False, indent=2)
    print(json.dumps({k: v for k, v in result.items() if k not in {'samples', 'protectedBefore', 'protectedAfter'}}, ensure_ascii=False))
