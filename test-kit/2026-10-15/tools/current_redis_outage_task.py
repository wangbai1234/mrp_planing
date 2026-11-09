#!/usr/bin/env python3
"""B17: isolated Redis outage while MySQL-backed calculation is running."""
import datetime
import json
import pathlib
import subprocess
import time

from current_source_snapshot import snapshot
from current_stock_policy_full_chain import request, data, sql

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-b17-redis-' + datetime.datetime.now().strftime('%H%M%S'))
REDIS = 'mrp-testkit-redis'


def container(action):
    subprocess.run(['docker', action, REDIS], check=True, capture_output=True, text=True, timeout=30)


def redis_up():
    return subprocess.run(['docker', 'exec', REDIS, 'redis-cli', 'PING'], capture_output=True, text=True, timeout=4).stdout.strip() == 'PONG'


def main():
    OUT.mkdir(parents=True)
    record = {'status': 'IN_PROGRESS', 'environment': '8182/3317/6391', 'beforeSource': snapshot()}
    batch_id = task_id = None
    stopped = False
    try:
        assert redis_up()
        status, body = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
        assert status == 200 and body['success']
        token = body['data']['token']
        batch = data('/mrp/batches?factoryCode=ALL&forecastVersionId=38&inventorySnapshotId=46', token, 'POST', b'')
        batch_id = batch['id']
        record['batchId'] = batch_id
        assert data(f'/mrp/batches/{batch_id}/precheck', token, 'POST', b'')['status'] == 'READY_TO_CALCULATE'
        task_id = data(f'/mrp/batches/{batch_id}/calculate', token, 'POST', b'')['id']
        record['taskId'] = task_id
        for _ in range(200):
            row = sql(f'SELECT status,phase,attempt_count FROM calc_task WHERE id={task_id};')[0].split('	')
            if row[0] == 'RUNNING':
                break
            assert row[0] == 'PENDING', row
            time.sleep(.1)
        assert row[0] == 'RUNNING', row
        record['beforeOutage'] = row
        container('stop')
        stopped = True
        assert not redis_up()
        record['duringOutage'] = sql(f'SELECT status,phase,attempt_count FROM calc_task WHERE id={task_id};')[0].split('	')
        time.sleep(4)
        container('start')
        stopped = False
        for _ in range(100):
            row = sql(f'SELECT status,phase,attempt_count FROM calc_task WHERE id={task_id};')[0].split('	')
            if row[0] in ('SUCCEEDED', 'FAILED'):
                break
            time.sleep(1)
        record['afterRecovery'] = row
        record['batchStatus'] = sql(f'SELECT status,active_run_id FROM mrp_calc_batch WHERE id={batch_id};')
        record['runCount'] = sql(f'SELECT COUNT(*) FROM mrp_calc_run WHERE batch_id={batch_id};')[0]
        assert row[0] == 'SUCCEEDED' and record['batchStatus'][0].startswith('SCHEDULE_READY	') and record['runCount'] == '1', record
        assert redis_up()
        record['status'] = 'PASS'
    except Exception as error:
        record.update(status='FAIL', error=repr(error))
    finally:
        if stopped:
            try:
                container('start')
            except Exception as error:
                record.setdefault('cleanupErrors', []).append(f'redis: {error!r}')
        if batch_id:
            try:
                status, body = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
                assert status == 200
                token = body['data']['token']
                record['cancelled'] = data(f'/mrp/batches/{batch_id}/cancel', token, 'POST', b'')['status']
            except Exception as error:
                record.setdefault('cleanupErrors', []).append(f'batch: {error!r}')
        record['redisRestored'] = redis_up()
        record['afterSource'] = snapshot()
        record['protectedSourcesUnchanged'] = record['beforeSource']['tables'] == record['afterSource']['tables']
        if record.get('cleanupErrors') or not record['redisRestored'] or not record['protectedSourcesUnchanged'] or batch_id and record.get('cancelled') != 'CANCELLED':
            record['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'status': record['status'], 'evidence': str(OUT), 'error': record.get('error'), 'cleanupErrors': record.get('cleanupErrors')}, ensure_ascii=False))
    assert record['status'] == 'PASS', record.get('error') or record.get('cleanupErrors')


if __name__ == '__main__':
    main()
