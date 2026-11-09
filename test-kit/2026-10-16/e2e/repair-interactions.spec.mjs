import {test,expect} from '../../../frontend/node_modules/@playwright/test/index.mjs';
const base=process.env.UAT_API_BASE;
test.beforeEach(async({page,request})=>{
 test.skip(base!=='http://127.0.0.1:8180','Isolated repair acceptance only');
 const r=await request.post(base+'/api/v1/auth/login',{data:{username:'admin',password:process.env.UAT_PASSWORD}});
 const j=await r.json();expect(j.success).toBe(true);
 await page.addInitScript(d=>{localStorage.setItem('mrp_token',d.token);localStorage.setItem('mrp_user_info',JSON.stringify(d));},j.data);
});
test('permission groups save search and selected filter',async({page,request})=>{
 const login=await request.post(base+'/api/v1/auth/login',{data:{username:'admin',password:process.env.UAT_PASSWORD}});
 const headers={Authorization:'Bearer '+(await login.json()).data.token};
 const call=async(p,method='GET',data)=>{const r=await request.fetch(base+'/api/v1'+p,{method,headers,data});expect(r.ok()).toBe(true);return (await r.json()).data;};
 const role=await call('/roles','POST',{code:'repair_ui_'+Date.now(),name:'修复浏览器临时角色'});
 try{
  const catalog=await call('/permissions');
  const permissions=Array.isArray(catalog)?catalog:catalog.items;
  await page.goto('/admin/roles/'+role.id+'/permissions');
  await expect(page.getByRole('heading',{name:'权限配置'})).toBeVisible();
  await page.getByRole('button',{name:'全部展开',exact:true}).click();
  for(const p of permissions)await expect(page.locator('.tree-container .children .permission-code').filter({hasText:new RegExp('^'+p.code+'$')})).toHaveCount(1);
  const group=page.locator('.tree-container > .tree-node > .node-row').filter({hasText:'计算配置'}).first();
  await group.locator('.el-checkbox').click();
  await page.getByRole('button',{name:'保存',exact:true}).click();
  await expect(page.getByText('保存成功',{exact:true})).toBeVisible();
  const saved=await call('/roles/'+role.id+'/permissions');expect(saved.length).toBeGreaterThan(0);expect(saved.every(x=>x>0)).toBe(true);
  await page.reload();await page.getByRole('button',{name:'全部展开',exact:true}).click();
  await page.locator('label.el-checkbox').filter({hasText:'只显示已选'}).click();
  await expect(page.getByRole('checkbox',{name:'只显示已选',exact:true})).toBeChecked();
  await expect(page.locator('.tree-container .children .permission-code')).toHaveCount(saved.length);
  await page.getByPlaceholder('搜索权限').fill('NO_MATCH_REPAIR');
  await expect(page.getByText('暂无匹配权限',{exact:true})).toBeVisible();
  expect(await call('/roles/'+role.id+'/permissions')).toEqual(saved);
 }finally{await call('/roles/'+role.id,'DELETE');}
});
test('machine drawer integer transfer preview and decimal rejection',async({page})=>{
 test.setTimeout(90000);
 await page.goto('/planning/'+process.env.UAT_BATCH_ID);
 await expect(page.locator('.cell-load').first()).toBeVisible({timeout:30000});
 await page.locator('.cell-load').first().click();
 await expect(page.getByRole('heading',{name:'整机排产明细',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'联动调整',exact:true}).first().click();
 const dialog=page.getByRole('dialog',{name:'整机与末级联动调整'});
 await expect(dialog).toBeVisible();
 await dialog.locator('.el-date-editor input').fill('2026-11-02');
 await dialog.locator('.el-date-editor input').press('Enter');
 await dialog.getByPlaceholder('正整数',{exact:true}).fill('1');
 await dialog.getByRole('button',{name:'预览明细',exact:true}).click();
 await expect(dialog.getByText('转移数量（整数）',{exact:true})).toBeVisible({timeout:30000});
 const inputs=dialog.locator('input[placeholder="非负整数"]');expect(await inputs.count()).toBeGreaterThan(0);
 for(const input of await inputs.all())expect(await input.inputValue()).toMatch(/^\d+$/);
 await inputs.first().fill('83.5');
 await expect(dialog.getByText('转移量必须为整数，且不能超过当前可转出量。',{exact:true})).toBeVisible();
 await expect(dialog.getByRole('button',{name:'确认',exact:true})).toBeDisabled();
 await dialog.getByRole('button',{name:'取消',exact:true}).click();
});
