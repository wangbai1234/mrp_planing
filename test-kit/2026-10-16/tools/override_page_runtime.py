"""Read-only HTTP equivalence on a disposable large manual-override batch."""
import argparse, datetime, hashlib, json, statistics, time
from urllib.parse import urlencode
import continuation_access as api
from continuation_publish import ROOT
from override_page_benchmark import META

parser = argparse.ArgumentParser(); parser.add_argument('phase', choices=['before', 'after']); args = parser.parse_args()
out = ROOT / f'evidence/override-page-{args.phase}-runtime.json'
assert not out.exists(), 'Preserve existing evidence'
assert api.BASE == 'http://127.0.0.1:8180/api/v1'
meta = json.loads(META.read_text()); batch = meta['batch']
assert batch not in (10, 13)
api.token = api.ok('/auth/login', 'POST', {'username': 'admin', 'password': api.password()})['token']
common = dict(startDate='2026-09-01', endDate='2027-03-31', pageSize=100)
endpoint = f'/mrp/batches/{batch}/rolling-details?'
first = api.ok(endpoint + urlencode(common))
assert meta['sourceFacts']['rows'] > 200000
assert first['total'] > 90000
print('Fixture facts:', meta['sourceFacts']['rows'], 'positive terminal details:', first['total'], flush=True)
sample = first['items'][0]
selectors = [dict(common, page=1), dict(common, page=first['total'] // 200 + 1),
             dict(common, page=(first['total'] + 99) // 100), dict(common, terminalType='MACHINE_OUTPUT'),
             dict(common, factoryCode=sample['factoryCode'], rootMaterialCode=sample['rootMaterialCode'],
                  terminalMaterialCode=sample['terminalMaterialCode'], startDate=sample['scheduleDate'], endDate=sample['scheduleDate']),
             dict(common, factoryCode='TEST_NONEXISTENT')]
results = []
for selector in selectors:
    previous, samples = None, []
    for _ in range(5):
        begin = time.monotonic(); data = api.ok(endpoint + urlencode(selector)); elapsed = round((time.monotonic() - begin) * 1000)
        assert previous is None or data == previous
        previous = data; samples.append(elapsed)
    results.append(dict(selector=selector, samplesMs=samples, medianMs=statistics.median(samples),
                        response=previous, sha256=hashlib.sha256(json.dumps(previous, sort_keys=True).encode()).hexdigest()))
    print(args.phase, selector, samples, flush=True)
if args.phase == 'after':
    before = json.loads((ROOT / 'evidence/override-page-before-runtime.json').read_text())
    assert before['fixture'] == meta
    for old, new in zip(before['results'], results, strict=True):
        assert old['selector'] == new['selector'] and old['response'] == new['response'], new['selector']
        new['fullResponseUnchanged'] = True
out.write_text(json.dumps(dict(status='PASS', fixture=meta, results=results,
    checkedAt=datetime.datetime.now().astimezone().isoformat()), ensure_ascii=False, indent=2) + '\n')
print(args.phase, 'PASS', len(results), 'selectors, five repeats each', flush=True)
