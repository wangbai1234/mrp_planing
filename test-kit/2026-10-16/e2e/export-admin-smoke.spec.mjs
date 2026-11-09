import {test,expect} from '../../../frontend/node_modules/@playwright/test/index.mjs';

test('restarted main backend and export page remain available',async({page,request})=>{
  const response=await request.post(process.env.UAT_API_BASE+'/api/v1/auth/login',{
    data:{username:'admin',password:process.env.UAT_PASSWORD}});
  expect(response.status()).toBe(200);
  const login=(await response.json()).data;
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(login=>{
    localStorage.setItem('mrp_token',login.token);
    localStorage.setItem('mrp_user_info',JSON.stringify(login));
  },login);
  await page.goto('/export');
  await expect(page.getByText('排产导出',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'导出 Excel',exact:true}).click();
  await expect(page.getByText('请先加载排产计划',{exact:true})).toBeVisible();
  const missing=await request.get(process.env.UAT_API_BASE+'/api/v1/export-tasks/9223372036854775807',{
    headers:{Authorization:'Bearer '+login.token}});
  expect(missing.status()).toBe(400);
  expect(errors).toEqual([]);
});
