import { expect, test } from '@playwright/test'

test('B02: no current BOM disables batch creation in planning dialog', async ({ page }, testInfo) => {
  await page.route('**/api/v1/mrp/batches/current-bom-status', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ success: true, data: { localCount: 0 } })
  }))
  await page.goto('/login')
  await page.getByPlaceholder('邮箱').fill('admin')
  await page.getByPlaceholder('密码').fill(process.env.E2E_ADMIN_PASSWORD || '')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/planning$/)
  await page.getByRole('button', { name: '生成排产' }).click()
  const dialog = page.getByRole('dialog', { name: '生成排产' })
  await expect(dialog.getByText('尚无当前同步的 BOM，请先完成 NC 同步')).toBeVisible()
  await expect(dialog.getByRole('button', { name: '确认', exact: true })).toBeDisabled()
  await page.screenshot({ path: testInfo.outputPath('b02-no-current-bom.png'), fullPage: true })
})

test('B02: missing factory warehouse mapping disables batch creation', async ({ page }, testInfo) => {
  await page.route('**/api/v1/mrp/factory-warehouse-mappings', async route => {
    const response = await route.fetch()
    const payload = await response.json()
    payload.data = payload.data.filter((row: { factoryCode: string }) => row.factoryCode !== '永惠')
    await route.fulfill({ response, body: JSON.stringify(payload) })
  })
  await page.goto('/login')
  await page.getByPlaceholder('邮箱').fill('admin')
  await page.getByPlaceholder('密码').fill(process.env.E2E_ADMIN_PASSWORD || '')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/planning$/)
  await page.getByRole('button', { name: '生成排产' }).click()
  const dialog = page.getByRole('dialog', { name: '生成排产' })
  await expect(dialog.getByText('尚未在仓库管理中维护工厂仓库：永惠')).toBeVisible()
  await expect(dialog.getByRole('button', { name: '确认', exact: true })).toBeDisabled()
  await page.screenshot({ path: testInfo.outputPath('b02-missing-factory-mapping.png'), fullPage: true })
})
