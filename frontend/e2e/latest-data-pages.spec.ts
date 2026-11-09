import { test, expect, type Page } from '@playwright/test'

async function login(page: Page) {
  await page.goto('/login')
  await page.locator('input[placeholder*="用户名"], input[type="text"]').first().fill('admin')
  await page.locator('input[placeholder*="密码"], input[type="password"]').first().fill('admin123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(url => !url.pathname.includes('/login'), { timeout: 10000 })
}

test.describe('最新数据首屏与历史版本', () => {
  test.beforeEach(async ({ page }) => login(page))

  test('经营计划首屏显示当前计划并能进入版本记录', async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })

    await page.goto('/forecast')
    await expect(page.locator('.forecast-version-heading')).toContainText('经营计划', { timeout: 10000 })
    await expect(page.locator('.forecast-version-heading .el-tag').first()).toContainText(/V\d+/)
    await expect(page.locator('.el-card .el-table__row').first()).toBeVisible()
    await expect(page.getByText('数据已同步')).toHaveCount(0)
    const materialCode = (await page.locator('.el-card .el-table__row').first().locator('td').nth(2).innerText()).trim()
    const search = page.getByPlaceholder('搜索料号/名称/工厂')
    await search.fill(materialCode.slice(0, 4))
    await search.press('Enter')
    await expect(page.locator('.el-card .el-table__row').first()).toContainText(materialCode)
    const pagination = await page.locator('.el-card .el-pagination').boundingBox()
    const card = await page.locator('.el-card').boundingBox()
    expect(pagination && card && pagination.x + pagination.width > card.x + card.width / 2).toBeTruthy()
    await page.getByRole('button', { name: '查看版本' }).click()
    const dialog = page.locator('.el-dialog').filter({ hasText: '版本记录' })
    await expect(dialog).toBeVisible()
    await expect(dialog.locator('.el-table__row').first()).toBeVisible()
    await dialog.getByRole('button', { name: '查看' }).first().click()
    await expect(dialog).toBeHidden()
    expect(errors).toEqual([])
  })

  test('库存首屏展示最新快照，导入按行区分多仓库', async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })

    await page.goto('/inventory')
    await expect(page.locator('.table-title-text')).toContainText('库存快照 #', { timeout: 10000 })
    await expect(page.locator('.table-card .el-table__header').getByText('仓库', { exact: true })).toBeVisible()
    await expect(page.locator('.table-card .el-table__header').getByText('品质', { exact: true })).toBeVisible()

    await page.getByRole('button', { name: '历史版本' }).click()
    const historyDialog = page.locator('.el-dialog').filter({ hasText: '历史版本' })
    await expect(historyDialog.locator('.el-table__row').first()).toBeVisible()
    await historyDialog.getByRole('button', { name: '查看' }).first().click()
    await expect(historyDialog).toBeHidden()

    await page.getByRole('button', { name: '导入', exact: true }).click()
    const importDialog = page.locator('.el-dialog').filter({ hasText: '库存批量导入' })
    await expect(importDialog).toBeVisible()
    await expect(importDialog.getByText('同一文件可包含多个仓库')).toBeVisible()
    await expect(importDialog.getByText('归属仓库')).toHaveCount(0)
    await expect(importDialog.locator('input[type="file"]')).toHaveCount(1)
    expect(errors).toEqual([])
  })
})
