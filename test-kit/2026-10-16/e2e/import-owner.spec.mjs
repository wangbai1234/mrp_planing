import {test, expect} from '../../../frontend/node_modules/@playwright/test/index.mjs';
import fs from 'node:fs';

for (const flow of ['forecast', 'inventory']) {
  test('owner can preview and download invalid '+flow+' without confirmation', async ({page,request}) => {
    const base=process.env.UAT_API_BASE;
    const response=await request.post(base+'/api/v1/auth/login',{data:{username:process.env.UAT_USERNAME,password:process.env.UAT_PASSWORD}});
    expect(response.status()).toBe(200);
    const login=(await response.json()).data;
    await page.addInitScript(data=>{localStorage.setItem('mrp_token',data.token);localStorage.setItem('mrp_user_info',JSON.stringify(data));},login);
    const errors=[];page.on('pageerror',error=>errors.push(error.message));
    await page.goto('/'+flow);
    await page.getByRole('button',{name:'导入',exact:true}).click();
    const uploaded=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().includes(flow==='forecast'?'/forecast-imports':'/inventory-imports/items'));
    await page.locator('input[type="file"]').setInputFiles(process.env.UAT_IMPORT_FIXTURE);
    const upload=await uploaded;expect(upload.status()).toBe(202);
    const data=(await upload.json()).data;expect(data.errorRows).toBeGreaterThan(0);
    await expect(page.getByRole('button',{name:'开始导入',exact:true})).toBeDisabled();
    await expect(page.locator('.el-alert').filter({hasText:'无法导入'})).toBeVisible();
    const preview=await request.get(base+'/api/v1/'+(flow==='forecast'?'import-tasks/':'inventory-import-tasks/')+data.taskId,{headers:{Authorization:'Bearer '+login.token}});
    expect(preview.status()).toBe(200);
    const downloadEvent=page.waitForEvent('download');
    await page.getByRole('button',{name:'下载错误数据',exact:true}).click();
    const download=await downloadEvent;expect(await download.failure()).toBeNull();
    const file=fs.readFileSync(await download.path());expect(file.subarray(0,2).toString()).toBe('PK');
    await page.getByRole('button',{name:'取消',exact:true}).click();
    expect(errors).toEqual([]);
  });
}
