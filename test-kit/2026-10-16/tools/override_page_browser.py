"""Read-only planning browser checks; credentials stay in the subprocess environment."""
import argparse
import json
import os
import subprocess

import continuation_access as api
from continuation_publish import ROOT
from override_page_benchmark import META

parser = argparse.ArgumentParser()
parser.add_argument('environment', choices=['main', 'isolated'])
args = parser.parse_args()
port, ui, batch = (8080, 5180, 91) if args.environment == 'main' else (
    8180, 5181, json.loads(META.read_text())['batch'])
prefix = ROOT / f'evidence/override-page-{args.environment}-browser'
log_path = prefix.with_suffix('.log')
assert not log_path.exists(), 'Preserve existing evidence'
env = os.environ.copy()
env.update(UAT_PASSWORD=api.password(), UAT_API_BASE=f'http://127.0.0.1:{port}',
           UAT_UI_BASE=f'http://localhost:{ui}', UAT_BATCH_ID=str(batch),
           PLAYWRIGHT_USE_SYSTEM_CHROME='1', UAT_E2E_REPORT=str(prefix) + '-report',
           UAT_E2E_OUTPUT=str(prefix) + '-output')
with log_path.open('w') as log:
    subprocess.run(['./node_modules/.bin/playwright', 'test',
                    '--config=../test-kit/2026-10-16/e2e/playwright.config.mjs',
                    'planning-performance.spec.mjs'], cwd=ROOT.parents[1] / 'frontend',
                   env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
print(args.environment, 'browser PASS', 'batch', batch)
