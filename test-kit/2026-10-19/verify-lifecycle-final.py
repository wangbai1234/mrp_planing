"""Real HTTP verification against an expendable isolated READY batch only."""
import json
import pathlib
import re
import sys
import time
import urllib.request

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / '2026-10-16/tools'))
from continuation_publish import sql

ROOT = pathlib.Path(__file__).resolve().parents[2]
password = re.search(r'Default admin user: admin / (\S+)', next((ROOT / 'backend/src/main/resources/db/migration').glob('V001*')).read_text()).group(1)
base = 'http://127.0.0.1:8180/api/v1'
batch = int(json.loads((pathlib.Path(__file__).parent / 'evidence/lifecycle-background-fixture.json').read_text())['batch'])
assert sql(f'SELECT status FROM mrp_calc_batch WHERE id={batch};') == ['SUPERSEDED']
token = None
def api(path, body=None):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(base + path, headers=headers, data=None if body is None else json.dumps(body).encode())
    started = time.monotonic()
    with urllib.request.urlopen(req, timeout=180) as response:
        result = json.load(response)
        assert result['success']
        return response.status, result['data'], round((time.monotonic()-started)*1000)

token = api('/auth/login', {'username': 'admin', 'password': password})[1]['token']
publish = api(f'/mrp/batches/{batch}/lifecycle-task?kind=publish')[1]
assert publish['status'] == 'SUCCEEDED', publish
published = publish['resultBatchId']
revision = api(f'/mrp/batches/{published}/revise', {})
assert revision[0] == 202 and revision[2] < 2000, revision[:2]
deadline = time.monotonic() + 120
while True:
    revised = api(f'/mrp/batches/{published}/lifecycle-task?kind=revise')[1]
    if revised['status'] not in ('PENDING','RUNNING'): break
    assert time.monotonic() < deadline, revised
    time.sleep(1)
assert revised['status'] == 'SUCCEEDED', revised
checks = {}
for table in ('mrp_requirement_detail','mrp_terminal_demand','mrp_daily_plan_detail'):
    a = int(sql(f'SELECT COUNT(1) FROM {table} WHERE batch_id={batch};')[0])
    b = int(sql(f'SELECT COUNT(1) FROM {table} WHERE batch_id={published};')[0])
    assert a == b, (table,a,b)
    checks[table] = a
assert api(f'/mrp/batches/{published}/plans/conservation')[1]['mismatchCount'] == 0
output = {'sourceBatch':batch,'publishTask':publish['taskId'],'publishedBatch':published,
          'revisionTask':revised['taskId'],'revisionBatch':revised['resultBatchId'],
          'revisionSubmitMs':revision[2],'factCounts':checks}
(pathlib.Path(__file__).parent/'evidence/lifecycle-background-final-api.json').write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(output,ensure_ascii=False))
