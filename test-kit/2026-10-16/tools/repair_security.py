"""Isolated authorization regression; temporary identity only, no administrator grant writes."""
import json,os,pathlib,time,urllib.request,urllib.error
base='http://127.0.0.1:8180/api/v1';token=None;results=[]
def req(path,method='GET',body=None,auth=None,raw=None):
 headers={'Content-Type':'application/json'}
 if auth or token:headers['Authorization']='Bearer '+(auth or token)
 data=raw if raw is not None else None if body is None else json.dumps(body).encode()
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,data=data,headers=headers,method=method),timeout=60) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:return e.code,json.load(e)
def ok(path,method='GET',body=None,auth=None):
 status,j=req(path,method,body,auth);assert status<300 and j['success'],(path,status);return j.get('data')
def check(label,actual,want):
 results.append({'id':label,'status':'PASS' if actual==want else 'FAIL','actual':actual,'expected':want});assert actual==want,label
token=ok('/auth/login','POST',{'username':'admin','password':os.environ['UAT_PASSWORD']})['token']
stamp=str(int(time.time()*1000));role=ok('/roles','POST',{'code':'repair_auth_'+stamp,'name':'修复权限临时角色'});uid=None
try:
 catalog=ok('/permissions');catalog=catalog if isinstance(catalog,list) else catalog['items'];ids={p['code']:p['id'] for p in catalog}
 grants=[ids['user:view'],ids['forecast:view']]
 ok(f"/roles/{role['id']}/permissions",'PUT',{'permissionIds':grants})
 user=ok('/users','POST',{'username':'repair_auth_'+stamp,'displayName':'临时只读用户','roleIds':[role['id']]});uid=user['id'];assert 'passwordHash' not in user
 session=ok('/auth/login','POST',{'username':'repair_auth_'+stamp,'password':'Mrp@123456'})['token']
 for path in ['/users/1','/forecast-versions/1']:check('granted detail '+path,req(path,auth=session)[0],200)
 check('read-only write rejected',req('/users','POST',{'username':'forbidden'},session)[0],403)
 ok(f"/roles/{role['id']}/permissions",'PUT',{'permissionIds':[]})
 for path in ['/users/1','/forecast-versions/1']:check('revoked old session '+path,req(path,auth=session)[0],403)
 check('malformed JSON',req('/mrp/production-calendars','POST',raw=b'{')[0],400)
 check('method not allowed',req('/forecast-versions','PUT',{})[0],405)
finally:
 if uid:ok('/users/'+str(uid),'DELETE')
 ok('/roles/'+str(role['id']),'DELETE')
 pathlib.Path('test-kit/2026-10-16/evidence/repair/security-extra.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
print('PASS',len(results),'security checks')
