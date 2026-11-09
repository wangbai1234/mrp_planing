import {test, expect} from '../../../frontend/node_modules/@playwright/test/index.mjs';

test('capacity review opens and cancels without publishing', async ({page, request}, testInfo) => {
  test.setTimeout(120000);
  const base=process.env.UAT_API_BASE;
  const batch=process.env.UAT_BATCH_ID;
  if(!base || !batch || !process.env.UAT_PASSWORD) throw Error('Explicit environment and batch required');
  const response=await request.post(base+'/api/v1/auth/login',{data:{username:'admin',password:process.env.UAT_PASSWORD}});
  expect(response.ok()).toBeTruthy();
  const login=(await response.json()).data;
  await page.addInitScript(data=>{localStorage.setItem('mrp_token',data.token);localStorage.setItem('mrp_user_info',JSON.stringify(data));},login);
  const writes=[],errors=[];
  // Protect the preserved batch even if a UI regression tries to send a write.
  await page.route('**/api/v1/mrp/**',async route=>{
    if(!['GET','HEAD','OPTIONS'].includes(route.request().method())){
      writes.push({method:route.request().method(),path:new URL(route.request().url()).pathname});
      await route.abort();
    } else await route.continue();
  });
  page.on('pageerror',error=>errors.push(error.message));
  await page.goto('/planning/'+batch);
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0,{timeout:60000});
  const reviewResponse=page.waitForResponse(r=>r.url().endsWith('/batches/'+batch+'/capacity-review'));
  const start=Date.now();
  await page.getByRole('button',{name:'发布排产结果',exact:true}).click();
  const review=await reviewResponse;
  expect(review.status()).toBe(200);
  const data=(await review.json()).data;
  expect(data.fingerprint).toMatch(/^[a-f0-9]{64}$/);
  const warnings=data.loads.filter(row=>row.status!=='NORMAL');
  expect(warnings.length).toBeGreaterThan(0);
  const dialog=page.getByRole('dialog',{name:'发布确认',exact:true});
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('.el-table__body tbody tr')).toHaveCount(warnings.length);
  const confirm=dialog.getByRole('button',{name:'确认',exact:true});
  await expect(confirm).toBeDisabled();
  await dialog.getByText('已了解当前预警，确认按当前计划发布',{exact:true}).click();
  await expect(dialog.getByRole('checkbox')).toBeChecked();
  await expect(confirm).toBeEnabled();
  await dialog.getByRole('button',{name:'取消',exact:true}).click();
  await expect(dialog).toBeHidden();
  expect(writes).toEqual([]);
  expect(errors).toEqual([]);
  await testInfo.attach('capacity-review-preview',{body:JSON.stringify({batch,warningCount:warnings.length,fingerprint:data.fingerprint,ms:Date.now()-start,writes,errors}),contentType:'application/json'});
});
