#!/usr/bin/env python3
"""Probe invalid current imports against the isolated API; never confirm successful previews."""
import datetime, hashlib, io, json, pathlib, subprocess, urllib.request, urllib.error
import openpyxl
ROOT=pathlib.Path(__file__).resolve().parents[1]
SEED=ROOT/'evidence/runs/2026-11-02-positive-chain'
OUT=ROOT/('evidence/runs/2026-11-02-import-matrix-'+datetime.datetime.now().strftime('%H%M%S'))
OUT.mkdir(parents=True,exist_ok=True)
BASE='http://127.0.0.1:8182/api/v1'
def request(path,method='GET',data=None,token=None,ctype='application/json'):
 headers={'Content-Type':ctype}
 if token: headers['Authorization']='Bearer '+token
 req=urllib.request.Request(BASE+path,data=data,headers=headers,method=method)
 try:
  with urllib.request.urlopen(req,timeout=60) as r:return r.status,json.loads(r.read())
 except urllib.error.HTTPError as e:return e.code,json.loads(e.read())
def upload(path,blob,name,token):
 bound='----MRPMatrix20261102'
 prefix=("--"+bound+chr(13)+chr(10)+"Content-Disposition: form-data; name="+chr(34)+"file"+chr(34)+"; filename="+chr(34)+name+chr(34)+chr(13)+chr(10)+"Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"+(chr(13)+chr(10))*2)
 body=prefix.encode()+blob+(chr(13)+chr(10)+"--"+bound+"--"+chr(13)+chr(10)).encode()
 return request(path,'POST',body,token,'multipart/form-data; boundary='+bound)
def count(t):
 p=subprocess.run(['docker','exec','-i','mrp-testkit-mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B -u"$MYSQL_USER" mrp_testkit'],input='SELECT COUNT(*) FROM '+t,capture_output=True,text=True,check=True)
 return int(p.stdout.strip())
def fc():
 w=openpyxl.load_workbook(SEED/'forecast-current.xlsx');w.active.delete_rows(4,w.active.max_row);return w
def inv():
 w=openpyxl.load_workbook(SEED/'inventory-current.xlsx');s=w.active;s.delete_rows(4,s.max_row);return w
def change_month(w, col, val):w.active.cell(1,col).value=val
def do_mixed(w):
 s=w.active;s.append([s.cell(3,c).value for c in range(1,8)]);s.cell(4,6).value='bad'
cases=[
 ('F03_date_material',fc,lambda w:setattr(w.active['C3'],'value','2026-11-02'),'/forecast-imports','INVALID_MATERIAL_ID'),
 ('F03_scientific_material',fc,lambda w:setattr(w.active['C3'],'value','1.23E+10'),'/forecast-imports','INVALID_MATERIAL_ID'),
 ('F06_duplicate_month',fc,lambda w:change_month(w,7,'2026-11'),'/forecast-imports','INVALID_MONTH_SEQUENCE'),
 ('F06_reverse_month',fc,lambda w:change_month(w,7,'2026-10'),'/forecast-imports','INVALID_MONTH_SEQUENCE'),
 ('F06_skip_month',fc,lambda w:change_month(w,7,'2027-01'),'/forecast-imports','INVALID_MONTH_SEQUENCE'),
 ('F06_blank_month',fc,lambda w:change_month(w,7,None),'/forecast-imports','INVALID_MONTH'),
 ('F06_bad_month',fc,lambda w:change_month(w,7,'November'),'/forecast-imports','INVALID_MONTH'),
 ('F07_overflow',fc,lambda w:setattr(w.active['F3'],'value','1000000000000'),'/forecast-imports','INVALID_QTY'),
 ('F08_sparse_material',fc,lambda w:setattr(w.active['C3'],'value',None),'/forecast-imports','REQUIRED_FIELD'),
 ('F08_formula_qty',fc,lambda w:setattr(w.active['F3'],'value','=1+2'),'/forecast-imports','INVALID_QTY'),
 ('I02_mixed_invalid',inv,do_mixed,'/inventory-imports/items','INVALID_QTY'),
 ('I02_blank_qty',inv,lambda w:setattr(w.active['F3'],'value',None),'/inventory-imports/items','REQUIRED_FIELD'),
 ('I02_nonnumeric_qty',inv,lambda w:setattr(w.active['F3'],'value','not-a-number'),'/inventory-imports/items','INVALID_QTY'),
 ('I07_sparse_material',inv,lambda w:setattr(w.active['D3'],'value',None),'/inventory-imports/items','REQUIRED_FIELD'),
 ('I07_formula_qty',inv,lambda w:setattr(w.active['F3'],'value','=1+2'),'/inventory-imports/items','INVALID_QTY'),
]
_,a=request('/auth/login','POST',json.dumps({'username':'admin','password':'admin123'}).encode());token=a['data']['token']
tables=('forecast_version','forecast_detail','inventory_snapshot','inventory_item');before={t:count(t) for t in tables}
results=[]
for name,f,mut,path,expected in cases:
 w=f();mut(w);buf=io.BytesIO();w.save(buf);blob=buf.getvalue();fname=name+'-'+OUT.name[-6:]+'.xlsx';(OUT/fname).write_bytes(blob)
 status,res=upload(path,blob,fname,token);d=res.get('data') or {};errors=d.get('errors',[]);codes=[x.get('errorCode') for x in errors]
 task=d.get('taskId');confirm=None
 if task and d.get('errorRows',0)>0:confirm,_=request(path+'/'+str(task)+'/confirm','POST',b'',token)
 original_values=[x.get('originalValue') for x in d.get('errors',[])]
 original_ok=name not in ('F08_formula_qty','I07_formula_qty') or '=1+2' in original_values
 i02_values={'I02_mixed_invalid':'bad','I02_blank_qty':'','I02_nonnumeric_qty':'not-a-number'}
 i02_ok=name not in i02_values or any(x.get('rowNumber')==3 if name!='I02_mixed_invalid' else x.get('rowNumber')==4 for x in errors)
 i02_ok=i02_ok and (name not in i02_values or any(x.get('column')=='inventory_qty' and x.get('originalValue')==i02_values[name] for x in errors))
 ok=status==202 and d.get('errorRows',0)>0 and expected in codes and confirm==400 and original_ok and i02_ok
 results.append({'case':name,'expected':expected,'codes':codes,'originalValues':original_values,
                 'uploadHttp':status,'errorRows':d.get('errorRows'),'taskId':task,
                 'confirmHttp':confirm,'sha256':hashlib.sha256(blob).hexdigest(),
                 'status':'PASS' if ok else 'FAIL'})
 (OUT / "progress.json").write_text(json.dumps(results, ensure_ascii=False, indent=2))
after={t:count(t) for t in tables};allpass=all(x['status']=='PASS' for x in results) and before==after
(OUT / "result.json").write_text(json.dumps({"before": before, "after": after, "status": "PASS" if allpass else "FAIL", "cases": results}, ensure_ascii=False, indent=2))
print(json.dumps({'evidence':str(OUT),'before':before,'after':after,'cases':[(r['case'],r['status'],r['codes']) for r in results],'status':'PASS' if allpass else 'FAIL'},ensure_ascii=False))
