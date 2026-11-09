import {test, expect} from '../../../frontend/node_modules/@playwright/test/index.mjs';

test('large planning load and bidirectional scrolling', async ({page, request}, testInfo) => {
  test.setTimeout(180000);
  page.setDefaultTimeout(15000);
  const base = process.env.UAT_API_BASE || 'http://127.0.0.1:8080';
  const batch = process.env.UAT_BATCH_ID || '91';
  const loginResponse = await request.post(base + '/api/v1/auth/login', {data:{username:'admin', password:process.env.UAT_PASSWORD}});
  expect(loginResponse.ok()).toBeTruthy();
  const login = (await loginResponse.json()).data;
  await page.addInitScript(data => {localStorage.setItem('mrp_token',data.token);localStorage.setItem('mrp_user_info',JSON.stringify(data));}, login);
  const timings = [];
  let gridRequests = 0;
  page.on('request', request => { if (request.url().includes('/rolling-grid')) gridRequests++; });
  const initialGrid = page.waitForResponse(response => response.url().includes('/rolling-grid') && response.status() === 200);
  page.on('response', async response => {
    if (!/\/mrp\/batches\/.*(summary|rolling-grid|rolling-details)/.test(response.url())) return;
    await response.finished(); const t = response.request().timing();
    timings.push({path:new URL(response.url()).pathname, ms:Math.round(t.responseEnd),status:response.status()});
  });
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  const start=Date.now();await page.goto('/planning/'+batch);
  await expect(page.locator('.el-table-v2').first()).toBeVisible({timeout:60000});
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0,{timeout:60000});
  const readyMs=Date.now()-start;
  const grid = (await (await initialGrid).json()).data;
  const heading = day => day.slice(5).replace('-', '/');
  const edgeCellChecks=[];
  const assertEdgeValues=async day=>{
    const rows=await page.locator('.el-table-v2__main').evaluate((main,title)=>{
      const headings=[...main.querySelectorAll('.period-heading > div')].map(el=>el.textContent);
      const index=headings.indexOf(title);
      if(index<0) throw Error('missing edge heading '+title);
      return [...main.querySelectorAll('.el-table-v2__row[rowkey]')].map(row=>({key:row.getAttribute('rowkey'),text:row.querySelectorAll('button.period-cell')[index]?.innerText.split('\n')[0].split('/')[0].trim()})).filter(row=>row.text!==undefined);
    },heading(day));
    const scaled=value=>{const [whole,fraction='']=String(value).split('.');return BigInt(whole)*1000000n+BigInt(fraction.padEnd(6,'0'));};
    const expected=new Map(grid.rows.map(row=>[row.rowKey,scaled(row.dayQuantities[day]||'0')]));
    for(const factory of new Set(grid.capacityLoads.map(load=>load.factoryCode))) expected.set('load-'+factory,grid.capacityLoads.filter(load=>load.factoryCode===factory&&load.date===day).reduce((sum,load)=>sum+scaled(load.load),0n));
    expect(rows.length).toBeGreaterThan(5);
    for(const row of rows){expect(expected.has(row.key)).toBeTruthy();expect(scaled(row.text.replaceAll(',','')),day+' '+row.key).toBe(expected.get(row.key));}
    edgeCellChecks.push({day,cells:rows.length});
  };
  const moveTo = async end => {
    await page.evaluate(end => {
      const body=[...document.querySelectorAll('.el-table-v2__main .el-table-v2__body, .el-table-v2__main .el-table-v2__body *')].find(el=>el.scrollWidth>el.clientWidth*5 && el.clientHeight>100);
      if (!body) throw Error('missing scroll body');
      body.scrollLeft=end ? body.scrollWidth-body.clientWidth : 0;
    }, end);
  };
  await moveTo(true);
  await expect(page.locator('.el-table-v2__main .period-heading > div').filter({hasText:new RegExp('^'+heading(grid.calendarDays.at(-1))+'$')})).toBeVisible();
  await expect(page.locator('.el-table-v2__main button.period-cell').first()).toBeVisible();
  await assertEdgeValues(grid.calendarDays.at(-1));
  await moveTo(false);
  await expect(page.locator('.el-table-v2__main .period-heading > div').filter({hasText:new RegExp('^'+heading(grid.calendarDays[0])+'$')})).toBeVisible();
  await assertEdgeValues(grid.calendarDays[0]);
  const scroll = await page.evaluate(async () => {
    const body=[...document.querySelectorAll('.el-table-v2__main .el-table-v2__body, .el-table-v2__main .el-table-v2__body *')].find(el=>el.scrollWidth>el.clientWidth*5 && el.clientHeight>100);
    if (!body) throw Error('missing main table scroll body');
    const counts=()=>({nodes:document.querySelectorAll('.virtual-table *').length,buttons:document.querySelectorAll('button.period-cell').length,cells:document.querySelectorAll('.el-table-v2__cell').length});
    const before=counts(),frames=[],longTasks=[];
    const observer=new PerformanceObserver(list=>longTasks.push(...list.getEntries().map(e=>Math.round(e.duration))));observer.observe({type:'longtask',buffered:false});
    let previous=performance.now();
    for(let frame=0;frame<120;frame++){
      await new Promise(resolve=>requestAnimationFrame(resolve));const now=performance.now();frames.push(now-previous);previous=now;
      const progress=frame<60?frame/59:(119-frame)/59;
      body.scrollLeft=(body.scrollWidth-body.clientWidth)*progress;
      body.scrollTop=(body.scrollHeight-body.clientHeight)*progress;
    }
    await new Promise(resolve=>requestAnimationFrame(resolve));observer.disconnect();
    return {before,after:counts(),framesMs:frames.map(Math.round),longTasksMs:longTasks,scrollWidth:body.scrollWidth,clientWidth:body.clientWidth};
  });
  expect(scroll.scrollWidth).toBeGreaterThan(scroll.clientWidth*5);
  expect(errors).toEqual([]);
  const modeTimes={};
  const requestsBeforeModes=gridRequests;
  for(const name of ['周汇总','月汇总','日排产']){
    const begin=Date.now();await page.getByRole('tab',{name,exact:true}).click();
    await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0,{timeout:60000});
    await expect(page.locator('.el-table-v2').first()).toBeVisible();modeTimes[name]=Date.now()-begin;
  }
  expect(gridRequests).toBe(requestsBeforeModes);
  await page.setViewportSize({width:1440,height:1000});
  await moveTo(true);
  await expect(page.locator('.el-table-v2__main .period-heading > div').filter({hasText:new RegExp('^'+heading(grid.calendarDays.at(-1))+'$')})).toBeVisible();
  await moveTo(false);
  await page.locator('.grid-footer .el-select').click();
  await page.getByRole('option',{name:'100条/页',exact:true}).click();
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0,{timeout:60000});
  const detailResponse=page.waitForResponse(response=>response.url().includes('/rolling-details'));
  await page.locator('.el-table-v2__main button.period-cell:not([disabled])').first().click();
  expect((await detailResponse).status()).toBe(200);
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button',{name:'关闭此对话框',exact:true}).click();
  expect(errors).toEqual([]);
  const result={batch,readyMs,timings,scroll,modeTimes,gridRequests,edgeDates:[grid.calendarDays[0],grid.calendarDays.at(-1)],edgeCellChecks,errors};
  await testInfo.attach('planning-performance',{body:JSON.stringify(result,null,2),contentType:'application/json'});
  console.log('PLANNING_PERFORMANCE '+JSON.stringify(result));
});
