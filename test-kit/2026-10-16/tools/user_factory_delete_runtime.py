"""Reproduce scoped-user deletion and verify the fix using isolated temporary accounts."""
import argparse, json, os, subprocess, time
import continuation_access as api
from continuation_publish import sql, ROOT

def run(phase):
    api.token=api.ok('/auth/login','POST',{'username':'admin','password':api.password()})['token']
    marker='udel_'+hex(time.time_ns())[2:]
    users=[];role=None;checks=[];passed=False
    def create(suffix):
        user=api.ok('/users','POST',{'username':marker+suffix,'displayName':marker+suffix,'roleIds':[role['id']]})
        users.append(user)
        sql(f"INSERT INTO user_factory_scope(user_id,factory_code) VALUES({user['id']},'爱培科'),({user['id']},'永惠');")
        return user
    def state(user):
        uid=user['id']
        return sql(f"SELECT (SELECT COUNT(*) FROM user WHERE id={uid}),(SELECT COUNT(*) FROM user_factory_scope WHERE user_id={uid}),(SELECT COUNT(*) FROM user_role WHERE user_id={uid}),(SELECT COUNT(*) FROM user_permission WHERE user_id={uid});")
    try:
        role=api.ok('/roles','POST',{'code':marker,'name':'工厂授权用户删除验收'})
        target,other=create('_target'),create('_other')
        catalog=api.ok('/permissions');catalog=catalog if isinstance(catalog,list) else catalog['items']
        permission=next(p['id'] for p in catalog if p['code']=='user:view')
        api.ok(f"/roles/{role['id']}/permissions",'PUT',{'permissionIds':[permission]})
        auth=api.ok('/auth/login','POST',{'username':other['username'],'password':'Mrp@123456'})['token']
        before=state(target);other_before=state(other)
        status,_=api.req('/users/'+str(target['id']),'DELETE',auth=auth)
        checks.append({'check':'without delete permission','status':status,'expected':403})
        assert status==403 and state(target)==before
        api.ok(f"/users/{target['id']}/permissions",'PUT',{'permissions':[{'permissionId':permission,'effect':'ALLOW'}]})
        before=state(target)
        status,data=api.req('/users/'+str(target['id']),'DELETE')
        expected=500 if phase=='before' else 200
        checks.append({'check':'admin deletes scoped user','status':status,'expected':expected})
        assert status==expected
        assert state(other)==other_before,'Another user was changed'
        if phase=='before':
            assert state(target)==before,'Failed transaction left partial deletion'
        else:
            assert data['success'] and state(target)==['0\t0\t0\t0']
            assert sql(f"SELECT COUNT(*) FROM audit_log WHERE action='DELETE' AND resource_type='USER' AND resource_id='{target['id']}';")==['1']
            browser=create('_browser')
            env=os.environ.copy();env.update({'UAT_API_BASE':'http://127.0.0.1:8180','UAT_UI_BASE':'http://localhost:5181',
                'UAT_PASSWORD':api.password(),'UAT_DELETE_USERNAME':browser['username'],'UAT_DELETE_USER_ID':str(browser['id']),
                'PLAYWRIGHT_USE_SYSTEM_CHROME':'1','UAT_E2E_REPORT':str(ROOT/'evidence/user-factory-delete-browser-report'),
                'UAT_E2E_OUTPUT':str(ROOT/'evidence/user-factory-delete-browser-results')})
            with (ROOT/'evidence/user-factory-delete-browser.log').open('w') as output:
                subprocess.run(['npx','playwright','test','-c',str(ROOT/'e2e/playwright.config.mjs'),'user-factory-delete.spec.mjs'],cwd=ROOT.parents[1]/'frontend',env=env,stdout=output,stderr=subprocess.STDOUT,check=True)
            assert state(browser)==['0\t0\t0\t0'] and state(other)==other_before
        passed=True
    finally:
        for user in users:
            if sql(f"SELECT COUNT(*) FROM user WHERE id={user['id']};")==['1']:
                if phase=='before':sql(f"DELETE FROM user_factory_scope WHERE user_id={user['id']};")
                api.ok('/users/'+str(user['id']),'DELETE')
        if role:api.ok('/roles/'+str(role['id']),'DELETE')
        remaining=sql(f"SELECT COUNT(*) FROM user WHERE username LIKE '{marker}%';")
        (ROOT/f'evidence/user-factory-delete-{phase}-runtime.json').write_text(json.dumps({'status':'PASS' if passed else 'FAIL','checks':checks,'temporaryUserIds':[u['id'] for u in users],'remainingUsers':remaining},ensure_ascii=False,indent=2)+'\n')
    print('PASS',phase,len(checks),'HTTP checks; scoped temporary users cleaned')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['before','after']);run(parser.parse_args().phase)
