import { expect, test, type Page } from '@playwright/test'

async function login(page: Page, password: string) {
  await page.goto('/login')
  await page.getByPlaceholder('邮箱').fill('admin')
  await page.getByPlaceholder('密码').fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).not.toHaveURL(/\/login/)
}

test('物料与库存策略页面完成新增、修改、日志和删除', async ({ page, request }) => {
  test.setTimeout(120_000)
  const password = process.env.E2E_ADMIN_PASSWORD
  expect(password, 'E2E_ADMIN_PASSWORD').toBeTruthy()
  const base = 'http://127.0.0.1:8182/api/v1'
  const loginResponse = await request.post(`${base}/auth/login`, { data: { username: 'admin', password } })
  const token = (await loginResponse.json()).data.token as string
  const headers = { Authorization: `Bearer ${token}` }
  async function allItems(path: string) {
    const values: Array<Record<string, string>> = []
    for (let pageNo = 1; ; pageNo++) {
      const response = await request.get(`${base}${path}?page=${pageNo}&pageSize=100`, { headers })
      expect(response.ok()).toBeTruthy()
      const result = (await response.json()).data as { items: Array<Record<string, string>>; total: number }
      values.push(...result.items)
      if (values.length >= result.total) break
    }
    return values
  }
  const materialsResponse = await request.get(`${base}/mrp/material-policies/materials/page?page=1&pageSize=10`, { headers })
  const materials = ((await materialsResponse.json()).data.items as Array<{ materialCode: string }>).map(item => item.materialCode)
  const policyCodes = new Set((await allItems('/mrp/material-policies/page')).map(item => item.materialCode))
  const stockCodes = new Set((await allItems('/mrp/stock-policies/page')).map(item => `${item.warehouseCode}|${item.materialCode}`))
  const warehouse = 'UAT_APK'
  const material = materials.find(code => !policyCodes.has(code) && !stockCodes.has(`${warehouse}|${code}`))
  expect(material, '需要一个尚未维护策略的现有物料').toBeTruthy()

  await login(page, password!)
  await page.goto('/mrp/material-policies')
  await page.getByRole('button', { name: '新增', exact: true }).click()
  let dialog = page.getByRole('dialog', { name: '新增物料策略' })
  await dialog.getByRole('combobox', { name: /物料料号/ }).click()
  await page.getByRole('option').filter({ hasText: material! }).first().click()
  await dialog.locator('.el-form-item').filter({ hasText: '供应方式' }).locator('.el-select').click()
  await page.getByRole('option', { name: '外购' }).click()
  await dialog.locator('.el-form-item').filter({ hasText: '下钻策略' }).locator('.el-select').click()
  await page.getByRole('option', { name: '停止下钻' }).click()
  await dialog.getByRole('button', { name: '确认' }).click()
  const policySearch = page.getByPlaceholder('搜索编码、名称或规格')
  await policySearch.fill(material!.slice(2, 9))
  await policySearch.press('Enter')
  const policyRow = page.locator('.el-table__body tr').filter({ hasText: material! })
  await expect(policyRow).toContainText('外购')
  await policyRow.getByRole('button', { name: '编辑' }).click()
  dialog = page.getByRole('dialog', { name: '编辑物料策略' })
  await dialog.locator('.el-form-item').filter({ hasText: '供应方式' }).locator('.el-select').click()
  await page.getByRole('option', { name: '自制' }).click()
  await dialog.getByRole('button', { name: '确认' }).click()
  await expect(policyRow).toContainText('自制')
  await policyRow.getByRole('button', { name: '查看日志' }).click()
  await expect(page.getByRole('dialog').first()).toBeVisible()
  await page.locator('.el-dialog:visible .el-dialog__headerbtn').click()
  await policyRow.getByRole('button', { name: '删除' }).click()
  await page.getByRole('dialog', { name: '删除物料策略' }).getByRole('button', { name: '确定' }).click()
  await expect(policyRow).toHaveCount(0)

  await page.goto('/mrp/stock-policies')
  await page.getByRole('button', { name: '新增', exact: true }).click()
  dialog = page.getByRole('dialog', { name: '新增库存策略' })
  await dialog.locator('.el-form-item').filter({ hasText: '仓库' }).locator('.el-select').click()
  await page.getByRole('option').filter({ hasText: warehouse }).first().click()
  await dialog.getByRole('combobox', { name: /物料料号/ }).click()
  await page.getByRole('option').filter({ hasText: material! }).first().click()
  await dialog.locator('.el-input-number input').nth(0).fill('1')
  await dialog.locator('.el-input-number input').nth(1).fill('2')
  await dialog.getByRole('button', { name: '确认' }).click()
  const stockSearch = page.getByPlaceholder('搜索仓库或物料')
  await stockSearch.fill(material!.slice(2, 9))
  await stockSearch.press('Enter')
  const stockRow = page.locator('.el-table__body tr').filter({ hasText: material! }).filter({ hasText: warehouse })
  await expect(stockRow).toContainText('2')
  await stockRow.getByRole('button', { name: '编辑' }).click()
  dialog = page.getByRole('dialog', { name: '编辑库存策略' })
  await dialog.locator('.el-input-number input').nth(1).fill('3')
  await dialog.getByRole('button', { name: '确认' }).click()
  await expect(stockRow).toContainText('3')
  await stockRow.getByRole('button', { name: '查看日志' }).click()
  await expect(page.getByRole('dialog').first()).toBeVisible()
  await page.locator('.el-dialog:visible .el-dialog__headerbtn').click()
  await stockRow.getByRole('button', { name: '删除' }).click()
  await page.getByRole('dialog', { name: '删除库存策略' }).getByRole('button', { name: '确定' }).click()
  await expect(stockRow).toHaveCount(0)
})
