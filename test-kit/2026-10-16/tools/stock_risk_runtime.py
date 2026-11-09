"""One expendable large async calculation; independently verify every stored risk via HTTP."""
import collections
import datetime
import hashlib
import json
import time

import continuation_access as api
from continuation_publish import ROOT, clone, digest, sql

OUT = ROOT / 'evidence/stock-risk-runtime.json'
assert not OUT.exists()
SOURCE = 13
FACTS = ['mrp_requirement_detail', 'mrp_terminal_demand', 'mrp_daily_plan_detail']
TABLES = ['mrp_stock_risk', 'mrp_material_policy_notice', 'mrp_daily_plan_override',
          *FACTS, 'mrp_unallocated_quantity', 'mrp_decision_issue',
          'mrp_batch_factory_scope', 'mrp_batch_business_week_scope', 'mrp_calc_run']


def guard():
    result = {}
    for batch in [10, 13]:
        for table in FACTS + ['mrp_calc_batch', 'mrp_calc_run', 'mrp_batch_factory_scope', 'mrp_batch_business_week_scope']:
            where = f'id={batch}' if table == 'mrp_calc_batch' else f'batch_id={batch}'
            rows = sql(f'SELECT * FROM {table} WHERE {where} ORDER BY id;')
            result[f'{batch}:{table}'] = {'rows': len(rows), 'sha256': hashlib.sha256('\n'.join(rows).encode()).hexdigest()}
    return result


assert sql('SELECT status FROM mrp_calc_batch WHERE id=13;') == ['PUBLISHED']
assert sql('SELECT COUNT(*) FROM mrp_inventory_pool WHERE batch_id=13;') == ['0']
before = guard()
baseline = {table: digest(table, SOURCE) for table in FACTS}
result = {'status': 'RUNNING', 'sourceGuardBefore': before, 'baseline': baseline}
batch = task_id = None
try:
    api.token = api.ok('/auth/login', 'POST', {'username': 'admin', 'password': api.password()})['token']
    statements = ['START TRANSACTION;', clone('mrp_calc_batch', 'id=13', {
        'status': "'READY_TO_CALCULATE'", 'active_run_id': 'NULL', 'published_at': 'NULL',
        'active_request_key': 'NULL', 'revision_of_batch_id': '13', 'superseded_by_batch_id': 'NULL',
        'result_checksum': 'NULL', 'version': '0'}), 'SET @riskBatch=LAST_INSERT_ID();']
    for table in ['mrp_batch_factory_scope', 'mrp_batch_business_week_scope']:
        statements.append(clone(table, 'batch_id=13', {'batch_id': '@riskBatch'}))
    statements += ['COMMIT;', 'SELECT @riskBatch;']
    batch = int(sql('\n'.join(statements))[-1])
    assert batch not in [10, 13]
    start = time.monotonic()
    task_id = api.ok(f'/mrp/batches/{batch}/calculate', 'POST', {})['id']
    result.update(batch=batch, task=task_id)
    deadline = time.monotonic() + 600
    while True:
        task = sql(f'SELECT status,IFNULL(result_resource_id,0) FROM calc_task WHERE id={task_id};')[0].split('\t')
        if task[0] in ['SUCCEEDED', 'FAILED', 'CANCELLED']:
            break
        assert time.monotonic() < deadline, 'Calculation timeout'
        time.sleep(1)
    assert task[0] == 'SUCCEEDED', task
    run = int(task[1])
    assert sql(f'SELECT status,active_run_id FROM mrp_calc_batch WHERE id={batch};') == [f'READY\t{run}']
    result.update(run=run, elapsedMs=round((time.monotonic() - start) * 1000))
    actual_facts = {table: digest(table, batch) for table in FACTS}
    assert actual_facts == baseline
    result['actualFacts'] = actual_facts

    # No pools are present in this copied input. Every factory/material must therefore
    # produce exactly one missing-policy notice, with roots in first-seen input order.
    assert sql(f'SELECT COUNT(*) FROM mrp_inventory_pool WHERE batch_id={batch};') == ['0']
    roots = collections.OrderedDict()
    for row in sql(f"SELECT JSON_OBJECT('factory',factory_code,'material',current_material_code,'root',root_material_code) FROM mrp_requirement_detail WHERE batch_id={batch} ORDER BY id;"):
        detail = json.loads(row)
        key = (detail['factory'], detail['material'])
        roots.setdefault(key, dict())[detail['root']] = None
    expected = [{'riskType': 'STOCK_POLICY_MISSING', 'materialCode': material,
                 'warehouseCode': None, 'availableQty': None, 'usedQty': None, 'remainingQty': None,
                 'safetyStockQty': None, 'warningStockQty': None,
                 'rootMaterialCodes': ','.join(ordered)[:1000]} for (_, material), ordered in roots.items()]
    start = time.monotonic()
    risks = api.ok(f'/mrp/inventory/stock-risks/{batch}?runId={run}')
    result['riskApiMs'] = round((time.monotonic() - start) * 1000)
    fields = list(expected[0])
    actual = []
    for risk in risks:
        assert risk['batchId'] == batch and risk['runId'] == run
        actual.append({field: risk[field] for field in fields})
    serialize = lambda rows: sorted(json.dumps(row, ensure_ascii=False, sort_keys=True) for row in rows)
    assert serialize(actual) == serialize(expected), 'Risk fields or sources mismatch'
    assert len(actual) > 1000, 'Fixture must cross the SQL chunk boundary'
    result.update(riskCount=len(actual), expectedRiskCount=len(expected),
                  riskSha256=hashlib.sha256('\n'.join(serialize(actual)).encode()).hexdigest(),
                  riskFieldsExact=True)
    mismatches = sql(f'SELECT COUNT(*) FROM mrp_requirement_detail WHERE batch_id={batch} AND (gross_qty<>inventory_used_qty+net_qty OR inventory_after_qty<>inventory_before_qty-inventory_used_qty OR inventory_used_qty<0 OR net_qty<0 OR inventory_after_qty<0);')
    assert mismatches == ['0']
    conservation = api.ok(f'/mrp/batches/{batch}/plans/conservation')
    assert conservation['mismatchCount'] == 0
    result.update(conservation=conservation, requirementMismatches=0, status='PASS')
except Exception as error:
    result.update(status='FAIL', error=str(error))
    raise
finally:
    if batch is not None:
        terminal = sql(f'SELECT status FROM calc_task WHERE id={task_id};') if task_id else []
        if not task_id or terminal and terminal[0] in ['SUCCEEDED', 'FAILED', 'CANCELLED']:
            statements = ['START TRANSACTION;'] + [f'DELETE FROM {table} WHERE batch_id={batch};' for table in TABLES]
            if task_id:
                statements.append(f'DELETE FROM calc_task WHERE id={task_id};')
            statements += [f'DELETE FROM mrp_calc_batch WHERE id={batch};', 'COMMIT;']
            sql('\n'.join(statements))
            remaining = {table: int(sql(f'SELECT COUNT(*) FROM {table} WHERE batch_id={batch};')[0]) for table in TABLES}
            remaining['mrp_calc_batch'] = int(sql(f'SELECT COUNT(*) FROM mrp_calc_batch WHERE id={batch};')[0])
            remaining['calc_task'] = int(sql(f'SELECT COUNT(*) FROM calc_task WHERE id={task_id};')[0]) if task_id else 0
            result['cleanup'] = remaining
            if any(remaining.values()):
                result['status'] = 'FAIL'
        else:
            result.update(status='FAIL', retainedReason='Worker not terminal; fixture retained')
    after = guard()
    result.update(sourceGuardAfter=after, sourceUnchanged=before == after,
                  finishedAt=datetime.datetime.now().astimezone().isoformat())
    if before != after:
        result['status'] = 'FAIL'
    OUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
assert result['status'] == 'PASS'
print('PASS', result['batch'], result['elapsedMs'], 'ms', result['riskCount'], 'risks; sources unchanged; cleanup complete')
