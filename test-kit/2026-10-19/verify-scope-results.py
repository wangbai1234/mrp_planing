"""Read-only exact comparison of retained single-factory and ALL calculations."""
import hashlib
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parent / '2026-10-16/tools'))
from continuation_publish import sql, columns

runtime = json.loads((ROOT / 'evidence/create-scope-progress-runtime.json').read_text())
cases = {case['factory']: case['batch'] for case in runtime['results']}
checks = []
for table in ['mrp_inventory_pool', 'mrp_requirement_detail', 'mrp_terminal_demand', 'mrp_daily_plan_detail']:
    excluded = {'id', 'batch_id', 'run_id', 'created_at', 'updated_at', 'version'}
    cols = [c for c in columns(table) if c not in excluded]
    expression = ','.join("IFNULL(HEX(`" + col + "`),'NULL')" for col in cols)
    for factory in ['爱培科', '永惠']:
        def values(batch):
            return sql("SELECT CONCAT_WS('|'," + expression + ') v FROM ' + table
                       + f" WHERE batch_id={batch} AND factory_code='{factory}' ORDER BY v;")
        own, combined = values(cases[factory]), values(cases['ALL'])
        assert own and own == combined, (table, factory, len(own), len(combined))
        checks.append({'table': table, 'factory': factory, 'rows': len(own),
                       'sha256': hashlib.sha256('\n'.join(own).encode()).hexdigest(), 'equal': True})
for factory in ['爱培科', '永惠']:
    for table in ['mrp_inventory_pool', 'mrp_requirement_detail', 'mrp_daily_plan_detail']:
        assert sql(f"SELECT COUNT(*) FROM {table} WHERE batch_id={cases[factory]} AND factory_code!='{factory}';") == ['0']
output = {'status': 'PASS', 'batches': cases, 'exactComparisons': checks, 'foreignFactoryRows': 0}
(ROOT / 'evidence/scope-exact-comparison.json').write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(output, ensure_ascii=False))
