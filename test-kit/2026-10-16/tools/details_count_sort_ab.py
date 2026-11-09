import sys,pathlib,xml.etree.ElementTree as ET,re,time,json,statistics
sys.path.insert(0,'test-kit/2026-10-16/tools')
from continuation_publish import sql,ROOT
xml=ET.parse('backend/src/main/resources/mapper/mrp/RollingPlanningMapper.xml').getroot()
nodes={n.attrib.get('id'):n for n in xml}
p={'batchId':13,'runId':13,'rolling':True,'startDate':'2026-09-30','endDate':'2027-03-31','factoryCode':None,'rootMaterialCode':None,'terminalMaterialCode':None,'terminalType':None,'offset':59100,'pageSize':100}
def render(n,props=None):
 props=props or {}
 if n.tag=='include':return render(nodes[n.attrib['refid']],props|{x.attrib['name']:x.attrib['value'] for x in n})
 if n.tag=='if':
  name=n.attrib['test'].split()[0]
  if p.get(name) in (None,''):return ''
 if n.tag=='choose':
  for x in n:
   if x.tag=='otherwise' or p.get(x.attrib['test'].split()[0]) not in (None,''):return render(x,props)
  return ''
 result=n.text or ''
 for child in n:
  result+=render(child,props)+(child.tail or '')
 for k,v in props.items():result=result.replace('${'+k+'}',v)
 return re.sub(r'#\{(\w+)\}',lambda m:'NULL' if p[m[1]] is None else str(int(p[m[1]])) if isinstance(p[m[1]],(int,bool)) else "'"+str(p[m[1]])+"'",result)
queries={'scalar_count':render(nodes['countDetails']), 'union_count':'SELECT COUNT(*) FROM ('+render(nodes['effectiveFacts'])+') effective', 'union_page':render(nodes['selectDetails']), 'stored_page':render(nodes['storedFactColumns'])+render(nodes['storedFactSource'])+' ORDER BY '+render(nodes['detailOrder'])+' LIMIT 59100,100'}
results={}
for name,q in queries.items():
 begin=time.monotonic();plan=sql('EXPLAIN ANALYZE '+q+';');results[name]={'explain':plan,'explainMs':round((time.monotonic()-begin)*1000),'samples':[]}
 print(name,'explain ms',results[name]['explainMs'],flush=True)
for cycle in range(5):
 for name in (list(queries) if cycle%2==0 else list(reversed(queries))):
  begin=time.monotonic();rows=sql(queries[name]+';');ms=round((time.monotonic()-begin)*1000);results[name]['samples'].append(ms)
  results[name]['rows']=len(rows)
  if 'count' in name:results[name]['count']=rows
  print(cycle,name,ms,flush=True)
for name,r in results.items():r['medianMs']=statistics.median(r['samples'])
(ROOT/'evidence/details-count-sort-ab.json').write_text(json.dumps({'parameters':p,'results':results},indent=2)+'\n')
assert results['scalar_count']['count']==results['union_count']['count']
assert sql(queries['stored_page']+';')==sql(queries['union_page']+';')
print('PASS count and page equivalence',flush=True)
