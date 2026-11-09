#!/usr/bin/env python3
"""B12 isolated linked transfer preview, concurrency, calendar and lock guards."""
import concurrent.futures
import datetime
import decimal
import json
import pathlib

from current_source_snapshot import snapshot
from current_stock_policy_full_chain import request, data, await_task, sql

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'evidence/runs' / ('2026-11-03-b12-linked-transfer-' + datetime.datetime.now().strftime('%H%M%S'))
FORECAST_ID = 50
STOCK_ID = 66


def main():
    OUT.mkdir(parents=True)
    record = {'status': 'IN_PROGRESS', 'environment': '8182/3317/6391', 'beforeSource': snapshot(),
              'forecastVersionId': FORECAST_ID, 'inventorySnapshotId': STOCK_ID}
    batch_id = None
    token = None
    try:
        http, login = request('/auth/login', 'POST', {'username': 'admin', 'password': 'admin123'})
        assert http == 200 and login['success'], login
        token = login['data']['token']
        batch = data(f'/mrp/batches?factoryCode=ALL&forecastVersionId={FORECAST_ID}&inventorySnapshotId={STOCK_ID}',
                     token, 'POST', b'')
        batch_id = batch['id']
        record['batchId'] = batch_id
        assert data(f'/mrp/batches/{batch_id}/precheck', token, 'POST', b'')['status'] == 'READY_TO_CALCULATE'
        record['taskId'] = data(f'/mrp/batches/{batch_id}/calculate', token, 'POST', b'')['id']
        record['task'] = await_task(batch_id, token)
        batch = data(f'/mrp/batches/{batch_id}', token)
        assert batch['status'] == 'SCHEDULE_READY', batch
        run_id = batch['activeRunId']
        record['runId'] = run_id
        facts = sql(f'SELECT factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,'
                    f'path_group_key,schedule_date,system_quantity FROM mrp_daily_plan_detail WHERE batch_id={batch_id} '
                    f'AND run_id={run_id} AND terminal_type="MACHINE_OUTPUT" AND factory_code="爱培科" '
                    'AND root_material_code="6839AA000362" AND system_quantity>0 ORDER BY schedule_date;')
        assert len(facts) >= 2, facts
        parsed = [row.split('\t') for row in facts]
        source = parsed[0]
        target = parsed[1]
        row_key = '|'.join(source[:6])
        assert source[:6] == target[:6], (source, target)
        source_date, target_date = source[6], target[6]
        record.update(sourceFact=source, targetFact=target, transferRowKey=row_key)
        request_body = {'rowKey': row_key, 'sourceDate': source_date, 'targetDate': target_date,
                        'quantity': '1', 'expectedRunId': run_id}
        before_overrides = sql(f'SELECT COUNT(*) FROM mrp_daily_plan_override WHERE batch_id={batch_id};')
        preview = data(f'/mrp/batches/{batch_id}/linked-transfers/preview', token, 'POST', request_body)
        record['preview'] = preview
        assert preview['lines'] and preview['fingerprint'], preview
        after_preview = sql(f'SELECT COUNT(*) FROM mrp_daily_plan_override WHERE batch_id={batch_id};')
        assert before_overrides == after_preview == ['0'], (before_overrides, after_preview)
        record['previewDidNotWrite'] = True
        rest_date = datetime.date.fromisoformat(source_date)
        while rest_date.weekday() != 6:
            rest_date += datetime.timedelta(days=1)
        rest_body = dict(request_body, targetDate=rest_date.isoformat())
        rest_http, rest_result = request(f'/mrp/batches/{batch_id}/linked-transfers/preview', 'POST', rest_body, token)
        record['restDayRejection'] = {'http': rest_http, 'result': rest_result}
        assert rest_http == 400 and not rest_result['success'], rest_result
        commit = dict(request_body, fingerprint=preview['fingerprint'], reason='B12并发调日验收',
                      lines=[{'rowKey': line['rowKey'], 'quantity': line['suggestedQuantity']}
                             for line in preview['lines']])
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            futures = [executor.submit(request, f'/mrp/batches/{batch_id}/linked-transfers', 'POST', commit, token)
                       for _ in range(2)]
            responses = [future.result(timeout=60) for future in futures]
        record['concurrentCommits'] = responses
        assert sorted(status for status, _ in responses) == [200, 409], responses
        overrides = sql(f'SELECT schedule_date,manual_quantity FROM mrp_daily_plan_override WHERE batch_id={batch_id} '
                        f'AND root_material_code="6839AA000362" AND terminal_type="MACHINE_OUTPUT" ORDER BY schedule_date;')
        record['overrides'] = overrides
        assert len(overrides) == 2, overrides
        assert {row.split('\t')[0]: row.split('\t')[1] for row in overrides} == {
            source_date: str(decimal.Decimal(source[7]) - 1),
            target_date: str(decimal.Decimal(target[7]) + 1)}, overrides
        conservation = data(f'/mrp/batches/{batch_id}/plans/conservation', token)
        record['conservation'] = conservation
        assert conservation['mismatchCount'] == 0, conservation
        data(f'/mrp/batches/{batch_id}/execution/lock?scheduleDate={source_date}', token, 'POST', b'')
        record['lockedDate'] = source_date
        locked_http, locked_result = request(f'/mrp/batches/{batch_id}/linked-transfers/preview', 'POST',
                                             request_body, token)
        record['lockedRejection'] = {'http': locked_http, 'result': locked_result}
        assert locked_http == 400 and not locked_result['success'], locked_result
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
