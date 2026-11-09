"""Read-only EXPLAIN of the automatic detail branch in the isolated fixture."""
import json
import re
import time
import xml.etree.ElementTree as ET
from continuation_publish import sql, ROOT

nodes = {n.attrib.get('id'): n for n in ET.parse(ROOT.parents[1] / 'backend/src/main/resources/mapper/mrp/RollingPlanningMapper.xml').getroot()}
baseline = json.loads((ROOT / 'evidence/details-index-before.json').read_text())
sample = next(r for r in baseline['results'] if r['port'] == 8180 and r['selector'].get('page', 1) > 1)
p = dict(batchId=sample['batch'], runId=sample['activeRunId'], rolling=True,
         factoryCode=None, rootMaterialCode=None, terminalMaterialCode=None, terminalType=None,
         offset=(sample['selector']['page'] - 1) * 100, pageSize=100, hasOverrides=False,
         startDate=sample['selector']['startDate'], endDate=sample['selector']['endDate'])

def condition(test):
    if test == '!hasOverrides':
        return not p['hasOverrides']
    return p.get(test.split()[0]) not in (None, '')

def render(node, props=None):
    props = props or {}
    if node.tag == 'include':
        return render(nodes[node.attrib['refid']], props | {x.attrib['name']: x.attrib['value'] for x in node})
    if node.tag == 'if' and not condition(node.attrib['test']):
        return ''
    if node.tag == 'choose':
        for child in node:
            if child.tag == 'otherwise' or condition(child.attrib['test']):
                return render(child, props)
        return ''
    result = node.text or ''
    for child in node:
        result += render(child, props) + (child.tail or '')
    for key, value in props.items():
        result = result.replace('${' + key + '}', value)
    def literal(match):
        value = p[match[1]]
        if value is None:
            return 'NULL'
        if isinstance(value, (int, bool)):
            return str(int(value))
        return "'" + str(value).replace("'", "''") + "'"
    return re.sub(r'#\{(\w+)\}', literal, result)

results = {}
for name in ['countDetails', 'selectDetails']:
    query = render(nodes[name])
    plan = sql('EXPLAIN ANALYZE ' + query + ';')
    samples = []
    for _ in range(3):
        begin = time.monotonic()
        rows = sql(query + ';')
        samples.append(round((time.monotonic() - begin) * 1000))
    results[name] = dict(sql=query, explain=plan, milliseconds=samples, rows=len(rows))
    print(name, samples, '\n'.join(plan), flush=True)
assert any('idx_mdpd_run_detail_order' in line for line in results['selectDetails']['explain'])
(ROOT / 'evidence/details-index-explain.json').write_text(json.dumps(dict(parameters=p, results=results), indent=2) + '\n')
