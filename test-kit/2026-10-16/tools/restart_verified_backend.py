"""Restart an already configured local backend without exposing private launch settings."""
import argparse,json,pathlib,subprocess,psutil
ROOT=pathlib.Path(__file__).resolve().parents[3]
parser=argparse.ArgumentParser();parser.add_argument('environment',choices=['main','isolated']);args=parser.parse_args()
main=args.environment=='main';port=8080 if main else 8180
private=pathlib.Path('/tmp/mrp-repair-main-restart-private.json' if main else '/tmp/mrp-perf-isolated-restart-private.json')
log=pathlib.Path('/tmp/mrp-repair-main-runtime.log' if main else '/tmp/mrp-perf-isolated-runtime.log')
found=subprocess.run(['lsof','-t',f'-iTCP:{port}','-sTCP:LISTEN'],capture_output=True,text=True)
for value in set(found.stdout.split()):
    proc=psutil.Process(int(value))
    assert any('com.mrp.Mrp' in x for x in proc.cmdline()),'Unexpected port owner'
    parent=proc.parent();targets=[proc]
    if parent and parent.name()=='java':targets.insert(0,parent)
    for target in targets:target.terminate()
    _,alive=psutil.wait_procs(targets,timeout=10)
    assert not alive,'Backend did not terminate'
settings=json.loads(private.read_text())
with log.open('a') as output:
    launcher=subprocess.Popen(settings['args'],env=settings['env'],cwd=ROOT/'backend',stdout=output,stderr=subprocess.STDOUT,start_new_session=True)
print('Restarted',args.environment,'launcher',launcher.pid,'port',port)
