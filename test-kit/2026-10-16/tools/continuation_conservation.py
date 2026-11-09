"""Read-only before/after conservation requests against retained large plans."""
import json, pathlib, sys, time, urllib.request
from continuation_access import password

ROOT = pathlib.Path(__file__).resolve().parents[1]
phase = sys.argv[1]
assert phase in ('before', 'after')
results = []
for port, batch in [(8080, 91), (8180, 13)]:
    base = f'http://127.0.0.1:{port}/api/v1'
    def request(path, body=None, token=None):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        with urllib.request.urlopen(urllib.request.Request(base + path, headers=headers,
                data=None if body is None else json.dumps(body).encode()), timeout=120) as response:
            data = json.load(response)
            assert response.status == 200 and data['success']
            return data['data']
    token = request('/auth/login', {'username': 'admin', 'password': password()})['token']
    metadata = request(f'/mrp/batches/{batch}', token=token)
    samples = []
    payload = None
    for _ in range(3):
        start = time.monotonic()
        current = request(f'/mrp/batches/{batch}/plans/conservation', token=token)
        samples.append(round((time.monotonic() - start) * 1000))
        if payload is not None:
            assert current == payload
        payload = current
    results.append({'batch': batch, 'port': port, 'ruleVersion': metadata['ruleVersion'],
                    'activeRunId': metadata['activeRunId'], 'milliseconds': samples, 'response': payload})
if phase == 'after':
    baseline = json.loads((ROOT / 'evidence/continuation-conservation-before.json').read_text())
    for previous, current in zip(baseline['results'], results):
        assert previous['response'] == current['response'], (current['batch'], 'response changed')
        assert previous['activeRunId'] == current['activeRunId']
        current['responseUnchanged'] = True
output = {'status': 'PASS', 'phase': phase, 'results': results}
(ROOT / f'evidence/continuation-conservation-{phase}.json').write_text(
    json.dumps(output, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(output, ensure_ascii=False))
