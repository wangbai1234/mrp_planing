import {test,expect} from '../../../frontend/node_modules/@playwright/test/index.mjs';

test('inventory snapshot list and detail use real authorized API',async({page,request})=>{
  const scoped=Boolean(process.env.UAT_SCOPE_USERNAME);
  const response=await request.post(process.env.UAT_API_BASE+'/api/v1/auth/login',{
    data:{username:process.env.UAT_SCOPE_USERNAME||'admin',password:scoped?'Mrp@123456':process.env.UAT_PASSWORD}
  });
  expect(response.ok()).toBeTruthy();const login=(await response.json()).data;
  await page.addInitScript(login=>{
    localStorage.setItem('mrp_token',login.token);localStorage.setItem('mrp_user_info',JSON.stringify(login));
  },login);
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  const listingPromise=page.waitForResponse(r=>r.url().includes('/inventory-snapshots/page'));
  const detailPromise=page.waitForResponse(r=>/\/inventory-snapshots\/\d+\?/.test(r.url()));
  await page.goto('/inventory');
  const listing=await listingPromise;expect(listing.status()).toBe(200);
  const listData=(await listing.json()).data;expect(listData.total).toBeGreaterThan(0);
  const detail=await detailPromise;expect(detail.status()).toBe(200);
  const detailData=(await detail.json()).data;expect(detailData.items.length).toBeGreaterThan(0);
  await expect(page.locator('.table-title-text')).toContainText('#'+listData.items[0].id);
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0);
  await expect(page.locator('.inventory-page .table-card .el-table__body tr').first()).toContainText(detailData.items[0].materialId);
  await page.getByRole('button',{name:'历史版本',exact:true}).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  if(scoped){
    expect(listData.total).toBe(1);expect(listData.items[0].id).toBe(Number(process.env.UAT_SCOPE_OWN));
    await expect(page.getByRole('dialog').locator('.el-table__body tr')).toHaveCount(1);
    const forbidden=await request.get(process.env.UAT_API_BASE+'/api/v1/inventory-snapshots/'+process.env.UAT_SCOPE_FOREIGN,{headers:{Authorization:'Bearer '+login.token}});
    expect(forbidden.status()).toBe(403);
    const next=page.waitForResponse(r=>r.url().includes('/inventory-snapshots/page'));
    await page.getByPlaceholder('搜索ID / 日期 / 状态').fill(process.env.UAT_SCOPE_MARKER);
    await page.getByPlaceholder('搜索ID / 日期 / 状态').press('Enter');
    expect((await next).status()).toBe(200);
    await expect(page.getByRole('dialog').locator('.el-table__body tr')).toHaveCount(1);
  }
  expect(errors).toEqual([]);
  console.log(JSON.stringify({scoped,total:listData.total,detailRows:detailData.items.length,pageErrors:errors}));
});
