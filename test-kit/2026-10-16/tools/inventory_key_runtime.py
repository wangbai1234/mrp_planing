"""Delimiter regression: temporary isolated inventory only, never retained plans/master data."""
import argparse, datetime, json, time
from urllib.parse import urlencode
import continuation_access as api
from continuation_publish import ROOT, sql

parser = argparse.ArgumentParser()
parser.add_argument('phase', choices=['before', 'after'])
parser.add_argument('--tag', default='')
args = parser.parse_args()
out = ROOT / f'evidence/inventory-key-{args.phase}{args.tag}-runtime.json'
assert not out.exists(), 'Preserve original evidence'
assert api.BASE == 'http://127.0.0.1:8180/api/v1'
api.token = api.ok('/auth/login', 'POST', {'username': 'admin', 'password': api.password()})['token']
stamp = 'key' + str(time.time_ns())
warehouse, factory, material = stamp + '|W', stamp + 'F', 'KEY|M'
snapshot = None
checks, actual = [], []
passed = False
try:
    snapshot = int(sql("INSERT INTO inventory_snapshot(snapshot_date,status) VALUES('2026-10-16','TEST'); SELECT LAST_INSERT_ID();")[0])
    sql(f"INSERT INTO factory_warehouse_mapping(factory_code,warehouse_code) VALUES('{factory}','{warehouse}');"
        f"INSERT INTO inventory_item(snapshot_id,inventory_category,material_id,supplier_name,warehouse_code,inventory_quality,shipping_available_qty) VALUES"
        f"({snapshot},'TEST','{material}','TEST','{warehouse}','GOOD',10),"
        f"({snapshot},'TEST','{material}','TEST','{warehouse}','GOOD',5),"
        f"({snapshot},'TEST','{material}','TEST','{warehouse}','DEFECTIVE',7);")
    query = urlencode({'factoryCode': 'ALL', 'inventoryOrg': warehouse})
    normalized = api.ok(f'/mrp/inventory/normalize/{snapshot}?{query}', 'POST')
    checks.append({'operation': 'normalize', 'status': 200, 'rows': len(normalized)})
    actual = [{k: row[k] for k in ['factoryCode', 'warehouseScope', 'materialCode', 'inventoryQuality', 'availableQuantity', 'includedFlag']}
              for row in normalized]
    expected = {(factory, warehouse, material, 'GOOD'): (15, True),
                (factory, warehouse, material, 'DEFECTIVE'): (7, False)}
    by_key = {(r['factoryCode'], r['warehouseScope'], r['materialCode'], r['inventoryQuality']):
              (r['availableQuantity'], r['includedFlag']) for r in normalized}
    correct = by_key == expected
    assert correct == (args.phase == 'after'), (args.phase, actual)
    balances = api.ok(f'/mrp/inventory/balances/{snapshot}')
    checks.append({'operation': 'read persisted balances', 'status': 200, 'rows': len(balances)})
    assert sorted(actual, key=str) == sorted([{k: r[k] for k in actual[0]} for r in balances], key=str)
    passed = True
finally:
    if snapshot:
        sql(f'DELETE FROM mrp_inventory_balance WHERE inventory_snapshot_id={snapshot};'
            f'DELETE FROM inventory_item WHERE snapshot_id={snapshot};'
            f'DELETE FROM inventory_snapshot WHERE id={snapshot};')
    sql(f"DELETE FROM factory_warehouse_mapping WHERE factory_code='{factory}';")
    remaining = sql(f"SELECT COUNT(*) FROM factory_warehouse_mapping WHERE factory_code='{factory}';")
    assert remaining == ['0']
    out.write_text(json.dumps({'status': 'PASS' if passed else 'FAIL', 'phase': args.phase,
        'expectedBehavior': 'preserve complete warehouse/material/quality codes', 'checks': checks, 'actual': actual,
        'regressionReproduced': passed and args.phase == 'before', 'remainingFixtures': 0,
        'checkedAt': datetime.datetime.now().astimezone().isoformat()}, ensure_ascii=False, indent=2) + '\n')
print(args.phase, 'PASS', len(checks), 'HTTP checks; temporary fixtures removed')
