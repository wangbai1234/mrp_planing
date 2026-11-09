#!/usr/bin/env python3
"""Compare every HTTP daily fact to independent Decimal oracle. Nonzero on any mismatch."""
import csv,gzip,json,collections,pathlib,sys
from decimal import Decimal
P=pathlib.Path(__file__).resolve().parents[1]
case=sys.argv[1];folder=pathlib.Path(sys.argv[2]);actual=collections.defaultdict(Decimal);expected=collections.defaultdict(Decimal)
cfg=json.loads((P/'data'/(case+'.json')).read_text())
materials={r['code']:r for r in json.load(gzip.open(P/'data/source-materials.json.gz','rt'))}
roles={r['categoryCode']:r['role'] for r in json.loads((P/'data/category-roles.json').read_text())}
policies={r['materialCode']:r['supplyMode'] for r in cfg['materialPolicies']}
dimension_errors=[]
for r in json.loads((folder/(sys.argv[3] if len(sys.argv)>3 else 'facts.json')).read_text()):
 typ='MACHINE_OUTPUT' if r['terminalType']=='MACHINE_OUTPUT' else 'TERMINAL'
 key=(r['factoryCode'],r['rootMaterialCode'],r['sourceMonth'],r['terminalMaterialCode'],typ,r['scheduleDate'])
 wanted_type='MACHINE_OUTPUT' if typ=='MACHINE_OUTPUT' else roles[materials[r['terminalMaterialCode']]['category']]
 if r['terminalType']!=wanted_type or r['supplyMode']!=policies[r['terminalMaterialCode']] or r['sourceDate']!=r['scheduleDate']:
  dimension_errors.append({'key':key,'expectedType':wanted_type,'actualType':r['terminalType'],'supply':r['supplyMode'],'sourceDate':r['sourceDate']})
 actual[key]+=Decimal(r['effectiveQuantity'])
with gzip.open(P/'expected'/f'{case}-daily.csv.gz','rt') as f:
 for r in csv.DictReader(f):expected[tuple(r[k] for k in ['factory','root','sourceMonth','material','type','date'])]+=Decimal(r['quantity'])
actual_count=len(actual);expected_count=len(expected)
errors=[{'key':k,'expected':str(expected.get(k,Decimal(0))),'actual':str(actual.get(k,Decimal(0)))} for k in sorted(expected.keys()|actual.keys()) if expected.get(k,Decimal(0))!=actual.get(k,Decimal(0))]
result={'status':'FAIL' if errors or dimension_errors else 'PASS','expectedKeys':expected_count,'actualKeys':actual_count,'mismatches':len(errors),'dimensionErrors':len(dimension_errors),'checkedDimensions':['factory','root','sourceMonth','material','machineOrTerminal','scheduleDate','quantity','terminalRole','supplyMode','sourceDateEqualsScheduleDate'],'limitation':'Aggregated daily quantity and fixture dimensions only; per-level inventory/BOM path trace requires separate verification.','firstMismatches':errors[:100],'firstDimensionErrors':dimension_errors[:100]}
(folder/(sys.argv[4] if len(sys.argv)>4 else 'oracle-comparison.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items() if k not in ['firstMismatches','firstDimensionErrors']}));sys.exit(bool(errors or dimension_errors))
