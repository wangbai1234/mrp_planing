"""Read-only exact comparison: progress callbacks must not change scheduling facts."""
import hashlib
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parent / '2026-10-16/tools'))
from continuation_publish import sql, columns

checks = []
for table in ['mrp_inventory_pool', 'mrp_requirement_detail', 'mrp_terminal_demand', 'mrp_daily_plan_detail']:
    excluded = {'id', 'batch_id', 'run_id', 'created_at', 'updated_at', 'version'}
    cols = [c for c in columns(table) if c not in excluded]
    expression = ','.join("IFNULL(HEX(`" + col + "`),'NULL')" for col in cols)
    def values(batch):
        return sql("SELECT CONCAT_WS('|'," + expression + ') v FROM ' + table + f' WHERE batch_id={batch} ORDER BY v;')
    before, after = values(52), values(55)
    assert before and before == after, (table, len(before), len(after))
    checks.append({'table': table, 'rows': len(before), 'sha256': hashlib.sha256('\n'.join(before).encode()).hexdigest(), 'equal': True})
output = {'status': 'PASS', 'beforeBatch': 52, 'afterBatch': 55, 'exactComparisons': checks}
(ROOT / 'evidence/actual-progress-exact-comparison.json').write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(output, ensure_ascii=False))
