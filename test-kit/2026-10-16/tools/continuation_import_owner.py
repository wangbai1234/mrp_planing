"""Isolated live HTTP and browser import ownership checks; formal input data stay unchanged."""
import datetime, io, json, os, pathlib, subprocess, time, urllib.error, urllib.request, uuid, zipfile
from continuation_access import password
from continuation_publish import sql, ROOT
BASE='http://127.0.0.1:8180/api/v1'
results=[]; admin=None; users=[]; role=None

def req(path, method='GET', body=None, auth=None, raw=None, content_type=None):
    headers={'Content-Type':content_type or 'application/json'}
    if auth: headers['Authorization']='Bearer '+auth
    data=raw if raw is not None else (None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(urllib.request.Request(BASE+path,headers=headers,method=method,data=data),timeout=60) as r:
            data=r.read(); return r.status,data
    except urllib.error.HTTPError as e:return e.code,e.read()

def ok(path,method='GET',body=None,auth=None):
    status,raw=req(path,method,body,auth or admin); assert status<300,(path,status)
    data=json.loads(raw);assert data['success'];return data.get('data')

def check(path,want,auth,method='GET'):
    status,raw=req(path,method,auth=auth); results.append({'path':path,'method':method,'expected':want,'status':status})
    assert status==want,(path,status,want)
    if want==200 and path.endswith('/errors'):assert raw[:2]==b'PK'
    return raw

def workbook():
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('[Content_Types].xml','<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>')
        z.writestr('_rels/.rels','<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>')
        z.writestr('xl/workbook.xml','<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="owner" sheetId="1" r:id="rId1"/></sheets></workbook>')
        z.writestr('xl/_rels/workbook.xml.rels','<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>')
        rows=[]
        for row,values in enumerate([[str(uuid.uuid4())],['test only'],['invalid-'+str(i) for i in range(7)]],1):
            cells=''.join(f'<c r="{chr(65+i)}{row}" t="inlineStr"><is><t>{v}</t></is></c>' for i,v in enumerate(values))
            rows.append(f'<row r="{row}">{cells}</row>')
        z.writestr('xl/worksheets/sheet1.xml','<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>'+''.join(rows)+'</sheetData></worksheet>')
    return out.getvalue()

def upload(flow,auth,data):
    endpoint={'forecast':'/forecast-imports','items':'/inventory-imports/items','legacy':'/inventory-imports?snapshotDate=2026-10-16'}[flow]
    boundary='mrpOwner'+uuid.uuid4().hex
    raw=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="ownership.xlsx"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode()+data+f'\r\n--{boundary}--\r\n'.encode()
    status,body=req(endpoint,'POST',auth=auth,raw=raw,content_type='multipart/form-data; boundary='+boundary)
    results.append({'path':endpoint,'method':'POST','status':status,'expected':202}); assert status==202,(endpoint,status)
    parsed=json.loads(body)['data']; assert parsed['errorRows']>0
    return parsed['taskId']

def formal_counts():return sql('SELECT (SELECT COUNT(*) FROM forecast_version),(SELECT COUNT(*) FROM forecast_detail),(SELECT COUNT(*) FROM inventory_snapshot),(SELECT COUNT(*) FROM inventory_item),(SELECT COUNT(*) FROM inventory_detail);')

if __name__=='__main__':
    browser_status=None; before=formal_counts(); stamp=str(time.time_ns()); payload=workbook()
    fixture=pathlib.Path('/tmp/mrp-import-owner-fixture.xlsx'); fixture.write_bytes(payload)
    try:
        admin=ok('/auth/login','POST',{'username':'admin','password':password()})['token']
        role=ok('/roles','POST',{'code':'import_owner_'+stamp,'name':'导入归属临时验收'})
        catalog=ok('/permissions'); ids={p['code']:p['id'] for p in catalog}
        codes=['forecast:view','forecast:import','forecast:import_confirm','inventory:view','inventory:import']
        ok(f"/roles/{role['id']}/permissions",'PUT',{'permissionIds':[ids[c] for c in codes]})
        auths=[]
        for index in range(2):
            username='owner_'+stamp+'_'+str(index)
            user=ok('/users','POST',{'username':username,'displayName':'导入归属验收','roleIds':[role['id']]}); users.append(user)
            auths.append(ok('/auth/login','POST',{'username':username,'password':'Mrp@123456'})['token'])
        a,b=auths
        task_ids={}
        for flow in ['forecast','items','legacy']:
            own=upload(flow,a,payload); assert upload(flow,a,payload)==own
            foreign=upload(flow,b,payload); assert foreign!=own; task_ids[flow]=own
            if flow!='legacy':
                prefix='/import-tasks/' if flow=='forecast' else '/inventory-import-tasks/'
                errors='/forecast-imports/' if flow=='forecast' else '/inventory-imports/'
                check(prefix+str(own),200,a);check(prefix+str(own),403,b)
                check(errors+str(own)+'/errors',200,a);check(errors+str(own)+'/errors',403,b)
            confirm={'forecast':f'/forecast-imports/{own}/confirm','items':f'/inventory-imports/items/{own}/confirm','legacy':f'/inventory-imports/{own}/confirm?snapshotDate=2026-10-16'}[flow]
            check(confirm,403,b,'POST');check(confirm,400,a,'POST')
            assert sql(f'SELECT status FROM import_task WHERE id={own};')==['PENDING']
        for flow,task in task_ids.items():
            for target in ['forecast','items','legacy']:
                if flow==target:continue
                path={'forecast':f'/forecast-imports/{task}/confirm','items':f'/inventory-imports/items/{task}/confirm','legacy':f'/inventory-imports/{task}/confirm?snapshotDate=2026-10-16'}[target]
                check(path,403,a,'POST')
        env=os.environ.copy(); env.update(UAT_API_BASE='http://127.0.0.1:8180',UAT_UI_BASE='http://localhost:5181',UAT_USERNAME='owner_'+stamp+'_0',UAT_PASSWORD='Mrp@123456',UAT_IMPORT_FIXTURE=str(fixture),UAT_E2E_REPORT=str(ROOT/'evidence/import-owner-browser-report'),UAT_E2E_OUTPUT=str(ROOT/'evidence/import-owner-browser-output'))
        with (ROOT/'evidence/import-owner-browser.log').open('w') as log:
            process=subprocess.run(['node','frontend/node_modules/@playwright/test/cli.js','test','-c','test-kit/2026-10-16/e2e/playwright.config.mjs','import-owner.spec.mjs'],cwd=ROOT.parents[1],env=env,stdout=log,stderr=subprocess.STDOUT)
        browser_status=process.returncode; assert browser_status==0,'Browser verification failed; inspect dedicated log'
        assert formal_counts()==before
    finally:
        after=formal_counts()
        for user in users:
            sql(f"DELETE FROM import_task WHERE created_by={user['id']};")
            ok('/users/'+str(user['id']),'DELETE')
        if role:ok('/roles/'+str(role['id']),'DELETE')
        result={'status':'PASS' if browser_status==0 and before==after and all(r['status']==r['expected'] for r in results) else 'FAIL','results':results,'formalCountsBefore':before,'formalCountsAfter':after,'browserExitCode':browser_status,'finishedAt':datetime.datetime.now().astimezone().isoformat()}
        (ROOT/'evidence/import-owner-runtime.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print('PASS',len(results),'live HTTP assertions; browser PASS; formal input tables unchanged')
