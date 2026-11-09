import { test, expect } from '../../../frontend/node_modules/@playwright/test/index.mjs';
import fs from 'node:fs';

test('real calculation counts remain observable during transactions, including large output', async ({ page, request }, info) => {
  test.setTimeout(300000);
  const base = 'http://127.0.0.1:8180/api/v1';
  const loginResponse = await request.post(base + '/auth/login', { data: { username: 'admin', password: process.env.UAT_PASSWORD } });
  expect(loginResponse.ok()).toBeTruthy();
  const login = (await loginResponse.json()).data;
  const headers = { Authorization: 'Bearer ' + login.token };
  const api = async (path, method = 'get') => {
    const response = await request[method](base + path, { headers });
    expect(response.ok(), await response.text()).toBeTruthy();
    const body = await response.json(); expect(body.success).toBeTruthy(); return body.data;
  };
  const original = await api('/mrp/batches/current');
  expect(original.id).toBe(13); expect(original.status).toBe('PUBLISHED');
  await page.addInitScript(data => {
    localStorage.setItem('mrp_token', data.token); localStorage.setItem('mrp_user_info', JSON.stringify(data));
  }, login);
  const errors = [], networkErrors = [], cases = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('response', response => { if (response.url().includes('/calculation-task') && response.status() >= 400) networkErrors.push(response.status()); });
  for (const fixture of [{ forecast: 18, inventory: 8, minDaily: 38850 }, { forecast: 16, inventory: 6, minDaily: 100000, confirmMissingStock: true }]) {
    const batch = await api(`/mrp/batches?factoryCode=ALL&forecastVersionId=${fixture.forecast}&inventorySnapshotId=${fixture.inventory}`, 'post');
    const started = Date.now();
    await api(`/mrp/batches/${batch.id}/precheck`, 'post');
    await expect.poll(async () => (await api(`/mrp/batches/${batch.id}`)).status, { timeout: 60000 }).toMatch(/^(READY_TO_CALCULATE|WAITING_DECISION)$/);
    const fixtureDecisions = [];
    if ((await api(`/mrp/batches/${batch.id}`)).status === 'WAITING_DECISION') {
      expect(fixture.confirmMissingStock).toBeTruthy();
      const issues = await api(`/mrp/batches/${batch.id}/issues/page?status=OPEN&page=1&pageSize=100`);
      expect(issues.total).toBe(25);
      for (const issue of issues.items) {
        expect(issue.issueType).toBe('INVENTORY_MISSING');
        await api(`/mrp/batches/issues/${issue.id}/resolve?decisionCode=MANUAL_CONFIRM&expectedRevision=${issue.revision}&reason=${encodeURIComponent('仅隔离库大数据进度回归：缺失库存按零处理，不修改主数据')}`, 'post');
        fixtureDecisions.push({ id: issue.id, type: issue.issueType, scope: issue.scopeKey });
      }
    }
    expect((await api(`/mrp/batches/${batch.id}`)).status).toBe('READY_TO_CALCULATE');
    await page.goto(`/planning/${batch.id}`);
    await page.getByRole('button', { name: '开始 MRP 计算', exact: true }).click();
    const bar = page.locator('.status-bar [data-testid="calculation-progress"]');
    await expect(bar).toBeVisible();
    const stages = [], displayed = [], latencies = [];
    let screenshotTaken = false;
    await expect.poll(async () => {
      const before = performance.now();
      const task = await api(`/mrp/batches/${batch.id}/calculation-task`);
      latencies.push(Math.round(performance.now() - before));
      if (task) stages.push({ at: Date.now() - started, ...task });
      if (await bar.isVisible()) {
        const text = await bar.innerText(); displayed.push(text);
        expect(text).not.toContain('重试中');
        if (!screenshotTaken && /保存.*[（(]\d+\//.test(text)) {
          await page.screenshot({ path: info.outputPath(`actual-progress-${batch.id}.png`) }); screenshotTaken = true;
        }
      }
      return task?.status;
    }, { timeout: 180000, intervals: [250] }).toBe('SUCCEEDED');
    expect((await api(`/mrp/batches/${batch.id}`)).status).toBe('READY');
    await expect(bar).toContainText('100%', { timeout: 15000 });
    await expect(bar).toContainText('计算完成');
    const values = [...new Set(stages.map(stage => stage.progressCurrent))];
    expect(values.filter(value => value > 0 && value < 100).length).toBeGreaterThanOrEqual(2);
    expect(stages.some(stage => /（\d+\/\d+）/.test(stage.phase))).toBeTruthy();
    for (let i = 1; i < stages.length; i++) expect(stages[i].progressCurrent).toBeGreaterThanOrEqual(stages[i-1].progressCurrent);
    expect(displayed.some(text => /[1-9]\d?%/.test(text))).toBeTruthy();
    const summary = await api(`/mrp/batches/${batch.id}/summary`);
    expect(summary.dailyPlanCount).toBeGreaterThanOrEqual(fixture.minDaily);
    const conservation = await api(`/mrp/batches/${batch.id}/plans/conservation`);
    expect(conservation.mismatchCount).toBe(0);
    cases.push({ ...fixture, fixtureDecisions, batch: batch.id, elapsedMs: Date.now() - started, stages, displayed, latencies, summary, conservation });
    fs.writeFileSync(new URL('../evidence/actual-progress-runtime.json', import.meta.url), JSON.stringify({ cases, errors, networkErrors }, null, 2));
    await api(`/mrp/batches/${batch.id}/cancel`, 'post');
  }
  expect((await api('/mrp/batches/current')).id).toBe(original.id);
  expect(errors).toEqual([]); expect(networkErrors).toEqual([]);
});
