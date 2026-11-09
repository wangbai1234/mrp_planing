"""Read-only main HTTP/browser smoke and protected-data verification."""
import argparse, datetime, json, os, pathlib, subprocess
import continuation_access as api
from continuation_publish import ROOT

parser=argparse.ArgumentParser();parser.add_argument('tag');args=parser.parse_args()
output=ROOT/f'evidence/{args.tag}-main-runtime.json'
assert not output.exists(), 'Preserve existing evidence'
api.BASE='http://127.0.0.1:8080/api/v1'
api.token=api.ok('/auth/login','POST',{'username':'admin','password':api.password()})['token']
checks=[]
for path in ['/mrp/stock-policies/page?page=1&pageSize=20','/mrp/stock-policies/warehouses']:
    status,payload=api.req(path,auth=api.token)
    assert status==200 and payload['success'],(path,status)
    data=payload['data'];checks.append({'path':path,'status':status,'count':len(data['items'] if isinstance(data,dict) else data)})
env=os.environ.copy();env.update(UAT_API_BASE='http://127.0.0.1:8080',UAT_UI_BASE='http://localhost:5180',
    UAT_PASSWORD=api.password(),PLAYWRIGHT_USE_SYSTEM_CHROME='1',
    UAT_E2E_REPORT=str(ROOT/f'evidence/{args.tag}-main-browser-report'),UAT_E2E_OUTPUT=str(ROOT/f'evidence/{args.tag}-main-browser-output'))
with (ROOT/f'evidence/{args.tag}-main-browser.log').open('x') as log:
    subprocess.run(['./node_modules/.bin/playwright','test','--config=../test-kit/2026-10-16/e2e/playwright.config.mjs','stock-scope.spec.mjs'],
        cwd=ROOT.parents[1]/'frontend',env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
output.write_text(json.dumps({'status':'PASS','checks':checks,'checkedAt':datetime.datetime.now().astimezone().isoformat()},ensure_ascii=False,indent=2)+'\n')
p=pathlib.Path(__file__).with_name('verify_rollout.py')
source=p.read_text().replace("(evidence / 'main-rollout-verification.json')",
                            '(root / '+repr(f'evidence/{args.tag}-main-protection.json')+')')
exec(compile(source,str(p),'exec'),{'__file__':str(p)})
