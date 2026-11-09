import {test,expect} from '../../../frontend/node_modules/@playwright/test/index.mjs';

test('calendar page loads and legacy version reads enforce the logged-in factory scope',async({page,request})=>{
  const limited=Boolean(process.env.UAT_CALENDAR_USER);
  const response=await request.post(process.env.UAT_API_BASE+'/api/v1/auth/login',{
    data:{username:process.env.UAT_CALENDAR_USER||'admin',password:limited?'Mrp@123456':process.env.UAT_PASSWORD}});
  expect(response.status()).toBe(200);
  const login=(await response.json()).data;
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(login=>{
    localStorage.setItem('mrp_token',login.token);
    localStorage.setItem('mrp_user_info',JSON.stringify(login));
  },login);
  const monthResponse=page.waitForResponse(r=>r.url().includes('/api/v1/mrp/production-calendars/month?'));
  await page.goto('/mrp/production-calendars');
  await expect(page.getByRole('heading',{name:'生产日历',exact:true})).toBeVisible();
  expect((await monthResponse).status()).toBe(200);
  expect(await page.locator('.day-cell:not(.padding-cell)').count()).toBeGreaterThanOrEqual(28);
  await expect(page.locator('.el-alert--error')).toHaveCount(0);
  await expect(page.getByRole('button',{name:'导出',exact:true})).toHaveCount(0);
  await page.locator('.filter-bar .el-select').first().click();
  await expect(page.locator('.el-select-dropdown:visible .el-select-dropdown__item'))
    .toHaveText(limited?['爱培科']:['爱培科','永惠']);
  await page.keyboard.press('Escape');
  const read=path=>page.evaluate(async path=>{
    const response=await fetch('/api/v1'+path,{headers:{Authorization:'Bearer '+localStorage.getItem('mrp_token')}});
    return {status:response.status,body:await response.json()};
  },path);
  const root='/mrp/production-calendar-versions';
  const listed=await read(root);
  expect(listed.status).toBe(200);
  expect(listed.body.data.length).toBeGreaterThan(0);
  const choices=await read('/mrp/batch-creation-versions');
  expect(choices.status).toBe(200);
  expect(choices.body.data.productionCalendarVersions.map(v=>v.id)).toEqual(listed.body.data.map(v=>v.id));
  if(limited){
    const own=Number(process.env.UAT_LEGACY_CALENDAR_OWN);
    const foreign=Number(process.env.UAT_LEGACY_CALENDAR_FOREIGN);
    expect(listed.body.data.map(v=>v.id)).toContain(own);
    expect(listed.body.data.map(v=>v.id)).not.toContain(foreign);
    expect((await read(root+'/'+own+'/days')).status).toBe(200);
    expect((await read(root+'/'+foreign)).status).toBe(403);
    expect((await read(root+'/'+foreign+'/days')).status).toBe(403);
  }else{
    const id=listed.body.data[0].id;
    expect((await read(root+'/'+id)).status).toBe(200);
    expect((await read(root+'/'+id+'/days')).status).toBe(200);
  }
  expect(errors).toEqual([]);
});
