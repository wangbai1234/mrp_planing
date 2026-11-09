import {test,expect} from '../../../frontend/node_modules/@playwright/test/index.mjs';

test('current calendars and mappings display only authorized factories',async({page,request})=>{
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
  const factories=limited?['爱培科']:['爱培科','永惠'];
  const monthResponse=page.waitForResponse(r=>r.url().includes('/api/v1/mrp/production-calendars/month?'));
  await page.goto('/mrp/production-calendars');
  await expect(page.getByRole('heading',{name:'生产日历',exact:true})).toBeVisible();
  expect((await monthResponse).status()).toBe(200);
  expect(await page.locator('.day-cell:not(.padding-cell)').count()).toBeGreaterThanOrEqual(28);
  await expect(page.locator('.filter-bar .el-select').first()).toContainText('爱培科');
  await page.locator('.filter-bar .el-select').first().click();
  const options=page.locator('.el-select-dropdown:visible .el-select-dropdown__item');
  await expect(options).toHaveText(factories);
  await page.keyboard.press('Escape');
  await expect(page.locator('.el-alert--error')).toHaveCount(0);
  await expect(page.getByRole('button',{name:'导出',exact:true})).toHaveCount(0);
  const listResponse=page.waitForResponse(r=>r.url().includes('/api/v1/mrp/production-calendars/page?'));
  await page.getByText('列表',{exact:true}).click();
  expect((await listResponse).status()).toBe(200);
  await expect(page.locator('.el-table')).toBeVisible();
  await expect(page.locator('.el-alert--error')).toHaveCount(0);
  await page.goto('/mrp/factory-warehouse-mappings');
  await expect(page.getByRole('heading',{name:'工厂仓库映射',exact:true})).toBeVisible();
  await expect(page.locator('.el-table__body-wrapper tbody tr')).toHaveCount(factories.length);
  const rows=await page.locator('.el-table__body-wrapper tbody tr').allTextContents();
  factories.forEach(factory=>expect(rows.some(row=>row.includes(factory))).toBe(true));
  if(limited)expect(rows.join('')).not.toContain('永惠');
  await page.getByRole('button',{name:'新增',exact:true}).click();
  await page.getByRole('dialog').locator('.el-select').click();
  await expect(page.locator('.el-select-dropdown:visible .el-select-dropdown__item')).toHaveText(factories);
  await page.keyboard.press('Escape');
  await page.getByRole('dialog').getByRole('button',{name:'取消',exact:true}).click();
  expect(errors).toEqual([]);
});
