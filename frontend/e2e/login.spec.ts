import { test, expect } from '@playwright/test'

test.describe('Login Flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
  })

  test('should redirect to login page', async ({ page }) => {
    // Should be redirected to login
    await expect(page).toHaveURL(/.*login/)
  })

  test('should show login form', async ({ page }) => {
    await page.waitForURL('**/login')
    
    // Check login form elements exist
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
    
    // Should show error message - look for alert or error text
    const errorMessage = page.locator('.el-message--error, .error-message, [role="alert"]').first()
    await expect(errorMessage).toBeVisible({ timeout: 5000 })
  })

  test('should login successfully with valid credentials', async ({ page }) => {
    await page.waitForURL('**/login')
    
    const usernameInput = page.locator('input[placeholder*="用户名"], input[type="text"]').first()
    const passwordInput = page.locator('input[placeholder*="密码"], input[type="password"]').first()
    const loginButton = page.locator('button:has-text("登录"), button[type="submit"]').first()
    
    await usernameInput.fill('admin')
    await passwordInput.fill('admin123')
    await loginButton.click()
    
    // Should redirect to dashboard/home
    await expect(page).not.toHaveURL(/.*login/, { timeout: 10000 })
  })
})

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    // Login first
    await page.goto('/login')
    await page.waitForURL('**/login')
    
    const usernameInput = page.locator('input[placeholder*="用户名"], input[type="text"]').first()
    const passwordInput = page.locator('input[placeholder*="密码"], input[type="password"]').first()
    const loginButton = page.locator('button:has-text("登录"), button[type="submit"]').first()
    
    await usernameInput.fill('admin')
    await passwordInput.fill('admin123')
    await loginButton.click()
    
    // Wait for redirect
    await page.waitForURL((url) => !url.pathname.includes('login'), { timeout: 10000 })
  })

  test('should show dashboard after login', async ({ page }) => {
    // Should be on dashboard or home page
    await expect(page).not.toHaveURL(/.*login/)
    
    // Check for navigation elements - look for sidebar or menu
    const nav = page.locator('[role="complementary"], .sidebar, .el-aside, aside').first()
    await expect(nav).toBeVisible({ timeout: 5000 })
  })

  test('should have navigation menu', async ({ page }) => {
    // Look for menu items
    const menuItems = page.locator('.el-menu-item, .menu-item, nav a')
    const count = await menuItems.count()
    expect(count).toBeGreaterThan(0)
  })
})
