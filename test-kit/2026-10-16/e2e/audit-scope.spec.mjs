import {test,expect} from '../../../frontend/node_modules/@playwright/test/index.mjs';

test('audit page shows authorized rows and count and keeps foreign history private',async({page,request})=>{
  const limited=Boolean(process.env.UAT_AUDIT_USER);
  const response=await request.post(process.env.UAT_API_BASE+'/api/v1/auth/login',{
    data:{username:process.env.UAT_AUDIT_USER||'admin',password:limited?'Mrp@123456':process.env.UAT_PASSWORD}});
  expect(response.status()).toBe(200);
  const login=(await response.json()).data;
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(login=>{
    localStorage.setItem('mrp_token',login.token);
    localStorage.setItem('mrp_user_info',JSON.stringify(login));
  },login);
  const initial=page.waitForResponse(r=>r.url().includes('/api/v1/audit-logs?'));
  await page.goto('/admin/audit-logs');
  await expect(page.getByRole('heading',{name:'操作日志',exact:true})).toBeVisible();
  const initialResponse=await initial;
  expect(initialResponse.status()).toBe(200);
  const data=(await initialResponse.json()).data;
  await expect(page.locator('.el-table')).toBeVisible();
  if(limited){
    const fixtures=page.locator('.el-table__body-wrapper tbody tr').filter({hasText:process.env.UAT_AUDIT_ACTION});
    await expect(fixtures).toHaveCount(5);
    expect(data.items.filter(row=>row.action===process.env.UAT_AUDIT_ACTION)).toHaveLength(5);
    await expect(page.locator('.el-table__body-wrapper tbody tr')).toHaveCount(data.items.length);
    await expect(page.locator('.el-pagination__total')).toContainText(String(data.total));
    const text=(await fixtures.allTextContents()).join('\n');
    expect(text).toContain('爱培科');expect(text).not.toContain('永惠');
    await fixtures.first().getByRole('button',{name:'查看',exact:true}).hover();
    await expect(page.getByRole('tooltip')).toContainText('test');
  }
  expect(errors).toEqual([]);
});
