#!/usr/bin/env python3
"""B17: kill the isolated JVM during RUNNING, then resume its expired lease."""
import datetime
import json
import os
import pathlib
import signal
import subprocess
import time

from current_source_snapshot import snapshot
from current_stock_policy_full_chain import request, data, sql

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-b17-process-' + datetime.datetime.now().strftime('%H%M%S'))


def login():
    status, body = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
    assert status == 200 and body['success'], (status, body)
    return body['data']['token']


def listener_pid():
    result = subprocess.run(['lsof', '-nP', '-tiTCP:8182', '-sTCP:LISTEN'], capture_output=True, text=True, check=True)
    pids = [int(value) for value in result.stdout.split()]
    assert len(pids) == 1, pids
    command = subprocess.check_output(['ps', '-p', str(pids[0]), '-o', 'command='], text=True)
    assert 'backend/target/classes' in command and 'java' in command, command[:150]
    return pids[0]


def start_api():
    env = dict(os.environ, MRP_TESTKIT_API_PORT='8182')
    subprocess.run(['python3', str(pathlib.Path(__file__).with_name('start_backend.py'))], env=env, check=True, capture_output=True, text=True)
    for _ in range(90):
        try:
            if subprocess.run(['curl', '-fsS', 'http://127.0.0.1:8182/actuator/health'], capture_output=True, timeout=2).returncode == 0:
                return
        except subprocess.TimeoutExpired:
            pass
        time.sleep(1)
    raise AssertionError('isolated API failed to restart')


def main():
    OUT.mkdir(parents=True)
    result = {'status': 'IN_PROGRESS', 'environment': '8182/3317/6391', 'beforeSource': snapshot()}
    batch_id = task_id = None
    stopped = False
    token = login()
    try:
        batch = data('/mrp/batches?factoryCode=ALL&forecastVersionId=38&inventorySnapshotId=46', token, 'POST', b'')
        batch_id = batch['id']
        result['batchId'] = batch_id
        assert data(f'/mrp/batches/{batch_id}/precheck', token, 'POST', b'')['status'] == 'READY_TO_CALCULATE'
        task_id = data(f'/mrp/batches/{batch_id}/calculate', token, 'POST', b'')['id']
        result['taskId'] = task_id
        for _ in range(200):
            state = sql(f'SELECT status,attempt_count,phase FROM calc_task WHERE id={task_id};')[0].split('	')
            if state[0] == 'RUNNING':
                break
            assert state[0] == 'PENDING', state
            time.sleep(.1)
        assert state[0] == 'RUNNING', state
        result['beforeKill'] = state
        pid = listener_pid()
        result['killedIsolatedPid'] = pid
        os.kill(pid, signal.SIGSTOP)
        result['whileStopped'] = sql(f'SELECT status,attempt_count,phase FROM calc_task WHERE id={task_id};')[0].split('	')
        os.kill(pid, signal.SIGKILL)
        stopped = True
        time.sleep(1)
        assert subprocess.run(['curl', '-sS', 'http://127.0.0.1:8182/actuator/health'], capture_output=True, timeout=3).returncode != 0
        result['afterKill'] = sql(f'SELECT status,attempt_count,phase FROM calc_task WHERE id={task_id};')[0].split('	')
        assert result['afterKill'][0] == 'RUNNING'
        start_api()
        stopped = False
        token = login()
        # Advance only this isolated task's expired clock; no other task or input is modified.
        sql(f"UPDATE calc_task SET lease_until=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id={task_id} AND status='RUNNING';")
        for _ in range(120):
            state = data(f'/mrp/batches/{batch_id}/calculation-task', token)
            if state and state['status'] in ('SUCCEEDED', 'FAILED'):
                break
            time.sleep(1)
        result['afterRecoveryTask'] = state
        result['afterRecoveryBatch'] = data(f'/mrp/batches/{batch_id}', token)
        result['taskRow'] = sql(f'SELECT status,attempt_count,error_code FROM calc_task WHERE id={task_id};')
        result['runs'] = sql(f'SELECT id,status FROM mrp_calc_run WHERE batch_id={batch_id} ORDER BY id;')
        assert state['status'] == 'SUCCEEDED' and result['afterRecoveryBatch']['status'] == 'SCHEDULE_READY', result
        assert int(result['taskRow'][0].split('	')[1]) == 2, result['taskRow']
        assert result['afterRecoveryBatch']['activeRunId'] and len(result['runs']) == 1, result['runs']
        result['status'] = 'PASS'
    except Exception as error:
        result.update(status='FAIL', error=repr(error))
    finally:
        if stopped:
            try:
                start_api()
                token = login()
            except Exception as error:
                result.setdefault('cleanupErrors', []).append(f'restart: {error!r}')
        if batch_id:
            try:
                result['cancelled'] = data(f'/mrp/batches/{batch_id}/cancel', token, 'POST', b'')['status']
            except Exception as error:
                result.setdefault('cleanupErrors', []).append(f'cancel: {error!r}')
        result['afterSource'] = snapshot()
        result['protectedSourcesUnchanged'] = result['beforeSource']['tables'] == result['afterSource']['tables']
        if result.get('cleanupErrors') or not result['protectedSourcesUnchanged'] or batch_id and result.get('cancelled') != 'CANCELLED':
            result['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'status': result['status'], 'evidence': str(OUT), 'error': result.get('error'), 'cleanupErrors': result.get('cleanupErrors')}, ensure_ascii=False))
    assert result['status'] == 'PASS', result.get('error') or result.get('cleanupErrors')


if __name__ == '__main__':
    main()
