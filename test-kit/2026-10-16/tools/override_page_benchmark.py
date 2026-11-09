"""Isolated, disposable large override fixture and exact SQL A/B evidence.

Never changes retained batches 10/13 or the main database. Leaves its own fixture
for HTTP comparison; cleanup is explicit and verifies the source fingerprint.
"""
import argparse, hashlib, json, re, statistics, time
import xml.etree.ElementTree as ET
from continuation_publish import ROOT, sql, clone, digest

META = ROOT / 'evidence/override-page-fixture.json'
ORDER = 'schedule_date,source_month,factory_code,root_material_code,terminal_material_code,path_group_key,terminal_type,supply_mode'


def renderer(params):
    nodes = {n.get('id'): n for n in ET.parse(ROOT.parents[1] / 'backend/src/main/resources/mapper/mrp/RollingPlanningMapper.xml').getroot()}

    def condition(expression):
        name = expression.split()[0]
        return not params.get(name[1:]) if name.startswith('!') else params.get(name) not in (None, '', False)

    def render(node, props=None):
        props = props or {}
        if isinstance(node, str):
            node = nodes[node]
        if node.tag == 'include':
            return render(nodes[node.get('refid')], props | {x.get('name'): x.get('value') for x in node})
        if node.tag == 'if' and not condition(node.get('test')):
            return ''
        if node.tag == 'choose':
            return next(render(x, props) for x in node if x.tag == 'otherwise' or condition(x.get('test')))
        result = node.text or ''
        for child in node:
            result += render(child, props) + (child.tail or '')
        for key, value in props.items():
            result = result.replace('${' + key + '}', value)
        return re.sub(r'#\{(\w+)\}', lambda m: 'NULL' if params[m[1]] is None else
                      str(int(params[m[1]])) if isinstance(params[m[1]], (int, bool)) else
                      "'" + str(params[m[1]]).replace("'", "''") + "'", result)
    return render


def candidate(p, render):
    keys = ','.join('d.' + key for key in ORDER.split(',') if key != 'schedule_date')
    union = ('SELECT d.id detail_id,o.id override_id,0 is_virtual,d.schedule_date,' + keys + render('storedFactSource') +
             ' UNION ALL SELECT d.id detail_id,o.id override_id,1 is_virtual,o.schedule_date,' + keys + render('virtualFactSource'))
    return f'''SELECT CASE WHEN p.is_virtual=1 THEN NULL ELSE d.id END id,
      d.run_id,d.batch_id,d.factory_code,d.root_material_code,d.terminal_material_code,d.terminal_type,
      d.supply_mode,d.path_group_key,d.source_month,
      CASE WHEN p.is_virtual=1 THEN {"p.schedule_date" if p['rolling'] else "DATE_ADD(p.schedule_date,INTERVAL 7 DAY)"} ELSE d.source_date END source_date,
      p.schedule_date,CASE WHEN p.is_virtual=1 THEN CAST(0 AS DECIMAL(18,6)) ELSE d.system_quantity END system_quantity,
      COALESCE(o.manual_quantity,d.system_quantity) effective_quantity,
      CASE WHEN p.is_virtual=1 THEN TRUE ELSE d.is_production_day END is_production_day,
      COALESCE(o.revision,-1) revision,
      CASE WHEN p.is_virtual=1 THEN SHA2(CONCAT(d.factory_code,'|',d.root_material_code,'|',d.terminal_material_code,'|',d.terminal_type,'|',d.supply_mode,'|',d.path_group_key,'|',d.source_month,'|',p.schedule_date),256) ELSE d.row_key END row_key
      FROM (SELECT * FROM ({union}) ids ORDER BY {ORDER} LIMIT {p['offset']},{p['pageSize']}) p
      JOIN mrp_daily_plan_detail d ON d.id=p.detail_id
      LEFT JOIN mrp_daily_plan_override o ON o.id=p.override_id
      ORDER BY {','.join('p.' + key for key in ORDER.split(','))}'''


def create():
    assert not META.exists(), 'Preserve existing fixture/evidence'
    source = 13
    before = digest('mrp_daily_plan_detail', source)
    statements = ['START TRANSACTION;', f'SET @sourceRun=(SELECT active_run_id FROM mrp_calc_batch WHERE id={source});',
                  clone('mrp_calc_batch', f'id={source}', {'status': "'READY'", 'active_run_id': 'NULL', 'published_at': 'NULL', 'active_request_key': 'NULL', 'revision_of_batch_id': '13', 'superseded_by_batch_id': 'NULL'}),
                  'SET @newBatch=LAST_INSERT_ID();', clone('mrp_calc_run', 'id=@sourceRun', {'batch_id': '@newBatch', 'status': "'ACTIVE'"}),
                  'SET @newRun=LAST_INSERT_ID();', 'UPDATE mrp_calc_batch SET active_run_id=@newRun WHERE id=@newBatch;']
    for table in ['mrp_batch_factory_scope', 'mrp_batch_business_week_scope', 'mrp_daily_plan_detail']:
        statements.append(clone(table, f'batch_id={source}', {'batch_id': '@newBatch', **({'run_id': '@newRun'} if table == 'mrp_daily_plan_detail' else {})}))
    fields = 'batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity,revision,reason'
    identity = 'd.factory_code,d.root_material_code,d.terminal_material_code,d.terminal_type,d.supply_mode,d.path_group_key'
    statements += [f"INSERT INTO mrp_daily_plan_override({fields}) SELECT @newBatch,{identity},d.schedule_date,CASE MOD(d.id,3) WHEN 0 THEN 0 WHEN 1 THEN -0.000001 ELSE 1.000001 END,2,'disposable performance fixture' FROM mrp_daily_plan_detail d WHERE d.run_id=@newRun AND d.terminal_type!='MACHINE_OUTPUT' ORDER BY d.id LIMIT 30;",
                   f"INSERT INTO mrp_daily_plan_override({fields}) SELECT @newBatch,{identity},DATE_SUB(MIN(d.schedule_date),INTERVAL 1 DAY),1.000001,3,'disposable virtual fixture' FROM mrp_daily_plan_detail d WHERE d.run_id=@newRun AND d.terminal_type!='MACHINE_OUTPUT' GROUP BY {identity} ORDER BY MIN(d.id) LIMIT 30;",
                   'COMMIT;', 'SELECT @newBatch,@newRun;']
    batch, run = map(int, sql('\n'.join(statements))[-1].split('\t'))
    META.write_text(json.dumps({'source': source, 'batch': batch, 'run': run, 'sourceFacts': before}, indent=2) + '\n')
    assert digest('mrp_daily_plan_detail', source) == before
    print('Created disposable large fixture', batch, run, flush=True)


def benchmark():
    meta = json.loads(META.read_text())
    output = ROOT / 'evidence/override-page-sql-ab.json'
    assert not output.exists(), 'Preserve evidence'
    p = dict(batchId=meta['batch'], runId=meta['run'], rolling=True, hasOverrides=True,
             startDate='2026-09-01', endDate='2027-03-31', factoryCode=None,
             rootMaterialCode=None, terminalMaterialCode=None, terminalType=None, offset=0, pageSize=100)
    render = renderer(p)
    total = int(sql(render('countDetails'))[0])
    results = []
    for offset in [0, total // 2, max(0, total - 100)]:
        p['offset'] = offset
        queries = {'original': 'SELECT * FROM (' + render('effectiveFacts') + ') effective ORDER BY ' + ORDER + f' LIMIT {offset},100',
                   'candidate': candidate(p, render)}
        rows = {name: sql(q) for name, q in queries.items()}
        assert rows['original'] == rows['candidate'], ('full field comparison failed', offset)
        entry = {'offset': offset, 'rows': len(rows['original']), 'sha256': hashlib.sha256('\n'.join(rows['original']).encode()).hexdigest(), 'samples': {name: [] for name in queries}}
        entry['explain'] = {name: sql('EXPLAIN ANALYZE ' + q) for name, q in queries.items()}
        for cycle in range(5):
            for name in (list(queries) if cycle % 2 == 0 else list(reversed(queries))):
                begin = time.monotonic(); current = sql(queries[name]); elapsed = round((time.monotonic() - begin) * 1000)
                assert current == rows['original']
                entry['samples'][name].append(elapsed)
                print(offset, cycle, name, elapsed, flush=True)
        entry['medianMs'] = {name: statistics.median(samples) for name, samples in entry['samples'].items()}
        results.append(entry)
    assert digest('mrp_daily_plan_detail', meta['source']) == meta['sourceFacts']
    output.write_text(json.dumps({'status': 'PASS', 'fixture': meta, 'total': total, 'results': results}, indent=2) + '\n')


def cleanup():
    meta = json.loads(META.read_text()); batch = meta['batch']
    assert batch not in (10, 13)
    assert sql(f'SELECT revision_of_batch_id FROM mrp_calc_batch WHERE id={batch}') == ['13']
    for table in ['mrp_daily_plan_override', 'mrp_daily_plan_detail', 'mrp_batch_factory_scope', 'mrp_batch_business_week_scope', 'mrp_calc_run']:
        sql(f'DELETE FROM {table} WHERE batch_id={batch}')
    sql(f'DELETE FROM mrp_calc_batch WHERE id={batch}')
    assert digest('mrp_daily_plan_detail', meta['source']) == meta['sourceFacts']
    assert sql(f'SELECT COUNT(*) FROM mrp_calc_batch WHERE id={batch}') == ['0']
    print('Disposable fixture removed; source unchanged', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('action', choices=['create', 'benchmark', 'cleanup'])
    globals()[parser.parse_args().action]()
