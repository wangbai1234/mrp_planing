"""Clone an expendable READY fixture in isolated MySQL; preserve source/master data."""
import json
import pathlib
import sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / '2026-10-16/tools'))
from continuation_publish import sql, clone

SOURCE = 55
assert sql('SELECT status FROM mrp_calc_batch WHERE id=55;') == ['CANCELLED']
statement = [
    'START TRANSACTION;',
    clone('mrp_calc_batch', 'id=55', {
        'status': "'READY'", 'published_at': 'NULL', 'revision_of_batch_id': '13',
        'superseded_by_batch_id': 'NULL', 'result_checksum': 'NULL', 'active_request_key': 'NULL'}),
    'SET @newBatch=LAST_INSERT_ID();',
    clone('mrp_calc_run', 'id=(SELECT active_run_id FROM mrp_calc_batch WHERE id=55)', {'batch_id': '@newBatch', 'status': "'ACTIVE'"}),
    'SET @newRun=LAST_INSERT_ID();',
    'UPDATE mrp_calc_batch SET active_run_id=@newRun WHERE id=@newBatch;',
]
for table in ['mrp_batch_factory_scope','mrp_batch_business_week_scope','mrp_inventory_pool']:
    statement.append(clone(table, 'batch_id=55', {'batch_id': '@newBatch'}))
for table in ['mrp_requirement_detail','mrp_terminal_demand','mrp_daily_plan_detail','mrp_material_policy_notice']:
    statement.append(clone(table, 'run_id=(SELECT active_run_id FROM mrp_calc_batch WHERE id=55)',
                           {'batch_id':'@newBatch','run_id':'@newRun'}))
statement += ['COMMIT;', 'SELECT @newBatch,@newRun;']
batch, run = map(int, sql(chr(10).join(statement))[-1].split(chr(9)))
assert sql('SELECT status FROM mrp_calc_batch WHERE id='+str(batch)+';') == ['READY']
output={'source':SOURCE,'batch':batch,'run':run,
        'dailyRows':int(sql('SELECT COUNT(*) FROM mrp_daily_plan_detail WHERE batch_id='+str(batch)+';')[0])}
assert output['dailyRows']==38850
path=pathlib.Path(__file__).resolve().parent/'evidence/lifecycle-background-fixture.json'
path.write_text(json.dumps(output,indent=2)+chr(10))
print(json.dumps(output))
