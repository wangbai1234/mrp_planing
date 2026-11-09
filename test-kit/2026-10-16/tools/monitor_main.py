#!/usr/bin/env python3
"""Bounded observational monitor. Never controls processes or calls protected APIs."""
import argparse,subprocess,time,json,pathlib,urllib.request,datetime
p=argparse.ArgumentParser();p.add_argument('--pid',required=True,type=int);p.add_argument('--seconds',type=int,default=900);p.add_argument('--out',required=True);a=p.parse_args()
if not 1<=a.seconds<=3600:raise SystemExit('seconds must be 1..3600')
out=pathlib.Path(a.out);out.parent.mkdir(parents=True,exist_ok=True);deadline=time.monotonic()+a.seconds
with out.open('w') as f:
 while time.monotonic()<deadline:
  row={'time':datetime.datetime.now().astimezone().isoformat()}
  proc=subprocess.run(['ps','-p',str(a.pid),'-o','pid=,rss=,%cpu=,etime='],capture_output=True,text=True);row['process']=proc.stdout.strip();row['alive']=bool(row['process'])
  begin=time.monotonic()
  try:
   with urllib.request.urlopen('http://127.0.0.1:8080/api/v1/mrp/factories',timeout=3) as r:row['httpStatus']=r.status
  except urllib.error.HTTPError as e:row['httpStatus']=e.code # 401/405 proves HTTP responds, not task success
  except Exception as e:row['httpError']=type(e).__name__
  row['probeMs']=round((time.monotonic()-begin)*1000)
  stats=subprocess.run(['docker','stats','--no-stream','--format','{{json .}}','mrp-mysql','mrp-redis'],capture_output=True,text=True,timeout=15);row['containers']=stats.stdout.splitlines()
  f.write(json.dumps(row)+'\n');f.flush()
  if not row['alive']:raise SystemExit('Observed process exited; inspect application log')
  time.sleep(min(5,max(0,deadline-time.monotonic())))
