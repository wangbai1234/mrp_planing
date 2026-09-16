import { test, expect } from '@playwright/test'

test.describe('Planning Grid', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.waitForURL('**/login')

    const usernameInput = page.locator('input[placeholder*="用户名"], input[type="text"]').first()
    const passwordInput = page.locator('input[placeholder*="密码"], input[type="password"]').first()
    const loginButton = page.locator('button:has-text("登录"), button[type="submit"]').first()

    await usernameInput.fill('admin')
    await passwordInput.fill('admin123')
    await loginButton.click()

    await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })
  })

  test('should display planning page after login', async ({ page }) => {
    await expect(page).toHaveURL(/.*planning/)
    const pageTitle = page.locator('.page-title, h1').first()
    await expect(pageTitle).toBeVisible({ timeout: 5000 })
  })

  test('should display planning grid table', async ({ page }) => {
    await page.waitForTimeout(2000)
    const table = page.locator('table, .el-table').first()
    await expect(table).toBeVisible({ timeout: 5000 })
  })

  test('should show week columns', async ({ page }) => {
    await page.waitForTimeout(2000)
    const weekHeaders = page.locator('thead th')
    const count = await weekHeaders.count()
    expect(count).toBeGreaterThan(3)
  })

  test('should show toolbar with search and filters', async ({ page }) => {
    await page.waitForTimeout(1000)
    const searchInput = page.locator('input[placeholder*="搜索"]').first()
    await expect(searchInput).toBeVisible({ timeout: 5000 })
  })

  test('should show action buttons', async ({ page }) => {
    const recalcButton = page.locator('button:has-text("重算")').first()
    const publishButton = page.locator('button:has-text("发布"), button:has-text("已发布")').first()
    const exportButton = page.locator('button:has-text("导出")').first()

    await expect(recalcButton).toBeVisible({ timeout: 5000 })
    await expect(exportButton).toBeVisible({ timeout: 5000 })
  })

  test('should show history versions section', async ({ page }) => {
    await page.waitForTimeout(2000)
    const historySection = page.locator('text=历史版本').first()
    await expect(historySection).toBeVisible({ timeout: 5000 })
  })

  test('should navigate to other pages from sidebar', async ({ page }) => {
    const sidebar = page.locator('.el-aside, aside').first()
    await expect(sidebar).toBeVisible({ timeout: 5000 })

    const forecastMenu = page.locator('.el-menu-item:has-text("经营计划")').first()
    await expect(forecastMenu).toBeVisible({ timeout: 5000 })
  })

  test('should open export dialog', async ({ page }) => {
    const exportButton = page.locator('button:has-text("导出")').first()
    await exportButton.click()

    const dialog = page.locator('.el-dialog').first()
    await expect(dialog).toBeVisible({ timeout: 5000 })

    const dialogTitle = page.locator('.el-dialog__title:has-text("选择导出字段")').first()
    await expect(dialogTitle).toBeVisible({ timeout: 3000 })
  })

  test('should open compare dialog', async ({ page }) => {
    const compareButton = page.locator('button:has-text("版本对比")').first()
    await compareButton.click()

    const dialog = page.locator('.el-dialog').first()
    await expect(dialog).toBeVisible({ timeout: 5000 })
  })
})
