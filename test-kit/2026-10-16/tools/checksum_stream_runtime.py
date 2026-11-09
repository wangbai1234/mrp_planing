"""Read-only checksum rollout checks; never publishes or adjusts either large batch."""
import argparse
import datetime
import json
import os
import pathlib
import subprocess
import time

import continuation_access as api

ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('environment', choices=['main', 'isolated'])
parser.add_argument('--browser', action='store_true')
args = parser.parse_args()
main = args.environment == 'main'
port, ui, batch = (8080, 5180, 91) if main else (8180, 5181, 10)
api.BASE = f'http://127.0.0.1:{port}/api/v1'
deadline = time.monotonic() + 50
while True:
    try:
        api.token = api.ok('/auth/login', 'POST', {'username': 'admin', 'password': api.password()})['token']
        break
    except (ConnectionError, OSError, json.JSONDecodeError):
        if time.monotonic() >= deadline:
            raise
        time.sleep(1)

before = json.loads((ROOT / 'evidence/checksum-stream-before.json').read_text())[args.environment]
assert before['batchId'] == batch
samples = []
for _ in range(3):
    start = time.monotonic()
    data = api.ok(f'/mrp/batches/{batch}/capacity-review')
    samples.append(round((time.monotonic() - start) * 1000))
    assert data == before['data'], 'Capacity loads or fingerprint changed'
result = {'environment': args.environment, 'batchId': batch,
          'checkedAt': datetime.datetime.now().astimezone().isoformat(),
          'data': data, 'ms': samples, 'beforeMs': before['ms'],
          'exactResponseMatch': True}
(ROOT / f'evidence/checksum-stream-{args.environment}-after.json').write_text(
    json.dumps(result, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({k: v for k, v in result.items() if k != 'data'}, ensure_ascii=False), flush=True)
if args.browser:
    env = os.environ.copy()
    env.update(UAT_PASSWORD=api.password(), UAT_API_BASE=f'http://127.0.0.1:{port}',
               UAT_UI_BASE=f'http://localhost:{ui}', UAT_BATCH_ID=str(batch),
               PLAYWRIGHT_USE_SYSTEM_CHROME='1',
               UAT_E2E_REPORT=str(ROOT / f'evidence/checksum-stream-{args.environment}-browser-report'),
               UAT_E2E_OUTPUT=str(ROOT / f'evidence/checksum-stream-{args.environment}-browser-output'))
    with (ROOT / f'evidence/checksum-stream-{args.environment}-browser.log').open('w') as log:
        subprocess.run(['./node_modules/.bin/playwright', 'test',
                        '--config=../test-kit/2026-10-16/e2e/playwright.config.mjs',
                        'planning-performance.spec.mjs'], cwd=ROOT.parents[1] / 'frontend',
                       env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
    print(args.environment, 'browser PASS')
