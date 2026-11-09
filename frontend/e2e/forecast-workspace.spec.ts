import { expect, test } from '@playwright/test'

const base = process.env.E2E_BASE_URL || 'http://localhost:5191'

test('Forecast 浏览器完整闭环：编辑、新增、删除、恢复、日志、差异和发布', async ({ page, request }) => {
  test.setTimeout(90000)
  const failures: string[] = []
  page.on('pageerror', error => failures.push(error.message))
  page.on('response', response => {
    if (response.url().includes('/api/v1/') && response.status() >= 500) failures.push(`${response.status()} ${response.url()}`)
  })
  const auth = await request.post(`${base}/api/v1/auth/login`, { data: { username: 'admin', password: 'admin123' } })
  expect(auth.ok()).toBeTruthy()
  const token = (await auth.json()).data.token as string
  const headers = { Authorization: `Bearer ${token}` }
  const before = await request.get(`${base}/api/v1/forecast-workspace/current`, { headers })
  expect(before.ok()).toBeTruthy()
  const previous = (await before.json()).data as { id: number; versionNo: number }
  const original = await request.get(`${base}/api/v1/forecast-versions/${previous.id}?page=1&pageSize=20`, { headers })
  const originalData = (await original.json()).data as { items: { factoryCode: string; materialId: string; months: Record<string, number> }[] }
  const currentRow = originalData.items[0]
  expect(currentRow, '当前版本必须包含供编辑的验收料号').toBeTruthy()
  const material = currentRow!.materialId
  const factory = currentRow!.factoryCode
  const editedMonth = Object.keys(currentRow!.months)[0]
  const oldQty = Number(currentRow!.months[editedMonth])
  const newQty = oldQty + 1.25
  const newMaterial = `FORECAST-E2E-${Date.now()}`
  const created = await request.post(`${base}/api/v1/materials`, { headers, data: { materialCode: newMaterial, materialName: 'E2E测试整机', isActive: true, isDeleted: false } })
  expect(created.ok(), await created.text()).toBeTruthy()

  await page.goto(`${base}/login`)
  await page.getByPlaceholder('邮箱').fill('admin')
  await page.getByPlaceholder('密码').fill('admin123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).not.toHaveURL(/\/login/)
  await page.goto(`${base}/forecast`)
  await expect(page.getByRole('button', { name: '放弃变更' }), '验收前应无活动草稿').toHaveCount(0)
  await expect(page.getByText(material).first()).toBeVisible()
  const table = page.locator('.forecast-page > .el-card .el-table').first()
  const row = table.locator('.el-table__body tr').filter({ hasText: factory }).filter({ hasText: material }).first()
  await expect(row).toBeVisible()

  await row.getByRole('button', { name: '查看日志' }).click()
  await expect(page.getByRole('dialog', { name: `操作日志 · ${material}` })).toBeVisible()
  await page.getByRole('dialog', { name: `操作日志 · ${material}` }).getByRole('button', { name: '关闭此对话框' }).click()
  await page.locator('.page-actions').getByRole('button', { name: '版本差异' }).click()
  await expect(page.getByRole('dialog', { name: /版本差异/ })).toBeVisible()
  await expect(row.getByRole('button', { name: '版本差异' })).toHaveCount(0)
  await expect(page.getByRole('dialog', { name: /版本差异/ })).toBeVisible()
  await page.getByRole('dialog', { name: /版本差异/ }).getByRole('button', { name: '关闭此对话框' }).click()

  await row.getByRole('button', { name: '编辑' }).click()
  const edit = page.getByRole('dialog', { name: '编辑经营计划' })
  await expect(edit).toBeVisible()
  await expect(page.getByText('请输入业务制单人')).toHaveCount(0)
  await expect(edit.locator('.el-form-item').filter({ hasText: '料号' }).locator('input')).toBeDisabled()
  await expect(edit.getByRole('button', { name: '增加月份' })).toHaveCount(0)
  await expect(edit.getByRole('button', { name: '移除' })).toHaveCount(0)
  await edit.locator('.el-form-item').filter({ hasText: '月份数量' }).locator('input').first().fill(String(newQty))
  await edit.locator('textarea').fill('端到端测试：修改现有料号')
  await edit.getByRole('button', { name: '确认' }).click()
  await expect(page.getByText('当前显示：待发布预览')).toBeVisible()
  await expect(row).toContainText(String(newQty))

  await page.getByRole('button', { name: '新增', exact: true }).click()
  const add = page.getByRole('dialog', { name: '新增经营计划' })
  await add.locator('.el-select').first().click()
  await page.getByRole('option', { name: '爱培科' }).click()
  const materialPicker = add.locator('.el-form-item').filter({ hasText: '料号' }).locator('.el-select')
  await materialPicker.click()
  await materialPicker.locator('input').fill(newMaterial.slice(0, 12))
  await expect(page.getByRole('option', { name: new RegExp(newMaterial) })).toBeVisible()
  await page.getByRole('option', { name: new RegExp(newMaterial) }).click()
  await expect(materialPicker).toContainText(newMaterial)
  await expect(add.getByRole('button', { name: '增加月份' })).toHaveCount(0)
  await expect(add.getByRole('button', { name: '移除' })).toHaveCount(0)
  await add.locator('.el-form-item').filter({ hasText: '月份数量' }).locator('input').last().fill('4.25')
  await add.locator('textarea').fill('端到端测试：新增第二工厂料号')
  await add.getByRole('button', { name: '确认' }).click()
  const added = table.locator('.el-table__body tr').filter({ hasText: '爱培科' }).filter({ hasText: newMaterial }).first()
  await expect(added).toContainText('4.25')

  await row.getByRole('button', { name: '删除' }).click()
  const remove = page.getByRole('dialog', { name: `删除 ${material}` })
  await remove.locator('input').fill('端到端测试：删除后恢复')
  await remove.getByRole('button', { name: '确认' }).click()
  await expect(row).toHaveCount(0)
  await page.locator('.page-actions').getByRole('button', { name: '版本差异' }).click()
  const diff = page.getByRole('dialog', { name: /版本差异/ })
  await expect(diff.getByText('删除').first()).toBeVisible()
  await diff.locator('.el-select').last().click()
  await page.getByRole('option', { name: `${factory} / ${material}` }).click()
  await diff.getByRole('button', { name: '恢复' }).click()
  const restore = page.getByRole('dialog', { name: `恢复 ${material}` })
  await restore.locator('input').fill('端到端测试：恢复料号')
  await restore.getByRole('button', { name: '确认' }).click()
  await expect(row).toContainText(String(newQty))
  await diff.getByRole('button', { name: '关闭此对话框' }).click()

  await row.getByRole('button', { name: '查看日志' }).click()
  const logs = page.getByRole('dialog', { name: `操作日志 · ${material}` })
  await expect(logs.getByRole('button', { name: /编辑了经营计划明细/ }).first()).toBeVisible()
  await expect(logs.getByRole('button', { name: /删除了经营计划明细/ }).first()).toBeVisible()
  await expect(logs.getByText('端到端测试：恢复料号').first()).toBeVisible()
  await logs.getByRole('button', { name: '关闭此对话框' }).click()
  const newLogResponse = page.waitForResponse(response => response.url().includes('/audit-logs?')
    && new URL(response.url()).searchParams.get('resourceId') === `爱培科|${newMaterial}` && response.status() === 200)
  await added.getByRole('button', { name: '查看日志' }).click()
  await newLogResponse
  await expect(page.getByRole('dialog', { name: `操作日志 · ${newMaterial}` }).getByRole('button', { name: /新增了经营计划明细/ })).toBeVisible()
  await page.getByRole('dialog', { name: `操作日志 · ${newMaterial}` }).getByRole('button', { name: '关闭此对话框' }).click()

  await page.getByRole('button', { name: /查看变更.*并生成新版本/ }).click()
  const publish = page.getByRole('dialog', { name: /生成新版本/ })
  await expect(publish.getByText(/相关未发布排产批次将失效/)).toBeVisible()
  await expect(publish.getByText('数量变化', { exact: true })).toBeVisible()
  await expect(publish.getByText('新增', { exact: true }).first()).toBeVisible()
  await publish.getByPlaceholder('请输入生成原因').fill('端到端测试：确认发布新版本')
  await publish.getByRole('button', { name: '确认生成' }).click()
  await expect(page.locator('.forecast-version-heading').getByText(`生效版本 V${previous.versionNo + 1}`)).toBeVisible()
  await expect(page.getByRole('button', { name: '放弃变更' })).toHaveCount(0)

  const current = await request.get(`${base}/api/v1/forecast-workspace/current`, { headers })
  expect(current.ok()).toBeTruthy()
  const version = (await current.json()).data
  expect(version.versionNo).toBe(previous.versionNo + 1)
  const comparison = await request.get(`${base}/api/v1/forecast-workspace/compare?oldId=${previous.id}&newId=${version.id}&changesOnly=true`, { headers })
  expect(comparison.ok()).toBeTruthy()
  const changes = (await comparison.json()).data.items.map((item: { changeType: string }) => item.changeType)
  expect(changes).toContain('ADDED')
  expect(changes).toContain('QUANTITY')
  const oldVersion = await request.get(`${base}/api/v1/forecast-versions/${previous.id}?page=1&pageSize=20`, { headers })
  expect((await oldVersion.json()).data.items.find((item: { factoryCode: string; materialId: string }) => item.factoryCode === factory && item.materialId === material).months[editedMonth]).toBe(oldQty)
  const draft = await request.get(`${base}/api/v1/forecast-workspace/draft`, { headers })
  expect((await draft.json()).data).toEqual({})
  expect(failures).toEqual([])
})
