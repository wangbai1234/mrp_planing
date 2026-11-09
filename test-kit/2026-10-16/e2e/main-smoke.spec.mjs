import {test,expect} from '../../../frontend/node_modules/@playwright/test/index.mjs';
const apiBase=process.env.UAT_API_BASE||'http://127.0.0.1:8080';
const routes=[['/forecast','经营计划'],['/inventory','库存管理'],['/planning','排产中心'],['/mrp/config','计算配置'],['/mrp/factory-warehouse-mappings','工厂仓库映射'],['/mrp/stock-policies','库存控制策略'],['/mrp/material-policies','物料策略'],['/mrp/business-weeks','业务周期间'],['/mrp/production-calendars','生产日历'],['/versions','版本记录'],['/export','排产导出'],['/admin/users','用户管理'],['/admin/roles','角色管理'],['/admin/audit-logs','操作日志']];
test.beforeEach(async({page,request})=>{
 if(!process.env.UAT_PASSWORD)throw Error('Set UAT_PASSWORD');
 const r=await request.post(apiBase+'/api/v1/auth/login',{data:{username:process.env.UAT_USERNAME||'admin',password:process.env.UAT_PASSWORD}});expect(r.ok()).toBeTruthy();const j=await r.json();expect(j.data.token).toBeTruthy();
 await page.addInitScript(data=>{localStorage.setItem('mrp_token',data.token);localStorage.setItem('mrp_user_info',JSON.stringify(data));},j.data);
});
for(const [route,title] of routes)test('page '+route,async({page})=>{
 const errors=[];const failed=[];page.on('pageerror',e=>errors.push(e.message));page.on('response',r=>{if(r.url().includes('/api/v1/')&&r.status()>=400)failed.push(r.status()+' '+r.url());});
 await page.goto(route);await expect(page.locator(route==='/export'?'.el-card__header':route.startsWith('/admin/')?'h2':'h1.page-title').filter({hasText:title}).first()).toBeVisible();
 await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0,{timeout:20000});
 expect(errors).toEqual([]);expect(failed).toEqual([]);await expect(page.getByText(/加载失败|接口不存在/)).toHaveCount(0);
});
test('rolling full horizon and three tabs',async({page},testInfo)=>{
 test.setTimeout(120000);
 if(!process.env.UAT_BATCH_ID)throw Error('Set UAT_BATCH_ID from successful run manifest');
 const timings=[];const started=new Map();const begin=Date.now();
 page.on('request',r=>{if(r.url().includes('/api/v1/'))started.set(r,Date.now());});
 page.on('response',r=>{if(started.has(r.request()))timings.push({path:new URL(r.url()).pathname,status:r.status(),ms:Date.now()-started.get(r.request())});});
 try {
 await page.goto('/planning/'+process.env.UAT_BATCH_ID);
 for(const label of ['日排产','周汇总','月汇总']){const tab=page.getByRole('tab',{name:label,exact:true});await expect(tab).toBeEnabled({timeout:30000});await tab.click();await expect(page.locator('.el-table-v2').first()).toBeVisible({timeout:30000});await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0,{timeout:30000});timings.push({tab:label,elapsedMs:Date.now()-begin});}
 await expect(page.getByRole('tab')).toHaveCount(3);
 const r=await page.request.get(apiBase+'/api/v1/mrp/batches/'+process.env.UAT_BATCH_ID+'/rolling-grid',{headers:{Authorization:'Bearer '+await page.evaluate(()=>localStorage.getItem('mrp_token'))}});expect(r.ok()).toBeTruthy();const g=(await r.json()).data;
 expect(g.calendarDays[0]).toBe('2026-10-29');expect(g.calendarDays.at(-1)).toBe('2027-04-30');expect(new Set(g.capacityLoads.map(r=>r.factoryCode)).size).toBe(2);expect(g.capacityLoads.some(r=>r.status==='OVERLOAD')).toBeTruthy();
 } finally { await testInfo.attach('request-timings',{body:JSON.stringify(timings,null,2),contentType:'application/json'});console.log('UAT_TIMINGS '+JSON.stringify(timings)); }
});
test('calendar has no export button; capacity route redirects',async({page})=>{await page.goto('/capacity');await expect(page).toHaveURL(/mrp\/production-calendars/);await expect(page.getByRole('button',{name:'导出',exact:true})).toHaveCount(0);});
