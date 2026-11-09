import {test, expect} from '../../../frontend/node_modules/@playwright/test/index.mjs';

// Read-only check of rendered monthly cells, independent of the frontend sum helper.
test('rendered monthly terminal and factory-load values equal actual-date sums', async ({page, request}, testInfo) => {
  test.setTimeout(120000);
  const base = process.env.UAT_API_BASE || 'http://127.0.0.1:8080';
  const batch = process.env.UAT_BATCH_ID;
  if (!batch || !process.env.UAT_PASSWORD) throw Error('Set UAT_BATCH_ID and UAT_PASSWORD');
  const response = await request.post(base + '/api/v1/auth/login', {data: {username: 'admin', password: process.env.UAT_PASSWORD}});
  expect(response.ok()).toBeTruthy();
  const login = (await response.json()).data;
  await page.addInitScript(data => {
    localStorage.setItem('mrp_token', data.token);
    localStorage.setItem('mrp_user_info', JSON.stringify(data));
  }, login);
  await page.goto('/planning/' + batch);
  const tab = page.getByRole('tab', {name: '月汇总', exact: true});
  await expect(tab).toBeEnabled({timeout: 30000});
  await tab.click();
  await expect(page.locator('.el-table-v2').first()).toBeVisible({timeout: 30000});
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0, {timeout: 30000});
  const gridResponse = await request.get(base + '/api/v1/mrp/batches/' + batch + '/rolling-grid?page=1&pageSize=20', {headers: {Authorization: 'Bearer ' + login.token}});
  expect(gridResponse.ok()).toBeTruthy();
  const grid = (await gridResponse.json()).data;
  const months = [...new Set(grid.calendarDays.map(day => day.slice(0, 7)))];
  const scale = value => {
    const [whole, fraction = ''] = String(value).split('.');
    return BigInt(whole) * 1000000n + BigInt(fraction.padEnd(6, '0'));
  };
  const text = value => {
    const whole = String(value / 1000000n).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    const fraction = String(value % 1000000n).padStart(6, '0').replace(/0+$/, '');
    return whole + (fraction ? '.' + fraction : '');
  };
  const expected = new Map(grid.rows.map(row => [row.rowKey, months.map(month => text(Object.entries(row.dayQuantities).filter(([day]) => day.startsWith(month)).reduce((sum, [, qty]) => sum + scale(qty), 0n)))]));
  for (const factory of new Set(grid.capacityLoads.map(row => row.factoryCode))) {
    expected.set('load-' + factory, months.map(month => text(grid.capacityLoads.filter(row => row.factoryCode === factory && row.date.startsWith(month)).reduce((sum, row) => sum + scale(row.load), 0n))));
  }
  const rendered = await page.locator('.el-table-v2__row[rowkey]').evaluateAll(rows => rows.map(row => ({key: row.getAttribute('rowkey'), cells: [...row.querySelectorAll('button.period-cell')].map(button => button.innerText.trim().split('\n')[0])})).filter(row => row.cells.length));
  expect(rendered.length).toBeGreaterThan(5);
  let terminalRows = 0, loadRows = 0;
  for (const row of rendered) {
    expect(expected.has(row.key), 'unknown rendered row ' + row.key).toBeTruthy();
    expect(row.cells, row.key).toEqual(expected.get(row.key));
    if (row.key.startsWith('load-')) loadRows++; else terminalRows++;
  }
  expect(terminalRows).toBeGreaterThan(3);
  expect(loadRows).toBe(2);
  await testInfo.attach('monthly-values', {body: JSON.stringify({batch, months, terminalRows, loadRows, cellsChecked: rendered.length * months.length, rendered}, null, 2), contentType: 'application/json'});
  console.log('MONTHLY_VALUES ' + JSON.stringify({batch, months, terminalRows, loadRows, cellsChecked: rendered.length * months.length}));
});
