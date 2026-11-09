import { expect, test } from '@playwright/test'

const api = 'http://127.0.0.1:8182/api/v1'

test('B12: linked transfer preview cancellation and double click write once', async ({ page, request }, testInfo) => {
  test.setTimeout(180_000)
  const login = await request.post(`${api}/auth/login`, {
    data: { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD }
  })
  expect(login.status()).toBe(200)
  const headers = { Authorization: `Bearer ${(await login.json()).data.token as string}` }
  const created = await request.post(`${api}/mrp/batches?factoryCode=ALL&forecastVersionId=50&inventorySnapshotId=66`, { headers })
  expect(created.status()).toBe(200)
  const batchId = (await created.json()).data.id as number
  try {
    const precheck = await request.post(`${api}/mrp/batches/${batchId}/precheck`, { headers })
    expect((await precheck.json()).data.status).toBe('READY_TO_CALCULATE')
    const started = await request.post(`${api}/mrp/batches/${batchId}/calculate`, { headers })
    expect(started.status()).toBe(200)
    let completed = false
    for (let attempt = 0; attempt < 90; attempt++) {
      const task = await request.get(`${api}/mrp/batches/${batchId}/calculation-task`, { headers })
      const status = (await task.json()).data?.status as string | undefined
      if (status === 'SUCCEEDED') { completed = true; break }
      expect(status).not.toBe('FAILED')
      await new Promise(resolve => setTimeout(resolve, 1000))
    }
    expect(completed).toBe(true)
    const gridResponse = await request.get(`${api}/mrp/batches/${batchId}/rolling-grid?view=MACHINE_OUTPUT&factoryCode=${encodeURIComponent('爱培科')}`, { headers })
    expect(gridResponse.status()).toBe(200)
    const grid = (await gridResponse.json()).data as {
      rows: Array<{ factoryCode: string; rootMaterialCode: string; dayQuantities: Record<string, string> }>
    }
    const row = grid.rows.find(item => item.rootMaterialCode === '6839AA000362' && item.factoryCode === '爱培科')
    expect(row).toBeDefined()
    const positive = Object.entries(row!.dayQuantities).filter(([, value]) => Number(value) > 0).map(([date]) => date).sort()
    expect(positive.length).toBeGreaterThan(1)
    const sourceDate = positive[0]!
    const targetDate = positive[1]!

    await page.goto('/login')
    await page.getByPlaceholder('邮箱').fill('admin')
    await page.getByPlaceholder('密码').fill(process.env.E2E_ADMIN_PASSWORD || '')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page).toHaveURL(/\/planning$/)
    const query = new URLSearchParams({ root: row!.rootMaterialCode, factory: '爱培科', date: sourceDate })
    await page.goto(`/planning/${batchId}?${query}`)
    const drawer = page.getByRole('dialog', { name: '数量与来源追溯' })
    await expect(drawer).toBeVisible()
    await drawer.getByRole('button', { name: '联动调整' }).first().click()
    const dialog = page.getByRole('dialog', { name: '整机与末级联动调整' })
    await expect(dialog).toBeVisible()
    const dateInput = dialog.locator('.el-date-editor input').first()
    await dateInput.fill(targetDate)
    await dateInput.press('Enter')
    await dialog.getByPlaceholder('正整数').fill('1')
    const previewResponse = page.waitForResponse(response => response.url().endsWith(`/mrp/batches/${batchId}/linked-transfers/preview`))
    await dialog.getByRole('button', { name: '预览明细' }).click()
    expect((await previewResponse).status()).toBe(200)
    await expect(dialog.getByText('各物料总量不变')).toBeVisible()
    await dialog.getByRole('button', { name: '取消', exact: true }).click()
    await expect(dialog).not.toBeVisible()
    const afterCancel = await request.get(`${api}/mrp/batches/${batchId}/overrides`, { headers })
    expect((await afterCancel.json()).data).toHaveLength(0)

    await drawer.getByRole('button', { name: '联动调整' }).first().click()
    await dateInput.fill(targetDate)
    await dateInput.press('Enter')
    await dialog.getByPlaceholder('正整数').fill('1')
    await dialog.getByRole('button', { name: '预览明细' }).click()
    await expect(dialog.getByText('各物料总量不变')).toBeVisible()
    await dialog.getByRole('textbox', { name: '调整原因' }).fill('B12浏览器双击确认')
    let commits = 0
    page.on('request', outgoing => {
      if (outgoing.url().endsWith(`/mrp/batches/${batchId}/linked-transfers`) && outgoing.method() === 'POST') commits++
    })
    await dialog.getByRole('button', { name: '确认', exact: true }).dblclick()
    await expect(dialog).not.toBeVisible()
    const afterCommit = await request.get(`${api}/mrp/batches/${batchId}/overrides`, { headers })
    expect((await afterCommit.json()).data).toHaveLength(2)
    expect(commits).toBe(1)
    await page.screenshot({ path: testInfo.outputPath('b12-linked-transfer.png'), fullPage: true })
  } finally {
    const closed = await request.post(`${api}/mrp/batches/${batchId}/cancel`, { headers })
    expect((await closed.json()).data.status).toBe('CANCELLED')
  }
})
