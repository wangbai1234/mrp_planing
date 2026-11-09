import { expect, test } from '@playwright/test'

const api = 'http://127.0.0.1:8182/api/v1'

async function login(page: import('@playwright/test').Page) {
  await page.goto('/login')
  await page.getByPlaceholder('邮箱').fill('admin')
  await page.getByPlaceholder('密码').fill(process.env.E2E_ADMIN_PASSWORD || '')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/planning$/)
}

test('I06: incomplete and out-of-scope inventory versions explain why batch creation is blocked', async ({ page, request }, testInfo) => {
  test.setTimeout(90_000)
  const loginResponse = await request.post(`${api}/auth/login`, {
    data: { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD }
  })
  expect(loginResponse.status()).toBe(200)
  const token = (await loginResponse.json()).data.token as string
  const headers = { Authorization: `Bearer ${token}` }
  const before = await request.get(`${api}/mrp/batches?page=1&pageSize=1`, { headers })
  expect(before.status()).toBe(200)

  await login(page)
  await page.getByRole('button', { name: '生成排产' }).click()
  const dialog = page.getByRole('dialog', { name: '生成排产' })
  await expect(dialog.getByText('库存版本', { exact: true })).toBeVisible()
  const inventorySelect = dialog.locator('.el-form-item').nth(2).locator('.el-select')
  await inventorySelect.click()
  await page.getByRole('option', { name: /#56\b/ }).click()
  await expect(dialog.getByText(/所选库存版本缺少对应仓库数据/)).toBeVisible()
  await expect(dialog.getByRole('button', { name: '确认', exact: true })).toBeDisabled()

  await inventorySelect.click()
  await page.getByRole('option', { name: /#59\b/ }).click()
  const rejected = page.waitForResponse(response => response.url().includes('/mrp/batches?') && response.request().method() === 'POST')
  await dialog.getByRole('button', { name: '确认', exact: true }).click()
  const response = await rejected
  expect(response.status()).toBe(400)
  expect((await response.json()).error.code).toBe('INVENTORY_WAREHOUSE_MISMATCH')
  await expect(page.getByText(/所选库存快照包含未启用或未映射的仓库/)).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('i06-inventory-scope.png'), fullPage: true })
  const after = await request.get(`${api}/mrp/batches?page=1&pageSize=1`, { headers })
  expect(after.status()).toBe(200)
  expect((await after.json()).data.total).toBe((await before.json()).data.total)
})

test('F12/I10: version history pagination, search, empty result and recovery', async ({ page }, testInfo) => {
  test.setTimeout(90_000)
  await login(page)
  for (const item of [
    { route: '/forecast', button: '查看版本', dialog: '版本记录', query: '/forecast-versions?', search: '搜索版本', empty: '暂无历史版本' },
    { route: '/inventory', button: '历史版本', dialog: '历史版本', query: '/inventory-snapshots/page?', search: '搜索ID / 日期 / 状态', empty: '暂无历史版本' }
  ]) {
    await page.goto(item.route)
    await expect(page.locator('.table-card .el-loading-mask')).toHaveCount(0)
    await page.getByRole('button', { name: item.button, exact: true }).click()
    const dialog = page.getByRole('dialog', { name: item.dialog })
    await expect(dialog).toBeVisible()
    await expect(dialog.locator('.el-loading-mask')).toHaveCount(0)
    await expect(dialog.locator('.el-table__body tbody tr')).toHaveCount(3)
    const next = dialog.locator('.el-pagination button.btn-next')
    await next.click()
    await expect(dialog.locator('.el-table__body tbody tr')).toHaveCount(3)
    const response = page.waitForResponse(value => value.url().includes(item.query) && value.url().includes('keyword=999999') && value.status() === 200)
    await dialog.getByPlaceholder(item.search).fill('999999')
    await dialog.getByRole('button', { name: '搜索', exact: true }).click()
    await response
    await expect(dialog.getByText(item.empty)).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath(`${item.route.slice(1)}-history-empty.png`), fullPage: true })
    await dialog.getByPlaceholder(item.search).clear()
    await dialog.getByRole('button', { name: '搜索', exact: true }).click()
    await expect(dialog.locator('.el-table__body tbody tr')).toHaveCount(3)
    const failedUrl = `**/api/v1${item.query}**`
    await page.route(failedUrl, route => route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ success: false, error: { code: 'TEST_FAILURE', message: '模拟服务故障' } }) }))
    await dialog.getByRole('button', { name: '搜索', exact: true }).click()
    await expect(page.getByText(item.route === '/forecast' ? /加载版本失败/ : /加载列表失败/)).toBeVisible()
    await page.unroute(failedUrl)
    await dialog.getByRole('button', { name: '搜索', exact: true }).click()
    await expect(dialog.locator('.el-table__body tbody tr')).toHaveCount(3)
  }
})

test('F12: historical versions keep their own material values and month columns', async ({ page, request }, testInfo) => {
  const loginResponse = await request.post(`${api}/auth/login`, {
    data: { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD }
  })
  expect(loginResponse.status()).toBe(200)
  const headers = { Authorization: `Bearer ${(await loginResponse.json()).data.token}` }
  const versionsResponse = await request.get(`${api}/forecast-versions?page=1&pageSize=3`, { headers })
  expect(versionsResponse.status()).toBe(200)
  const versions = ((await versionsResponse.json()).data.items as { id: number; versionNo: number }[]).slice(1, 3)
  expect(versions).toHaveLength(2)

  await login(page)
  await page.goto('/forecast')
  await expect(page.locator('.table-card .el-loading-mask')).toHaveCount(0)
  for (const version of versions) {
    const detailsResponse = await request.get(`${api}/forecast-versions/${version.id}?page=1&pageSize=20`, { headers })
    expect(detailsResponse.status()).toBe(200)
    const details = (await detailsResponse.json()).data as {
      items: { materialId: string; months: Record<string, number> }[]; recognizedMonths: string[]
    }
    expect(details.items.length).toBeGreaterThan(0)
    expect(details.recognizedMonths.length).toBe(6)
    await page.getByRole('button', { name: '查看版本' }).click()
    const dialog = page.getByRole('dialog', { name: '版本记录' })
    await dialog.getByRole('row', { name: new RegExp(`V${version.versionNo}\\b`) }).getByRole('button', { name: '查看' }).click()
    await expect(page.getByText(`历史版本 V${version.versionNo}`, { exact: true })).toBeVisible()
    const firstMonth = details.recognizedMonths[0].slice(0, 7)
    const lastMonth = details.recognizedMonths.at(-1)!.slice(0, 7)
    await expect(page.locator('.forecast-page .el-table__header').first()).toContainText(firstMonth)
    await expect(page.locator('.forecast-page .el-table__header').first()).toContainText(lastMonth)
    const material = details.items[0].materialId
    await page.getByPlaceholder('搜索料号/名称/工厂').fill(material)
    await page.getByRole('button', { name: '搜索', exact: true }).first().click()
    const row = page.locator('.forecast-page .el-table__body tbody tr').filter({ hasText: material }).first()
    await expect(row).toBeVisible()
    await expect(row).toContainText(String(details.items[0].months[details.recognizedMonths[0]] ?? ''))
    await page.screenshot({ path: testInfo.outputPath(`f12-version-${version.id}.png`), fullPage: true })
  }
})

test('F12/I10: history endpoints enforce 401 and 403 and pages explain access errors', async ({ page, request }) => {
  const adminLogin = await request.post(`${api}/auth/login`, {
    data: { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD }
  })
  expect(adminLogin.status()).toBe(200)
  const headers = { Authorization: `Bearer ${(await adminLogin.json()).data.token}` }
  const username = `e2e-history-denied-${Date.now()}`
  const created = await request.post(`${api}/users`, {
    headers, data: { username, displayName: username, roleIds: [] }
  })
  expect(created.status()).toBe(200)
  const userId = (await created.json()).data.id as number
  try {
    const deniedLogin = await request.post(`${api}/auth/login`, {
      data: { username, password: process.env.E2E_ROLE_PASSWORD }
    })
    expect(deniedLogin.status()).toBe(200)
    const denied = { Authorization: `Bearer ${(await deniedLogin.json()).data.token}` }
    for (const path of ['/forecast-versions?page=1&pageSize=3', '/inventory-snapshots/page?page=1&pageSize=3',
      '/forecast-versions/43?page=1&pageSize=20', '/inventory-snapshots/63?page=1&pageSize=20']) {
      expect((await request.get(`${api}${path}`)).status(), `${path} without token`).toBe(401)
      expect((await request.get(`${api}${path}`, { headers: denied })).status(), `${path} without role`).toBe(403)
    }

    await login(page)
    for (const item of [
      { route: '/forecast', button: '查看版本', query: '**/api/v1/forecast-versions?**', error: /加载版本失败/ },
      { route: '/inventory', button: '历史版本', query: '**/api/v1/inventory-snapshots/page?**', error: /加载列表失败/ }
    ]) {
      await page.goto(item.route)
      await expect(page.locator('.el-loading-mask')).toHaveCount(0)
      await page.getByRole('button', { name: item.button, exact: true }).click()
      const dialog = page.getByRole('dialog', { name: item.route === '/forecast' ? '版本记录' : '历史版本' })
      await expect(dialog).toBeVisible()
      await page.route(item.query, route => route.fulfill({
        status: 403, contentType: 'application/json',
        body: JSON.stringify({ success: false, error: { code: 'FORBIDDEN', message: '无权访问历史版本' } })
      }))
      await dialog.getByRole('button', { name: '搜索', exact: true }).click()
      await expect(page.getByText(item.error)).toBeVisible()
      await page.unroute(item.query)
    }
    await page.route('**/api/v1/inventory-snapshots/page?**', route => route.fulfill({ status: 401, contentType: 'application/json', body: '{}' }))
    await page.getByRole('dialog', { name: '历史版本' }).getByRole('button', { name: '搜索', exact: true }).click()
    await expect(page).toHaveURL(/\/login\?redirect=/)
  } finally {
    const removed = await request.delete(`${api}/users/${userId}`, { headers })
    expect(removed.status()).toBe(200)
  }
})

test('B20: historical planning shows frozen versions and full checksums without mixed-role actions', async ({ page, request }, testInfo) => {
  const loginResponse = await request.post(`${api}/auth/login`, {
    data: { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD }
  })
  expect(loginResponse.status()).toBe(200)
  const token = (await loginResponse.json()).data.token as string
  const response = await request.get(`${api}/mrp/batches/73`, { headers: { Authorization: `Bearer ${token}` } })
  expect(response.status()).toBe(200)
  const batch = (await response.json()).data as { inputChecksum: string; resultChecksum: string; forecastVersionId: number; inventorySnapshotId: number }
  expect(batch.inputChecksum).toHaveLength(64)
  expect(batch.resultChecksum).toHaveLength(64)

  await login(page)
  await page.goto('/planning/73')
  await expect(page.getByText('历史版本', { exact: true }).first()).toBeVisible()
  await expect(page.getByRole('button', { name: '确认整机排产' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '生产日历检查' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '执行与锁定' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '前往物料缺料分析' })).toHaveCount(0)
  await page.getByRole('button', { name: '版本信息' }).click()
  const drawer = page.getByRole('dialog', { name: '版本信息' })
  await expect(drawer.getByText(batch.inputChecksum)).toBeVisible()
  await expect(drawer.getByText(batch.resultChecksum)).toBeVisible()
  await expect(drawer.getByText(`#${batch.forecastVersionId}`, { exact: true })).toBeVisible()
  await expect(drawer.getByText(`#${batch.inventorySnapshotId}`, { exact: true })).toBeVisible()
  await expect(drawer).toBeInViewport({ ratio: 0.9 })
  await page.screenshot({ path: testInfo.outputPath('b20-version-info.png'), fullPage: true })
  const staleResponse = await request.get(`${api}/mrp/batches/75`, { headers: { Authorization: `Bearer ${token}` } })
  expect(staleResponse.status()).toBe(200)
  const stale = (await staleResponse.json()).data as { inputChecksum: string; resultChecksum: string | null }
  expect(stale.inputChecksum).toHaveLength(64)
  expect(stale.resultChecksum).toBeNull()
  await page.goto('/planning/75')
  await page.getByRole('button', { name: '版本信息' }).click()
  const staleDrawer = page.getByRole('dialog', { name: '版本信息' })
  await expect(staleDrawer.getByText(stale.inputChecksum)).toBeVisible()
  await expect(staleDrawer.locator('tr').filter({ hasText: '结果校验码' }).getByText('-', { exact: true })).toBeVisible()
})

test('B19: planning grid paginates, reaches the final month and recovers from a failed request', async ({ page }, testInfo) => {
  await login(page)
  await page.goto('/planning/73')
  const grid = page.locator('.rolling-grid')
  await expect(grid.locator('.virtual-table')).toBeVisible()
  await expect(grid.locator('.el-pagination__total')).toContainText(/共\s*[2-9]\d|共\s*\d{3}/)
  const firstRow = await grid.locator('.el-table-v2__main .el-table-v2__row').first().innerText()
  const pageTwo = page.waitForResponse(value => value.url().includes('/mrp/batches/73/rolling-grid?') && value.url().includes('page=2') && value.status() === 200)
  await grid.locator('.grid-footer .btn-next').click()
  await pageTwo
  await expect(grid.locator('.el-pagination .is-active')).toHaveText('2')
  const secondRow = await grid.locator('.el-table-v2__main .el-table-v2__row').first().innerText()
  expect(secondRow).not.toBe(firstRow)

  await grid.locator('.grid-toolbar .el-select').click()
  const months = page.getByRole('option').filter({ hasText: /^\d{4}-\d{2}$/ })
  const lastMonth = await months.last().innerText()
  await months.last().click()
  await expect(grid.locator('.el-table-v2__header').first()).toContainText(lastMonth.slice(5))
  await page.screenshot({ path: testInfo.outputPath('b19-last-month.png'), fullPage: true })

  await page.route('**/api/v1/mrp/batches/73/rolling-grid?**', route => route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ success: false, error: { code: 'TEST_FAILURE', message: '模拟网格故障' } }) }))
  await grid.getByRole('button', { name: '查询' }).click()
  await expect(grid.getByText(/加载.*失败|模拟网格故障/)).toBeVisible()
  await page.unroute('**/api/v1/mrp/batches/73/rolling-grid?**')
  await grid.getByRole('button', { name: '重新加载' }).click()
  await expect(grid.locator('.virtual-table')).toBeVisible()
  await expect(grid.getByText(/加载.*失败|模拟网格故障/)).toHaveCount(0)
})

test('I10: inventory detail search covers the whole snapshot across pages', async ({ page, request }, testInfo) => {
  test.setTimeout(90_000)
  await login(page)
  const unauthorized = await request.get(`${api}/inventory-snapshots/63?keyword=32083100`)
  expect(unauthorized.status()).toBe(401)
  await page.goto('/inventory')
  await expect(page.locator('.inventory-page .table-card .el-loading-mask')).toHaveCount(0)
  await page.getByRole('button', { name: '历史版本', exact: true }).click()
  const history = page.getByRole('dialog', { name: '历史版本' })
  await expect(history).toBeVisible()
  await history.getByPlaceholder('搜索ID / 日期 / 状态').fill('63')
  await history.getByRole('button', { name: '搜索', exact: true }).click()
  await history.locator('tr').filter({ hasText: '#63' }).getByRole('button', { name: '查看' }).click()
  const detail = page.locator('.inventory-page .table-card')
  await expect(detail).toContainText('库存快照 #63')
  const search = detail.getByPlaceholder('搜索仓库 / 料号 / 名称 / 供应商')
  await search.fill('32083100')
  await expect(detail.locator('.el-pagination__total')).toContainText('共 2 条')
  await expect(detail.getByRole('row').filter({ hasText: '32083100' })).toHaveCount(2)
  await page.screenshot({ path: testInfo.outputPath('i10-material-across-pages.png'), fullPage: true })
  await search.fill('UAT_YH')
  await expect(detail.locator('.el-pagination__total')).toContainText('共 82 条')
  await expect(detail.getByRole('row').filter({ hasText: 'UAT_APK' })).toHaveCount(0)
  await search.fill('notfound')
  await expect(detail.getByText('暂无数据', { exact: true })).toHaveCount(1)
  await search.clear()
  await expect(detail.locator('.el-pagination__total')).toContainText('共 165 条')
})
