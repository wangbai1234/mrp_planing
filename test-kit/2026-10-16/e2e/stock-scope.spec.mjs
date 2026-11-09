import {test,expect} from '../../../frontend/node_modules/@playwright/test/index.mjs';

test('stock policy list, warehouse picker and audit respect factory scope',async({page,request})=>{
  const limited=Boolean(process.env.UAT_STOCK_USER);
  const response=await request.post(process.env.UAT_API_BASE+'/api/v1/auth/login',{
    data:{username:process.env.UAT_STOCK_USER||'admin',password:limited?'Mrp@123456':process.env.UAT_PASSWORD}});
  expect(response.status()).toBe(200);
  const login=(await response.json()).data;
  const errors=[]; page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(login=>{
    localStorage.setItem('mrp_token',login.token);
    localStorage.setItem('mrp_user_info',JSON.stringify(login));
  },login);
  const loaded=page.waitForResponse(r=>r.url().includes('/api/v1/mrp/stock-policies/page?'));
  await page.goto('/mrp/stock-policies');
  await expect(page.getByRole('heading',{name:'库存控制策略',exact:true})).toBeVisible();
  expect((await loaded).status()).toBe(200);
  if(limited){
    await page.getByPlaceholder('搜索仓库或物料',{exact:true}).fill(process.env.UAT_STOCK_MATERIAL);
    const searched=page.waitForResponse(r=>r.url().includes('/api/v1/mrp/stock-policies/page?')&&r.url().includes(process.env.UAT_STOCK_MATERIAL));
    await page.getByRole('button',{name:'搜索',exact:true}).click();
    const data=(await (await searched).json()).data;
    expect(data.total).toBe(1);
    expect(data.items[0].warehouseCode).toBe(process.env.UAT_STOCK_OWN);
    const rows=page.locator('.el-table__body-wrapper tbody tr');
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText(process.env.UAT_STOCK_OWN);
    await expect(rows.first()).not.toContainText(process.env.UAT_STOCK_FOREIGN);
    await page.locator('.filter-bar .el-select').click();
    await expect(page.locator('.el-select-dropdown:visible .el-select-dropdown__item')).toHaveCount(1);
    await page.keyboard.press('Escape');
    await page.getByRole('button',{name:'新增',exact:true}).click();
    await page.getByRole('dialog').locator('.el-select').first().click();
    const options=page.locator('.el-select-dropdown:visible .el-select-dropdown__item');
    await expect(options).toHaveCount(1);
    await expect(options.first()).toContainText(process.env.UAT_STOCK_OWN);
    await page.keyboard.press('Escape');
    await page.getByRole('dialog').getByRole('button',{name:'取消',exact:true}).click();
    const logged=page.waitForResponse(r=>r.url().includes('/api/v1/audit-logs?'));
    await page.getByRole('button',{name:'查看日志',exact:true}).click();
    expect((await logged).status()).toBe(200);
    await expect(page.getByRole('dialog')).toContainText('导入');
  }
  await expect(page.locator('.el-alert--error')).toHaveCount(0);
  expect(errors).toEqual([]);
});
