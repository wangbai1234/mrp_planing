#!/usr/bin/env python3
"""B09: real Worker four/five business-week allocation and daily conservation."""
import datetime
import decimal
import hashlib
import json
import pathlib
import subprocess
import urllib.request

from current_source_snapshot import snapshot
from current_stock_policy_full_chain import request, data, await_task, sql

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-b09-weeks-' + datetime.datetime.now().strftime('%H%M%S'))
BASE = 'http://127.0.0.1:8182/api/v1'


def upload(path, token):
    payload = path.read_bytes()
    boundary = '----MRPB09Weeks'
    head = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="forecast-four-five-weeks.xlsx"'
            '\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode()
    req = urllib.request.Request(BASE + '/forecast-imports', data=head + payload + ('\r\n--' + boundary + '--\r\n').encode(),
                                 headers={'Authorization': 'Bearer ' + token,
                                          'Content-Type': 'multipart/form-data; boundary=' + boundary})
    with urllib.request.urlopen(req, timeout=60) as response:
        result = json.loads(response.read())
        assert response.status == 202 and result['success'] and result['data']['errorRows'] == 0, result
    return result['data']['taskId'], hashlib.sha256(payload).hexdigest()


def quantity(value):
    return decimal.Decimal(str(value))


def main():
    OUT.mkdir(parents=True)
    record = {'status': 'IN_PROGRESS', 'environment': '8182/3317/6391', 'beforeSource': snapshot()}
    token = None
    batch_id = None
    try:
        subprocess.run(['node', str(pathlib.Path(__file__).with_name('build_b07_shared_stock.mjs')), str(OUT)], check=True)
        http, login = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
        assert http == 200 and login['success'], login
        token = login['data']['token']
        task_id, sha = upload(OUT / 'forecast-four-five-weeks.xlsx', token)
        version = data(f'/forecast-imports/{task_id}/confirm', token, 'POST', b'')
        record.update(forecastTaskId=task_id, forecastVersionId=version['id'], forecastSha256=sha)
        batch = data(f'/mrp/batches?factoryCode=ALL&forecastVersionId={version["id"]}&inventorySnapshotId=66',
                     token, 'POST', b'')
        batch_id = batch['id']
        record['batchId'] = batch_id
        assert data(f'/mrp/batches/{batch_id}/precheck', token, 'POST', b'')['status'] == 'READY_TO_CALCULATE'
        record['taskId'] = data(f'/mrp/batches/{batch_id}/calculate', token, 'POST', b'')['id']
        record['task'] = await_task(batch_id, token)
        current = data(f'/mrp/batches/{batch_id}', token)
        assert current['status'] == 'SCHEDULE_READY', current
        run_id = current['activeRunId']
        record['runId'] = run_id
        rows = sql(f'SELECT DATE_FORMAT(d.source_month,"%Y-%m"),DATE_FORMAT(s.business_month,"%Y-%m"),'
                   f'w.week_code,SUM(d.system_quantity) FROM mrp_daily_plan_detail d '
                   f'JOIN mrp_batch_business_week_scope s ON s.batch_id=d.batch_id '
                   f'JOIN business_week_detail w ON w.version_id=s.business_week_version_id '
                   f'AND d.schedule_date BETWEEN w.start_date AND w.end_date '
                   f'WHERE d.batch_id={batch_id} AND d.run_id={run_id} AND d.terminal_type="MACHINE_OUTPUT" '
                   f'AND d.factory_code="爱培科" GROUP BY d.source_month,s.business_month,w.week_code '
                   f'ORDER BY d.source_month,s.business_month,w.sort_no;')
        record['weeklyFacts'] = rows
        observed = {(p[0], p[1], p[2]): quantity(p[3]) for row in rows if (p := row.split('\t'))}
        expected = {('2026-11', '2026-10', 'W5'): 203,
                    **{('2026-11', '2026-11', f'W{i}'): 200 for i in range(1, 5)},
                    ('2027-02', '2027-01', 'W5'): 253,
                    **{('2027-02', '2027-02', f'W{i}'): 250 for i in range(1, 4)}}
        assert observed == expected, (observed, expected)
        monthly = sql(f'SELECT source_month,SUM(system_quantity),MIN(schedule_date),MAX(schedule_date) '
                      f'FROM mrp_daily_plan_detail WHERE batch_id={batch_id} AND run_id={run_id} '
                      f'AND terminal_type="MACHINE_OUTPUT" AND factory_code="爱培科" GROUP BY source_month ORDER BY source_month;')
        record['monthlyFacts'] = monthly
        assert [quantity(row.split('\t')[1]) for row in monthly] == [1003, 1003], monthly
        conservation = data(f'/mrp/batches/{batch_id}/plans/conservation', token)
        record['conservation'] = conservation
        assert conservation['mismatchCount'] == 0, conservation
        record['status'] = 'PASS'
    except Exception as error:
        record.update(status='FAIL', error=str(error))
        raise
    finally:
        if batch_id is not None:
            record['finalBatchStatus'] = data(f'/mrp/batches/{batch_id}/cancel', token, 'POST', b'')['status']
        record['afterSource'] = snapshot()
        record['protectedSourcesUnchanged'] = record['beforeSource']['tables'] == record['afterSource']['tables']
        if not record['protectedSourcesUnchanged'] or batch_id is not None and record.get('finalBatchStatus') != 'CANCELLED':
            record['status'] = 'FAIL'
        (OUT / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'status': record['status'], 'evidence': str(OUT), 'batchId': batch_id}, ensure_ascii=False))
    assert record['status'] == 'PASS', record


if __name__ == '__main__':
    main()
