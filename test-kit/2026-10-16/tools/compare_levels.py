"""SELECT-only per-path Decimal netting audit; records zero-only extra paths separately."""
import pathlib,json,subprocess,gzip,csv,sys
from decimal import Decimal as D
case,db,bid,run=sys.argv[1:]; assert db in ['main','isolated'];assert bid.isdigit() and run.isdigit()
container='mrp-mysql' if db=='main' else 'mrp-testkit-mysql';schema='mrp' if db=='main' else 'mrp_testkit'
q=f"SELECT JSON_OBJECT('factory',factory_code,'root',root_material_code,'sourceMonth',CAST(source_month AS CHAR),'path',path_text,'material',current_material_code,'gross',CAST(gross_qty AS CHAR),'availableBefore',CAST(inventory_before_qty AS CHAR),'used',CAST(inventory_used_qty AS CHAR),'net',CAST(net_qty AS CHAR)) FROM mrp_requirement_detail WHERE batch_id={bid} AND run_id={run} ORDER BY id;"
r=subprocess.run(['docker','exec','-i',container,'sh','-c',f'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 --raw -u"$MYSQL_USER" -NB {schema}'],input=q,text=True,capture_output=True,check=True)
keys=['factory','root','sourceMonth','path','material'];k=lambda r:tuple(r[x].replace('→','>') if x=='path' else r[x] for x in keys)
actual={k(r):r for r in map(json.loads,r.stdout.splitlines())}
with gzip.open(pathlib.Path('test-kit/2026-10-15/expected')/(case+'-details.csv.gz'),'rt') as f:expected={k(r):r for r in csv.DictReader(f)}
errors=[]
for key,e in expected.items():
 a=actual.get(key)
 if not a:errors.append({'key':key,'error':'missing'});continue
 for field in ['gross','availableBefore','used','net']:
  if abs(D(a[field])-D(e[field]))>D('0.000001'):errors.append({'key':key,'field':field,'expected':e[field],'actual':a[field]})
extras=[a for key,a in actual.items() if key not in expected];nonzero=[a for a in extras if any(D(a[f]) for f in ['gross','used','net'])]
result={'status':'FAIL' if errors or nonzero else 'PASS','case':case,'database':db,'batch':int(bid),'run':int(run),'expectedPaths':len(expected),'actualPaths':len(actual),'mismatches':len(errors),'extraZeroPaths':len(extras)-len(nonzero),'extraNonzeroPaths':len(nonzero),'errors':errors[:50],'extraNonzeroSamples':nonzero[:5]}
pathlib.Path(f'test-kit/2026-10-16/evidence/levels-{db}-{bid}.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print({k:v for k,v in result.items() if k not in ['errors','extraNonzeroSamples']})
