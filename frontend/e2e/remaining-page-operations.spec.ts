import { expect, test } from '@playwright/test'

async function login(page: import('@playwright/test').Page) {
  await page.goto('/login')
  await page.getByPlaceholder('邮箱').fill('admin')
  await page.getByPlaceholder('密码').fill(process.env.E2E_ADMIN_PASSWORD || '')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/planning$/)
}

test('经营计划草稿编辑、删除、恢复和放弃', async ({ page, request }) => {
  test.setTimeout(60_000)
  page.setDefaultTimeout(10_000)
  await login(page)
  const auth = await request.post('/api/v1/auth/login', {
    data: { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD }
  })
  expect(auth.ok()).toBeTruthy()
  const headers = { Authorization: `Bearer ${(await auth.json()).data.token}` }
  await page.goto('/forecast')
  const row = page.locator('.forecast-page > .el-card .el-table__body tr').first()
  await expect(row).toBeVisible()
  const material = (await row.locator('td').nth(2).innerText()).trim()
  const factory = (await row.locator('td').first().innerText()).trim()
  let draftId: number | undefined
  try {
    await page.getByRole('button', { name: '新增', exact: true }).click()
    const add = page.getByRole('dialog', { name: '新增经营计划' })
    await expect(add).toBeVisible()
    await add.getByRole('button', { name: '取消' }).click()

    await row.getByRole('button', { name: '编辑' }).click()
    const edit = page.getByRole('dialog', { name: '编辑经营计划' })
    await edit.locator('.el-form-item').filter({ hasText: '模具' }).getByRole('textbox').fill('页面验收草稿')
    await edit.locator('.el-form-item').filter({ hasText: '调整原因' }).getByRole('textbox').fill('页面操作验收')
    await edit.getByRole('button', { name: '确认' }).click()
    await expect(page.getByText('当前显示：待发布预览')).toBeVisible()
    const draft = await request.get('/api/v1/forecast-workspace/draft', { headers })
    draftId = (await draft.json()).data.id
    const existing = await request.get(`/api/v1/forecast-workspace/draft/${draftId}/line?factoryCode=${encodeURIComponent(factory)}&materialId=${encodeURIComponent(material)}`, { headers })
    expect(existing.ok()).toBeTruthy()
    const targetId = (await existing.json()).data.id as number

    const changed = page.locator('.forecast-page > .el-card .el-table__body tr')
      .filter({ has: page.getByRole('cell', { name: material, exact: true }) })
      .filter({ has: page.getByRole('cell', { name: factory, exact: true }) }).first()
    await changed.getByRole('button', { name: '查看日志' }).click()
    await expect(page.getByRole('dialog', { name: /操作日志/ })).toBeVisible()
    await page.getByRole('dialog', { name: /操作日志/ }).locator('.el-dialog__headerbtn').click()
    await changed.getByRole('button', { name: '删除' }).click()
    const remove = page.getByRole('dialog', { name: new RegExp(`删除 ${material}`) })
    await remove.locator('input').fill('页面操作验收')
    const deleted = page.waitForResponse(response => /\/forecast-workspace\/draft\/\d+\/lines\/\d+\/delete$/.test(response.url()))
    await remove.getByRole('button', { name: '确认' }).click()
    const deletion = await deleted
    expect(deletion.status()).toBe(200)
    expect(deletion.url()).toContain(`/lines/${targetId}/delete`)
    await expect(remove).toBeHidden()
    await expect(page.getByText('已标记删除，请核验差异后生成新版本')).toBeVisible()
    const target = await request.get(`/api/v1/forecast-workspace/draft/${draftId}/deleted-line?factoryCode=${encodeURIComponent(factory)}&materialId=${encodeURIComponent(material)}`, { headers })
    expect(target.status()).toBe(200)
    const lines = await request.get(`/api/v1/forecast-workspace/draft/${draftId}/lines?page=1&pageSize=100`, { headers })
    expect(lines.ok()).toBeTruthy()
    const remaining = (await lines.json()).data.items as { id: number; factoryCode: string; materialId: string }[]
    expect(remaining.filter(item => item.factoryCode === factory && item.materialId === material)).toEqual([])
    await expect(changed).toHaveCount(0)

    await page.getByRole('button', { name: '版本差异' }).click()
    const compare = page.getByRole('dialog', { name: /版本差异/ })
    await expect(compare.getByRole('button', { name: '恢复' })).toBeVisible()
    await compare.locator('.el-select').last().click()
    await page.getByRole('option').filter({ hasText: material }).click()
    await compare.getByRole('button', { name: '恢复' }).click()
    const restore = page.getByRole('dialog', { name: new RegExp(`恢复 ${material}`) })
    await restore.locator('input').fill('页面操作验收')
    await restore.getByRole('button', { name: '确认' }).click()
    await expect(page.getByText('已恢复料号')).toBeVisible()
    await compare.locator('.el-dialog__headerbtn').click()
    await expect(changed).toBeVisible()
    await page.getByRole('button', { name: '放弃变更' }).click()
    const discard = page.getByRole('dialog', { name: '放弃草稿' })
    await discard.locator('input').fill('页面操作验收结束')
    await discard.getByRole('button', { name: '确认' }).click()
    await expect(page.getByText('当前显示：待发布预览')).toHaveCount(0)
  } finally {
    for (let attempt = 0; attempt < 3; attempt++) {
      const active = await request.get('/api/v1/forecast-workspace/draft', { headers })
      const draft = (await active.json()).data as { id?: number; revision?: number }
      if (draft.id !== draftId) break
      const removed = await request.post(`/api/v1/forecast-workspace/draft/${draft.id}/discard`, {
        headers, data: { expectedRevision: draft.revision, reason: '清理页面操作验收草稿' }
      })
      if (removed.ok()) break
      if (attempt === 2) throw new Error(`草稿清理失败: ${removed.status()} ${await removed.text()}`)
    }
  }
})

test('生产日历批量预览、确认和逐日清理', async ({ page, request }) => {
  test.setTimeout(90_000)
  await login(page)
  const factory = `UITB${Date.now().toString(36).toUpperCase()}`
  await page.goto('/mrp/factories')
  await page.getByRole('button', { name: '新增', exact: true }).click()
  const create = page.getByRole('dialog', { name: '新增工厂' })
  await create.getByRole('textbox').nth(0).fill(factory)
  await create.getByRole('textbox').nth(1).fill('批量日历验收工厂')
  await create.getByRole('button', { name: '确认' }).click()
  const auth = await request.post('/api/v1/auth/login', {
    data: { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD }
  })
  expect(auth.ok()).toBeTruthy()
  const headers = { Authorization: `Bearer ${(await auth.json()).data.token}` }
  try {
    await page.goto('/mrp/production-calendars')
    await page.locator('.filter-bar .el-select').first().click()
    await page.getByRole('option', { name: factory }).click()
    await page.getByRole('button', { name: '新增', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '批量设置生产日历' })
    await expect(dialog.getByRole('button', { name: '确认' })).toBeDisabled()
    await dialog.getByPlaceholder('正整数（台/日）').fill('7')
    await dialog.getByText('周六', { exact: true }).click()
    await dialog.getByText('周日', { exact: true }).click()
    await dialog.getByRole('button', { name: '预览' }).click()
    await expect(dialog.getByText(/新增 \d+ 日/)).toBeVisible()
    await expect(dialog.getByRole('button', { name: '确认' })).toBeEnabled()
    await dialog.getByRole('button', { name: '确认' }).click()
    await expect(dialog).toBeHidden()
    await page.locator('.view-switch .el-radio-button').filter({ hasText: '列表' }).click()
    await expect(page.locator('.table-card .el-table__body tr').first()).toContainText('7 台')
    const pager = page.locator('.table-card .el-pagination')
    await expect(pager).toBeVisible()
    await expect(pager.locator('.el-pagination__total')).not.toContainText('共 0 条')
  } finally {
    const list = await request.get(`/api/v1/mrp/production-calendars/page?factoryCode=${factory}&page=1&pageSize=100`, { headers })
    expect(list.ok()).toBeTruthy()
    for (const row of (await list.json()).data.items as { id: number }[]) {
      const deleted = await request.delete(`/api/v1/mrp/production-calendars/${row.id}`, { headers })
      expect(deleted.ok()).toBeTruthy()
    }
    await page.goto('/mrp/factories')
    const search = page.getByPlaceholder('搜索工厂编码或名称')
    await search.fill(factory)
    await search.press('Enter')
    const row = page.locator('.el-table__body tr').filter({ hasText: factory })
    await expect(row).toHaveCount(1)
    await row.getByRole('button', { name: '删除' }).click()
    await page.getByRole('dialog', { name: '删除工厂' }).getByRole('button', { name: '确定' }).click()
    await expect(row).toHaveCount(0)
  }
})

test('排产日周月、历史和物控标签及来源下钻', async ({ page }) => {
  test.setTimeout(90_000)
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('response', response => {
    if (response.url().includes('/api/v1/') && response.status() >= 500) errors.push(`${response.status()} ${response.url()}`)
  })
  await login(page)
  await page.goto('/planning')
  for (const tab of ['日排产', '周汇总', '月汇总']) {
    await page.getByRole('tab', { name: tab }).click()
    await expect(page.locator('.rolling-grid .virtual-table')).toBeVisible()
  }
  await page.getByRole('button', { name: '历史版本' }).click()
  const history = page.getByRole('dialog', { name: '历史版本' })
  await expect(history).toBeVisible()
  await history.getByPlaceholder('搜索版本号或工厂').fill('999999')
  await history.getByPlaceholder('搜索版本号或工厂').press('Enter')
  await expect(history.getByText('暂无历史排产版本')).toBeVisible()
  await history.getByPlaceholder('搜索版本号或工厂').clear()
  await history.getByPlaceholder('搜索版本号或工厂').press('Enter')
  await expect(history.locator('.el-table__body tr').first()).toBeVisible()
  await history.locator('.el-table__body tr').first().getByRole('button', { name: '查看' }).click()
  await expect(page).toHaveURL(/\/planning\/\d+$/)
  await page.goto('/shortage')
  for (const tab of ['末级物料', '日备料', '待处理异常', '默认下钻', '库存分配', '待分配需求', '库存风险', '物料反馈', '计算追溯']) {
    await page.getByRole('tab', { name: new RegExp(tab) }).click()
    await expect(page.locator('.card')).not.toBeEmpty()
  }
  await page.getByRole('tab', { name: '末级物料' }).click()
  const first = page.locator('.card .el-table__body tr').first()
  await expect(first).toBeVisible()
  await first.getByRole('button', { name: '受影响整机' }).click()
  await expect(page.getByRole('dialog', { name: '受影响整机' })).toBeVisible()
  await page.getByRole('dialog', { name: '受影响整机' }).locator('.el-dialog__headerbtn').click()
  await first.getByRole('button', { name: /仓|无冻结库存池/ }).click()
  await expect(page.getByRole('dialog', { name: '良品库存池' })).toBeVisible()
  expect(errors).toEqual([])
})
