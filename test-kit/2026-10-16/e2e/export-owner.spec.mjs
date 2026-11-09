import {test,expect} from '../../../frontend/node_modules/@playwright/test/index.mjs';

test('browser can download its export and cannot read another uploader export',async({page,request})=>{
  const base=process.env.UAT_API_BASE;
  const task=process.env.UAT_EXPORT_TASK;
  const logins=[];
  for(const username of [process.env.UAT_EXPORT_OWNER,process.env.UAT_EXPORT_OTHER]){
    const response=await request.post(base+'/api/v1/auth/login',{data:{username,password:'Mrp@123456'}});
    expect(response.status()).toBe(200);logins.push((await response.json()).data);
  }
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(login=>{
    localStorage.setItem('mrp_token',login.token);localStorage.setItem('mrp_user_info',JSON.stringify(login));
  },logins[0]);
  await page.goto('/export');
  await expect(page.getByText('排产导出',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'导出 Excel',exact:true}).click();
  await expect(page.getByText('请先加载排产计划',{exact:true})).toBeVisible();
  const checks=await page.evaluate(async({task,owner,other})=>{
    const out=[];
    for(const [name,token] of [['owner',owner],['other',other]]){
      for(const suffix of ['', '/download']){
        const response=await fetch('/api/v1/export-tasks/'+task+suffix,{headers:{Authorization:'Bearer '+token}});
        const bytes=new Uint8Array(await response.arrayBuffer());
        out.push({name,suffix,status:response.status,bytes:bytes.length,zip:bytes[0]===80&&bytes[1]===75});
      }
    }
    return out;
  },{task,owner:logins[0].token,other:logins[1].token});
  expect(checks.map(c=>c.status)).toEqual([200,200,403,403]);
  expect(checks[1].zip).toBe(true);expect(checks[1].bytes).toBeGreaterThan(1000);
  expect(errors).toEqual([]);
  console.log(JSON.stringify({checks,pageErrors:errors}));
});
