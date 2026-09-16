import { test, expect } from '@playwright/test'

test.describe('Login Flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
  })

  test('should redirect to login page', async ({ page }) => {
    await expect(page).toHaveURL(/.*login/)
  })

  test('should show login form', async ({ page }) => {
    await page.waitForURL('**/login')

    const usernameInput = page.locator('input[placeholder*="用户名"], input[type="text"]').first()
    const passwordInput = page.locator('input[placeholder*="密码"], input[type="password"]').first()
    const loginButton = page.locator('button:has-text("登录"), button[type="submit"]').first()

    await expect(usernameInput).toBeVisible()
    await expect(passwordInput).toBeVisible()
    await expect(loginButton).toBeVisible()
  })

  test('should show error for invalid credentials', async ({ page }) => {
    await page.waitForURL('**/login')

    const usernameInput = page.locator('input[placeholder*="用户名"], input[type="text"]').first()
    const passwordInput = page.locator('input[placeholder*="密码"], input[type="password"]').first()
    const loginButton = page.locator('button:has-text("登录"), button[type="submit"]').first()

    await usernameInput.fill('invalid')
    await passwordInput.fill('invalid')
    await loginButton.click()

    const errorMessage = page.locator('.el-message--error, .error-message, [role="alert"]').first()
    await expect(errorMessage).toBeVisible({ timeout: 10000 })
  })

  test('should login successfully with valid credentials', async ({ page }) => {
    await page.waitForURL('**/login')

    const usernameInput = page.locator('input[placeholder*="用户名"], input[type="text"]').first()
    const passwordInput = page.locator('input[placeholder*="密码"], input[type="password"]').first()
    const loginButton = page.locator('button:has-text("登录"), button[type="submit"]').first()

    await usernameInput.fill('admin')
    await passwordInput.fill('admin123')
    await loginButton.click()

    await expect(page).not.toHaveURL(/.*login/, { timeout: 10000 })
  })
})

test.describe('Dashboard', () => {
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

  test('should show layout after login', async ({ page }) => {
    await expect(page).not.toHaveURL(/.*login/)

    const nav = page.locator('.el-aside, aside, [role="complementary"], .sidebar').first()
    await expect(nav).toBeVisible({ timeout: 5000 })
  })

  test('should have navigation menu', async ({ page }) => {
    const menuItems = page.locator('.el-menu-item, .menu-item, nav a, .el-aside .el-menu')
    await expect(menuItems.first()).toBeVisible({ timeout: 5000 })
    const count = await menuItems.count()
    expect(count).toBeGreaterThan(0)
  })
})
