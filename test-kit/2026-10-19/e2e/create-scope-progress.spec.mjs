import { test, expect } from '../../../frontend/node_modules/@playwright/test/index.mjs';
import fs from 'node:fs';

// Real isolated MySQL/API, with existing forecast 18 and unified inventory 8.
// Never publish or modify master data; retain cancelled calculations as evidence.
test('one inventory version, three factory scopes and real progress', async ({ page, request }, info) => {
  test.setTimeout(240000);
  const base = 'http://127.0.0.1:8180';
  const loginResponse = await request.post(base + '/api/v1/auth/login', { data: { username: 'admin', password: process.env.UAT_PASSWORD } });
  expect(loginResponse.ok()).toBeTruthy();
  const login = (await loginResponse.json()).data;
  const headers = { Authorization: 'Bearer ' + login.token };
  const api = async (path, method = 'get') => {
    const response = await request[method](base + '/api/v1' + path, { headers });
    const body = await response.json();
    expect(response.ok(), JSON.stringify(body)).toBeTruthy();
    expect(body.success).toBeTruthy();
    return body.data;
  };
  const original = await api('/mrp/batches/current');
  expect(original.id).toBe(13); expect(original.status).toBe('PUBLISHED');
  const rejected = await request.post(base + '/api/v1/mrp/batches?factoryCode=ALL&forecastVersionId=18&inventorySnapshotId=8&inventorySnapshotIds=7,8', { headers });
  expect(rejected.status()).toBe(400);
  expect((await rejected.json()).error.code).toBe('SINGLE_INVENTORY_VERSION_REQUIRED');
  await page.addInitScript(data => {
    localStorage.setItem('mrp_token', data.token);
    localStorage.setItem('mrp_user_info', JSON.stringify(data));
  }, login);
  const errors = [], results = [];
  page.on('pageerror', e => errors.push(e.message));
  for (const factory of ['爱培科', '永惠', 'ALL']) {
    await page.goto('/planning');
    await page.getByRole('button', { name: '生成排产', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: '生成排产', exact: true });
    await expect(dialog.locator('.el-form-item')).toHaveCount(3);
    await expect(dialog.locator('.el-loading-mask:visible')).toHaveCount(0);
    const fields = dialog.locator('.el-form-item');
    await fields.nth(0).locator('.el-select').click();
    await expect(page.getByRole('option')).toHaveText(['全部', '爱培科', '永惠']);
    await page.getByRole('option', { name: factory === 'ALL' ? '全部' : factory, exact: true }).click();
    await fields.nth(1).locator('.el-select').click();
    await page.getByRole('option', { name: /^V5 ·/ }).click();
    await fields.nth(2).locator('.el-select').click();
    await page.getByRole('option', { name: /^#8 ·/ }).click();
    await expect(dialog.getByRole('button', { name: '确认', exact: true })).toBeEnabled();
    await page.screenshot({ path: info.outputPath('create-' + factory + '.png') });
    const creation = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/v1/mrp/batches');
    await dialog.getByRole('button', { name: '确认', exact: true }).click();
    const response = await creation;
    expect(response.status()).toBe(200);
    const params = Object.fromEntries(new URL(response.url()).searchParams);
    expect(params).toEqual({ factoryCode: factory, forecastVersionId: '18', inventorySnapshotId: '8' });
    const batch = (await response.json()).data;
    const bar = page.locator('.status-bar [data-testid="calculation-progress"]');
    await expect(bar).toBeVisible({ timeout: 30000 });
    await expect(bar.locator('.el-progress')).toBeVisible();
    await expect(bar).toContainText(/检查|计算|排产|等待/);
    await page.screenshot({ path: info.outputPath('progress-' + factory + '.png') });
    const stages = [];
    await expect.poll(async () => {
      const current = await api('/mrp/batches/' + batch.id);
      const task = await api('/mrp/batches/' + batch.id + '/calculation-task');
      stages.push({ batchStatus: current.status, progress: task && { status: task.status, current: task.progressCurrent, total: task.progressTotal, phase: task.phase } });
      expect(['FAILED', 'WAITING_DECISION']).not.toContain(current.status);
      return current.status;
    }, { timeout: 60000, intervals: [250, 500, 1000] }).toBe('READY');
    await expect(bar).toContainText('100%', { timeout: 15000 });
    await expect(bar).toContainText('计算完成');
    const conservation = await api('/mrp/batches/' + batch.id + '/plans/conservation');
    expect(conservation.mismatchCount).toBe(0);
    for (const expectedFactory of factory === 'ALL' ? ['爱培科', '永惠'] : [factory]) {
      const grid = await api('/mrp/batches/' + batch.id + '/rolling-grid?page=1&pageSize=20&factoryCode=' + encodeURIComponent(expectedFactory));
      expect(grid.totalRows).toBeGreaterThan(0);
      expect(grid.rows.length).toBeGreaterThan(0);
      expect(grid.rows.every(row => row.factoryCode === expectedFactory)).toBeTruthy();
    }
    results.push({ factory, batch: batch.id, params, bomSnapshotId: batch.bomSnapshotId, categoryRoleVersionId: batch.categoryRoleVersionId, stages, conservation });
    await api('/mrp/batches/' + batch.id + '/cancel', 'post');
  }
  expect((await api('/mrp/batches/current')).id).toBe(original.id);
  expect(errors).toEqual([]);
  fs.writeFileSync(new URL('../evidence/create-scope-progress-runtime.json', import.meta.url), JSON.stringify({ status: 'PASS', originalBatch: original.id, results, errors }, null, 2));
});
