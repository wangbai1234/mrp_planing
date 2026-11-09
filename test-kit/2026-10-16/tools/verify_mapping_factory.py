import ast,os,pathlib,requests,io,json,openpyxl
root=pathlib.Path(__file__).resolve().parents[3];p=os.environ['UAT_PASSWORD']
s=requests.Session();base=os.environ.get('UAT_API_BASE','http://127.0.0.1:8180')+'/api/v1'
r=s.post(base+'/auth/login',json={'username':'admin','password':p});r.raise_for_status();s.headers['Authorization']='Bearer '+r.json()['data']['token']
url=base+'/mrp/factory-warehouse-mappings';before=s.get(url).json()['data']
r=s.post(url,json={'factoryCode':'不存在的工厂','warehouseCode':'TEST-INVALID','enabled':True});assert r.status_code==400,r.status_code
bad_create={'status':r.status_code,'message':r.json().get('message')}
template=s.get(url+'/template');template.raise_for_status()
def workbook(factory):
 w=openpyxl.load_workbook(io.BytesIO(template.content));sh=w.active
 if sh.max_row>2:sh.delete_rows(3,sh.max_row-2)
 sh.append([factory,'TEST-FREE-WAREHOUSE','可自由录入仓库','启用']);b=io.BytesIO();w.save(b);return b.getvalue()
files=lambda factory:{'file':('factory.xlsx',workbook(factory),'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')}
r=s.post(url+'/import/preview',files=files('不存在的工厂'));r.raise_for_status();bad_preview=r.json()['data'];assert bad_preview['errorRows']>0,bad_preview
r=s.post(url+'/import',files=files('不存在的工厂'));assert r.status_code==400,r.status_code
r=s.post(url+'/import/preview',files=files('爱培科'));r.raise_for_status();valid_preview=r.json()['data'];assert valid_preview['errorRows']==0,valid_preview
assert before==s.get(url).json()['data'],'mapping changed during rejected writes/preview'
result={'status':'PASS','badCreate':bad_create,'badPreview':bad_preview,'badImportStatus':400,'validFactoryFreeWarehousePreview':valid_preview,'mappingUnchanged':True}
(root/'test-kit/2026-10-16/evidence/mapping-factory-validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print('mapping validation PASS; invalid create/import 400; valid factory preview accepted; mappings unchanged')
