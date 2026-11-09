"""Read-only browser regression against retained data; never overwrite evidence."""
import argparse
import os
import re
import subprocess

import continuation_access as api
from continuation_publish import ROOT

parser = argparse.ArgumentParser()
parser.add_argument('environment', choices=['main', 'isolated'])
parser.add_argument('evidence_name')
args = parser.parse_args()
assert re.fullmatch(r'[a-z0-9-]+', args.evidence_name)
port, ui, batch = (8080, 5180, 91) if args.environment == 'main' else (8180, 5181, 13)
prefix = ROOT / f'evidence/{args.evidence_name}-{args.environment}-browser'
env = os.environ.copy()
env.update(UAT_PASSWORD=api.password(), UAT_API_BASE=f'http://127.0.0.1:{port}',
           UAT_UI_BASE=f'http://localhost:{ui}', UAT_BATCH_ID=str(batch),
           PLAYWRIGHT_USE_SYSTEM_CHROME='1', UAT_E2E_REPORT=str(prefix) + '-report',
           UAT_E2E_OUTPUT=str(prefix) + '-output')
with prefix.with_suffix('.log').open('x') as log:
    subprocess.run(['./node_modules/.bin/playwright', 'test',
                    '--config=../test-kit/2026-10-16/e2e/playwright.config.mjs',
                    'planning-performance.spec.mjs'], cwd=ROOT.parents[1] / 'frontend',
                   env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
print(args.environment, 'browser PASS', 'retained batch', batch)
