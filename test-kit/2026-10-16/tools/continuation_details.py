"""Read-only large-plan detail pagination baseline and response equivalence."""
import hashlib, json, pathlib, sys, time, urllib.parse, urllib.request
from continuation_access import password

ROOT = pathlib.Path(__file__).resolve().parents[1]

def run(phase):
    assert phase in {'before', 'after'}
    results = []
    for port, batch in [(8080, 91), (8180, 13)]:
        base = f'http://127.0.0.1:{port}/api/v1'
        def request(path, body=None, token=None):
            headers = {'Content-Type': 'application/json'}
            if token:
                headers['Authorization'] = 'Bearer ' + token
            begin = time.monotonic()
            with urllib.request.urlopen(urllib.request.Request(base + path, headers=headers,
                    data=None if body is None else json.dumps(body).encode()), timeout=180) as response:
                raw = response.read()
                data = json.loads(raw)
                assert response.status == 200 and data['success']
                return data['data'], round((time.monotonic() - begin) * 1000), len(raw)
        login, _, _ = request('/auth/login', {'username': 'admin', 'password': password()})
        token = login['token']
        metadata, _, _ = request(f'/mrp/batches/{batch}', token=token)
        grid, _, _ = request(f'/mrp/batches/{batch}/rolling-grid?pageSize=1', token=token)
        common = {'startDate': grid['calendarDays'][0], 'endDate': grid['calendarDays'][-1], 'pageSize': 100}
        endpoint = f'/mrp/batches/{batch}/rolling-details?'
        first, _, _ = request(endpoint + urllib.parse.urlencode(common), token=token)
        assert first['total'] > 100000
        sample = first['items'][0]
        selectors = [dict(common, page=1), dict(common, page=(first['total'] // 200) + 1),
            dict(common, page=(first['total'] + 99) // 100), dict(common, terminalType='MACHINE_OUTPUT'),
            dict(common, factoryCode=sample['factoryCode'], rootMaterialCode=sample['rootMaterialCode'],
                terminalMaterialCode=sample['terminalMaterialCode'], startDate=sample['scheduleDate'], endDate=sample['scheduleDate']),
            dict(common, factoryCode='TEST_NONEXISTENT')]
        for selector in selectors:
            samples = []
            previous = None
            for _ in range(3):
                current, ms, size = request(endpoint + urllib.parse.urlencode(selector), token=token)
                samples.append(ms)
                assert previous is None or previous == current
                previous = current
            digest = hashlib.sha256(json.dumps(previous, sort_keys=True).encode()).hexdigest()
            results.append({'port': port, 'batch': batch, 'activeRunId': metadata['activeRunId'],
                'selector': selector, 'milliseconds': samples, 'responseBytes': size,
                'total': previous['total'], 'items': previous['items'], 'sha256': digest})
            print(port, batch, selector.get('page', 1), selector.get('terminalType', 'TERMINAL'), samples, flush=True)
    if phase == 'after':
        baseline = json.loads((ROOT / 'evidence/continuation-details-before.json').read_text())
        for old, current in zip(baseline['results'], results, strict=True):
            assert old['selector'] == current['selector'] and old['activeRunId'] == current['activeRunId']
            assert old['sha256'] == current['sha256'], (current['port'], current['selector'], 'response changed')
            current['responseUnchanged'] = True
    output = {'status': 'PASS', 'phase': phase, 'results': results}
    (ROOT / f'evidence/continuation-details-{phase}.json').write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n')

if __name__ == '__main__':
    run(sys.argv[1])
