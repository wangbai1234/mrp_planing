"""Reproduce orphan-fact conservation using expendable isolated MySQL fixtures only."""
import datetime, json, sys
import continuation_access as http
from continuation_publish import sql, clone, digest, ROOT

if __name__ == '__main__':
    phase = sys.argv[1]
    assert phase in {'before', 'after'}
    http.token = http.ok('/auth/login', 'POST', {'username': 'admin', 'password': http.password()})['token']
    source = 13
    original = digest('mrp_daily_plan_detail', source)
    batch = None
    try:
        statements = ['START TRANSACTION;', f'SET @sourceRun=(SELECT active_run_id FROM mrp_calc_batch WHERE id={source});',
            clone('mrp_calc_batch', f'id={source}', {'status': "'READY'", 'active_run_id': 'NULL',
                'published_at': 'NULL', 'active_request_key': 'NULL', 'revision_of_batch_id': 'NULL',
                'superseded_by_batch_id': 'NULL', 'result_checksum': 'NULL'}),
            'SET @newBatch=LAST_INSERT_ID();',
            clone('mrp_calc_run', 'id=@sourceRun', {'batch_id': '@newBatch', 'status': "'ACTIVE'"}),
            'SET @newRun=LAST_INSERT_ID();', 'UPDATE mrp_calc_batch SET active_run_id=@newRun WHERE id=@newBatch;']
        for table in ['mrp_batch_factory_scope', 'mrp_batch_business_week_scope']:
            statements.append(clone(table, f'batch_id={source}', {'batch_id': '@newBatch'}))
        # Copy one real daily row and its entire source-month demand group, with exact equal totals.
        statements += ['SET @sourceFact=(SELECT MIN(id) FROM mrp_daily_plan_detail WHERE run_id=@sourceRun);',
            clone('mrp_daily_plan_detail', 'id=@sourceFact', {'batch_id': '@newBatch', 'run_id': '@newRun', 'system_quantity': '5'}),
            clone('mrp_terminal_demand', "id=(SELECT selected_id FROM (SELECT MIN(t.id) selected_id FROM mrp_terminal_demand t JOIN mrp_daily_plan_detail d ON d.id=@sourceFact AND t.run_id=d.run_id AND t.factory_code=d.factory_code AND t.root_material_code=d.root_material_code AND t.terminal_material_code=d.terminal_material_code AND t.terminal_type=d.terminal_type AND t.supply_mode=d.supply_mode AND t.source_month=d.source_month) x)",
                {'batch_id': '@newBatch', 'run_id': '@newRun', 'quantity': '5'}),
            clone('mrp_daily_plan_detail', 'id=@sourceFact', {'batch_id': '@newBatch', 'run_id': '@newRun',
                'terminal_material_code': "'TEST_ORPHAN'", 'path_group_key': "'TEST_ORPHAN'", 'row_key': 'UUID()', 'system_quantity': '0.000001'}),
            'COMMIT;', 'SELECT @newBatch,@newRun;']
        batch, run = map(int, sql('\n'.join(statements))[-1].split('\t'))
        assert sql(f'SELECT COUNT(*) FROM mrp_terminal_demand WHERE batch_id={batch};') == ['1']
        conservation = http.ok(f'/mrp/batches/{batch}/plans/conservation')
        assert conservation['totalDemands'] == 1
        assert conservation['mismatchCount'] == (0 if phase == 'before' else 1)
        publication = None
        if phase == 'after':
            mismatch = conservation['mismatches'][0]
            from decimal import Decimal
            assert Decimal(str(mismatch['demandQuantity'])) == 0
            assert Decimal(str(mismatch['planQuantity'])) == Decimal('0.000001')
            count = sql('SELECT COUNT(*) FROM mrp_calc_batch;')
            status, body = http.req(f'/mrp/batches/{batch}/publish', 'POST', {})
            assert status == 400 and 'conservation' in body['error']['message'], (status, body)
            assert sql('SELECT COUNT(*) FROM mrp_calc_batch;') == count
            assert sql(f'SELECT status FROM mrp_calc_batch WHERE id={batch};') == ['READY']
            publication = {'status': status, 'message': body['error']['message'], 'noNewBatch': True, 'workingStatus': 'READY'}
        assert digest('mrp_daily_plan_detail', source) == original
        result = {'status': 'REPRODUCED_DEFECT' if phase == 'before' else 'PASS', 'phase': phase,
            'fixtureBatch': batch, 'fixtureRun': run, 'conservation': conservation, 'publication': publication,
            'retainedSourceUnchanged': True, 'checkedAt': datetime.datetime.now().astimezone().isoformat()}
        (ROOT / f'evidence/continuation-orphan-{phase}.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps(result, ensure_ascii=False))
    finally:
        if batch:
            sql('\n'.join(f'DELETE FROM {table} WHERE batch_id={batch};' for table in
                ['mrp_daily_plan_detail', 'mrp_terminal_demand', 'mrp_batch_factory_scope', 'mrp_batch_business_week_scope', 'mrp_calc_run'])
                + f'\nDELETE FROM mrp_calc_batch WHERE id={batch};')
