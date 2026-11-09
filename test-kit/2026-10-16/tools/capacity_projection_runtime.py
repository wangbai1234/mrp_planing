"""Read-only capacity review comparison on preserved large batches."""
import argparse, datetime, json, os, pathlib, subprocess, threading, time
import psutil
import continuation_access as api

ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('environment', choices=['main', 'isolated'])
parser.add_argument('phase', choices=['before', 'after'])
parser.add_argument('--browser', action='store_true')
args = parser.parse_args()
port, ui, batch = (8080, 5180, 91) if args.environment == 'main' else (8180, 5181, 10)
api.BASE = f'http://127.0.0.1:{port}/api/v1'
api.token = api.ok('/auth/login', 'POST', {'username': 'admin', 'password': api.password()})['token']
baseline = json.loads((ROOT/'evidence/checksum-stream-before.json').read_text())[args.environment]['data']
pid = int(subprocess.check_output(['lsof', '-t', f'-iTCP:{port}', '-sTCP:LISTEN'], text=True).strip())
proc = psutil.Process(pid)
samples = []
for _ in range(3):
    observed = [proc.memory_info().rss]
    stop = threading.Event()
    def observe():
        while not stop.wait(0.05): observed.append(proc.memory_info().rss)
    thread = threading.Thread(target=observe); thread.start()
    cpu = proc.cpu_times(); started = time.monotonic()
    try:
        data = api.ok(f'/mrp/batches/{batch}/capacity-review')
    finally:
        elapsed = time.monotonic()-started
        stop.set(); thread.join()
    end_cpu = proc.cpu_times()
    assert data == baseline, 'Capacity loads or acknowledgment fingerprint changed'
    samples.append({'ms': round(elapsed*1000), 'cpuSeconds': round(end_cpu.user+end_cpu.system-cpu.user-cpu.system, 3),
        'startRssMiB': round(observed[0]/1048576, 2), 'peakRssMiB': round(max(observed)/1048576, 2),
        'rssIncreaseMiB': round((max(observed)-observed[0])/1048576, 2)})
result = {'status':'PASS', 'environment':args.environment, 'batchId':batch,
    'checkedAt':datetime.datetime.now().astimezone().isoformat(), 'data':data, 'samples':samples,
    'exactResponseMatch':True, 'measurement':'Process RSS/CPU sampled during sequential read-only HTTP; not peak heap or exclusive request CPU.'}
path=ROOT/f'evidence/capacity-projection-{args.environment}-{args.phase}.json'
assert not path.exists(), 'Preserve existing evidence'
path.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k!='data'}, ensure_ascii=False), flush=True)
if args.browser:
    env=os.environ.copy(); env.update(UAT_PASSWORD=api.password(), UAT_API_BASE=f'http://127.0.0.1:{port}',
        UAT_UI_BASE=f'http://localhost:{ui}', UAT_BATCH_ID=str(batch), PLAYWRIGHT_USE_SYSTEM_CHROME='1',
        UAT_E2E_REPORT=str(ROOT/f'evidence/capacity-projection-{args.environment}-browser-report'),
        UAT_E2E_OUTPUT=str(ROOT/f'evidence/capacity-projection-{args.environment}-browser-output'))
    with (ROOT/f'evidence/capacity-projection-{args.environment}-browser.log').open('w') as log:
        subprocess.run(['./node_modules/.bin/playwright','test',
            '--config=../test-kit/2026-10-16/e2e/playwright.config.mjs','planning-performance.spec.mjs'],
            cwd=ROOT.parents[1]/'frontend', env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
    print(args.environment, 'browser PASS')
