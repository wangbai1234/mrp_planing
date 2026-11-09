#!/usr/bin/env python3
"""Offline fixture acceptance: workbook contents, references and independent conservation."""
import pathlib,json,gzip,zipfile,xml.etree.ElementTree as ET,csv,collections,unittest
from decimal import Decimal,ROUND_HALF_UP
P=pathlib.Path(__file__).resolve().parents[1];D=P/'data';N={'s':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
def csvrows(case,kind):
 with gzip.open(P/'expected'/f'{case}-{kind}.csv.gz','rt') as f:return list(csv.DictReader(f))
class FixtureAcceptance(unittest.TestCase):
 def test_excel_exact_headers_and_data_dimensions(self):
  specs=json.loads((D/'workbooks.json').read_text());self.assertEqual(len(specs),29)
  for spec in specs:
   with self.subTest(file=spec['name']),zipfile.ZipFile(D/'xlsx'/(spec['name']+'.xlsx')) as z:
    sheet=ET.fromstring(z.read('xl/worksheets/sheet1.xml'));rows=sheet.findall('s:sheetData/s:row',N);self.assertEqual(len(rows),len(spec['rows'])+2)
    shared=[]
    if 'xl/sharedStrings.xml' in z.namelist():shared=[''.join(x.itertext()) for x in ET.fromstring(z.read('xl/sharedStrings.xml'))]
    def val(c):
     v=c.find('s:v',N)
     if c.get('t')=='s':return shared[int(v.text)]
     if c.get('t')=='inlineStr':return ''.join(c.find('s:is',N).itertext())
     return '' if v is None else v.text
    self.assertEqual([val(c) for c in rows[0]],spec['headers'])
    self.assertEqual([val(c) for c in rows[1]],spec['tips'])
    # Real imported identifiers survive without scientific notation / truncation.
    if spec['name'].startswith('forecast-'):
     self.assertEqual(val(rows[2][2]),str(spec['rows'][0][2]))
 def test_all_references_exist_and_policies_stop_only_explicitly(self):
  mats=json.load(gzip.open(D/'source-materials.json.gz','rt'));codes={x['code'] for x in mats if not x['deleted']}
  stats=json.loads((D/'dataset-manifest.json').read_text())['datasets']
  for case in stats:
   cfg=json.loads((D/(case+'.json')).read_text());self.assertTrue(set(cfg['reachableNodes'])<=codes)
   self.assertEqual({r['materialCode'] for r in cfg['materialPolicies']},set(cfg['reachableNodes']))
   self.assertEqual({r['materialCode'] for r in cfg['materialPolicies'] if r['explosionPolicy']=='STOP'},set(cfg['stopNodes']))
   for r in cfg['inventory']:self.assertIn(r['materialCode'],codes)
 def test_daily_weekly_monthly_and_demand_conservation(self):
  weeks=json.loads((D/'business-weeks.json').read_text());stats=json.loads((D/'dataset-manifest.json').read_text())['datasets']
  for case,stat in stats.items():
   with self.subTest(case=case):
    daily=csvrows(case,'daily');self.assertEqual(len(daily),stat['dailyRows']);monthly=collections.defaultdict(Decimal);weekly=collections.defaultdict(Decimal);demand=collections.defaultdict(Decimal)
    for r in daily:
     q=Decimal(r['quantity']);self.assertGreater(q,0);dim=(r['factory'],r['root'],r['material'],r['type']);monthly[dim+(r['date'][:7],)]+=q
     w=next(w for w in weeks if w['startDate']<=r['date']<=w['endDate']);weekly[dim+(w['businessMonth'][:7]+'-'+w['weekCode'],)]+=q
     demand[(r['factory'],r['root'],r['sourceMonth'],r['material'],r['type'])]+=q
     if r['type']=='MACHINE_OUTPUT':self.assertEqual(q,q.to_integral_value())
    for kind,actual,last in [('monthly',monthly,'actualMonth'),('weekly',weekly,'businessWeek')]:
     expected={tuple(r[k] for k in ['factory','root','material','type',last]):Decimal(r['quantity']) for r in csvrows(case,kind)};self.assertEqual(dict(actual),expected)
    wanted={tuple(r[k] for k in ['factory','root','sourceMonth','material','type']):Decimal(r['quantity']).quantize(Decimal('0.000001'),rounding=ROUND_HALF_UP) for r in csvrows(case,'demand')};self.assertEqual(dict(demand),wanted)
 def test_daily_calendar_and_stock_formula(self):
  cal=json.loads((D/'production-calendars.json').read_text());self.assertEqual(len(cal),424)
  self.assertTrue(all(Decimal(r['dailyCapacity'])==Decimal(r['dailyCapacity']).to_integral_value() for r in cal))
  details=csvrows('accuracy-stock','details');r=next(r for r in details if r['root']=='6839AA000359' and r['material']==r['root'] and r['sourceMonth']=='2026-11-01')
  self.assertEqual([Decimal(r[k]) for k in ['gross','availableBefore','used','net']],[Decimal(1001),Decimal(501),Decimal(501),Decimal(500)])
  for case in json.loads((D/'dataset-manifest.json').read_text())['datasets']:
   for r in csvrows(case,'details'):self.assertEqual(Decimal(r['gross'])-Decimal(r['used']),Decimal(r['net']))
if __name__=='__main__':unittest.main(verbosity=2)
