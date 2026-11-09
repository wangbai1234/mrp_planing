import { expect, test } from '@playwright/test'

test('真实草稿月份固定；取消放弃预览不报错且不提交', async ({ page, request }) => {
  const base = 'http://localhost:5191'
  const login = await request.post(base + '/api/v1/auth/login', { data: { username: 'admin', password: 'admin123' } })
  expect(login.ok(), await login.text()).toBeTruthy()
  const loginData = (await login.json()).data as { token: string; userId: number; username: string; displayName: string; roles: string[]; permissions: string[] }
  const token = loginData.token
  const headers = { Authorization: 'Bearer ' + token }
  const current = await request.get(base + '/api/v1/forecast-workspace/current', { headers })
  expect(current.ok()).toBeTruthy()
  const versionId = (await current.json()).data.id as number
  const details = await request.get(base + '/api/v1/forecast-versions/' + versionId + '?page=1&pageSize=20', { headers })
  expect(details.ok()).toBeTruthy()
  const detailData = (await details.json()).data as { items: { factoryCode: string; materialId: string; months: Record<string, number> }[]; recognizedMonths: string[] }
  const row = detailData.items[0]
  expect(row).toBeTruthy()
  let discardPosts = 0
  page.on('request', request => {
    if (request.method() === 'POST' && /forecast-workspace\/draft\/\d+\/discard$/.test(request.url())) discardPosts++
  })
  const created = await request.post(base + '/api/v1/forecast-workspace/draft', { headers, data: {} })
  expect(created.ok(), await created.text()).toBeTruthy()
  const draft = (await created.json()).data as { id: number; revision: number }
  try {
  await page.goto(base + '/login')
  await page.evaluate(data => {
    localStorage.setItem('mrp_token', data.token)
    localStorage.setItem('mrp_user_info', JSON.stringify({ id: data.userId, username: data.username, displayName: data.displayName, roles: data.roles, permissions: data.permissions }))
  }, loginData)
  await page.goto(base + '/forecast')
  const tableRow = page.locator('.forecast-page > .el-card .el-table__body tr').filter({ hasText: row.materialId }).first()
  await expect(tableRow).toBeVisible()
  await tableRow.getByRole('button', { name: '编辑' }).click()
  const edit = page.getByRole('dialog', { name: '编辑经营计划' })
  await expect(edit).toBeVisible()
  await expect(edit.getByRole('button', { name: '增加月份' })).toHaveCount(0)
  await expect(edit.getByRole('button', { name: '移除' })).toHaveCount(0)
  await expect(edit.locator('.el-form-item').filter({ hasText: '月份数量' }).locator('input')).toHaveCount(Object.keys(row.months).length)
  await edit.getByRole('button', { name: '取消' }).click()
  await page.getByRole('button', { name: '新增', exact: true }).click()
  const add = page.getByRole('dialog', { name: '新增经营计划' })
  await expect(add.locator('.el-form-item').filter({ hasText: '月份数量' }).locator('input')).toHaveCount(detailData.recognizedMonths.length)
  await expect(add.getByRole('button', { name: '增加月份' })).toHaveCount(0)
  await add.getByRole('button', { name: '取消' }).click()
  await page.getByRole('button', { name: '无实际变更，放弃预览' }).click()
  const confirm = page.getByRole('dialog', { name: '放弃草稿' })
  await expect(confirm).toBeVisible()
  await confirm.getByRole('button', { name: '取消' }).click()
  await expect(confirm).toHaveCount(0)
  await expect(page.getByText('放弃失败: 未知错误')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '无实际变更，放弃预览' })).toBeVisible()
  expect(discardPosts).toBe(0)
  const active = await request.get(base + '/api/v1/forecast-workspace/draft', { headers })
  expect((await active.json()).data.id).toBe(draft.id)
  const q = new URLSearchParams({ factoryCode: row.factoryCode, materialId: row.materialId })
  const lineResponse = await request.get(base + '/api/v1/forecast-workspace/draft/' + draft.id + '/line?' + q, { headers })
  expect(lineResponse.ok(), await lineResponse.text()).toBeTruthy()
  const line = (await lineResponse.json()).data as { id: number; formType: string; mold: string; status: string; months: Record<string, number> }
  const months = Object.fromEntries(Object.entries(line.months).map(([month, qty]) => [month.slice(0, 7), qty]))
  const body = { factoryCode: row.factoryCode, materialId: row.materialId, formType: line.formType, mold: line.mold, status: line.status, expectedRevision: draft.revision, reason: '测试阻止月份结构变更' }
  const added = await request.put(base + '/api/v1/forecast-workspace/draft/' + draft.id + '/lines/' + line.id, { headers, data: { ...body, months: { ...months, '2030-01': 1 } } })
  expect(added.status()).toBe(400)
  const shortened = { ...months }
  delete shortened[Object.keys(shortened)[0]]
  const removed = await request.put(base + '/api/v1/forecast-workspace/draft/' + draft.id + '/lines/' + line.id, { headers, data: { ...body, months: shortened } })
  expect(removed.status()).toBe(400)
  const summary = await request.get(base + '/api/v1/forecast-workspace/draft/' + draft.id + '/summary', { headers })
  expect((await summary.json()).data.changedLines).toBe(0)
  await page.getByRole('button', { name: '无实际变更，放弃预览' }).click()
  await confirm.locator('input').fill('隔离验收完成')
  await confirm.getByRole('button', { name: '确认' }).click()
  await expect(page.getByRole('button', { name: '无实际变更，放弃预览' })).toHaveCount(0)
  expect(discardPosts).toBe(1)
  const cleared = await request.get(base + '/api/v1/forecast-workspace/draft', { headers })
  expect((await cleared.json()).data).toEqual({})
  } finally {
    const active = await request.get(base + '/api/v1/forecast-workspace/draft', { headers })
    const live = (await active.json()).data as { id?: number; revision?: number }
    if (live.id === draft.id) await request.post(base + '/api/v1/forecast-workspace/draft/' + draft.id + '/discard', { headers, data: { expectedRevision: live.revision, reason: '清理隔离浏览器验收草稿' } })
  }
})
