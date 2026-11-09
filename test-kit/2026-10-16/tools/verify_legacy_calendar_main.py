"""Read-only legacy calendar smoke plus existing protected main-data SHA checks."""
import datetime, json, os, pathlib, subprocess
import continuation_access as api
from continuation_publish import ROOT

tag='legacy-calendar-scope'
output=ROOT/f'evidence/{tag}-main-runtime.json'
assert not output.exists(), 'Preserve existing evidence'
api.BASE='http://127.0.0.1:8080/api/v1'
api.token=api.ok('/auth/login','POST',{'username':'admin','password':api.password()})['token']
versions=api.ok('/mrp/production-calendar-versions')
assert versions
choices=api.ok('/mrp/batch-creation-versions')
assert [r['id'] for r in versions]==[r['id'] for r in choices['productionCalendarVersions']]
vid=versions[0]['id']
version=api.ok(f'/mrp/production-calendar-versions/{vid}')
days=api.ok(f'/mrp/production-calendar-versions/{vid}/days')
assert version['id']==vid and isinstance(days,list)
env=os.environ.copy();env.update(UAT_API_BASE='http://127.0.0.1:8080',UAT_UI_BASE='http://localhost:5180',
    UAT_PASSWORD=api.password(),PLAYWRIGHT_USE_SYSTEM_CHROME='1',
    UAT_E2E_REPORT=str(ROOT/f'evidence/{tag}-main-browser-report'),UAT_E2E_OUTPUT=str(ROOT/f'evidence/{tag}-main-browser-output'))
with (ROOT/f'evidence/{tag}-main-browser.log').open('x') as log:
    subprocess.run(['./node_modules/.bin/playwright','test','--config=../test-kit/2026-10-16/e2e/playwright.config.mjs','legacy-calendar-scope.spec.mjs'],
        cwd=ROOT.parents[1]/'frontend',env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
output.write_text(json.dumps({'status':'PASS','httpChecks':4,'visibleVersions':len(versions),'detailDays':len(days),
    'checkedAt':datetime.datetime.now().astimezone().isoformat()},ensure_ascii=False,indent=2)+'\n')
p=pathlib.Path(__file__).with_name('verify_rollout.py')
source=p.read_text().replace("(evidence / 'main-rollout-verification.json')",'(root / '+repr(f'evidence/{tag}-main-protection.json')+')')
exec(compile(source,str(p),'exec'),{'__file__':str(p)})
