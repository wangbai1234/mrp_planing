import {test,expect} from '../../../frontend/node_modules/@playwright/test/index.mjs';

test('delete a disposable user with factory grants through user management',async({page,request})=>{
  const target=process.env.UAT_DELETE_USERNAME;
  expect(target).toMatch(/^udel_/);
  const response=await request.post(process.env.UAT_API_BASE+'/api/v1/auth/login',{
    data:{username:'admin',password:process.env.UAT_PASSWORD}
  });
  expect(response.ok()).toBeTruthy();const login=(await response.json()).data;
  await page.addInitScript(login=>{
    localStorage.setItem('mrp_token',login.token);localStorage.setItem('mrp_user_info',JSON.stringify(login));
  },login);
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto('/admin/users');
  await page.getByPlaceholder('搜索姓名/账号/部门').fill(target);
  const listing=page.waitForResponse(r=>r.url().includes('/api/v1/users?')&&r.url().includes(target));
  await page.getByPlaceholder('搜索姓名/账号/部门').press('Enter');
  expect((await listing).status()).toBe(200);
  const row=page.locator('.user-list .el-table__body tr').filter({hasText:target});
  await expect(row).toHaveCount(1);
  await row.getByRole('button',{name:'删除',exact:true}).click();
  const deletion=page.waitForResponse(r=>r.request().method()==='DELETE'&&r.url().endsWith('/users/'+process.env.UAT_DELETE_USER_ID));
  await page.getByRole('dialog').getByRole('button',{name:'确定',exact:true}).click();
  const deleted=await deletion;expect(deleted.status()).toBe(200);expect((await deleted.json()).success).toBe(true);
  await expect(page.getByText('删除成功',{exact:true})).toBeVisible();
  await expect(row).toHaveCount(0);expect(errors).toEqual([]);
  console.log(JSON.stringify({targetId:process.env.UAT_DELETE_USER_ID,deleteStatus:deleted.status(),pageErrors:errors}));
});
