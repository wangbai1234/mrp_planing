import { expect, test } from '@playwright/test'

test('经营计划版本元信息收纳在标题栏详情，草稿状态清晰', async ({ page }) => {
  const base = process.env.FORECAST_E2E_BASE || 'http://localhost:5191'
  await page.goto(base + '/login')
  await page.getByPlaceholder('邮箱').fill('admin')
  await page.getByPlaceholder('密码').fill('admin123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).not.toHaveURL(/\/login/)
  await page.goto(base + '/forecast')
  await expect(page.locator('.forecast-version-heading').getByText(/生效版本 V/)).toBeVisible()
  await expect(page.locator('.forecast-meta')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '返回最新版本' })).toHaveCount(0)
  await expect(page.locator('.page-actions').getByRole('button', { name: '查看日志' })).toHaveCount(0)
  await expect(page.locator('.forecast-page > .el-card').getByRole('button', { name: '版本差异' })).toHaveCount(0)
  await page.getByRole('button', { name: '版本详情' }).click()
  const info = page.getByRole('dialog', { name: /版本详情/ })
  await expect(info.getByText('创建人')).toBeVisible()
  await expect(info.getByText('生效时间')).toBeVisible()
  await expect(info.getByText('系统管理员').first()).toBeVisible()
  await info.getByRole('button', { name: '关闭此对话框' }).click()
  await page.getByRole('button', { name: '查看版本' }).click()
  const history = page.getByRole('dialog', { name: '版本记录' })
  await expect(history.locator('.el-table__body tr').first()).toBeVisible()
  await history.locator('.el-table__body tr').last().getByRole('button', { name: '查看' }).click()
  if (await page.getByRole('button', { name: '返回最新版本' }).count()) {
    await expect(page.locator('.forecast-version-heading').getByText(/历史版本 V/)).toBeVisible()
    await page.getByRole('button', { name: '版本详情' }).click()
    await expect(page.getByRole('dialog', { name: /版本详情/ }).getByText('创建人')).toBeVisible()
    await page.getByRole('dialog', { name: /版本详情/ }).getByRole('button', { name: '关闭此对话框' }).click()
    await page.getByRole('button', { name: '返回最新版本' }).click()
  }
  await expect(page.getByRole('button', { name: '返回最新版本' })).toHaveCount(0)
})
