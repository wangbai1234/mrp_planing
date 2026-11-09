import { test, expect, type Page } from '@playwright/test'

async function login(page: Page) {
  await page.goto('/login')
  await page.locator('input[placeholder*="用户名"], input[type="text"]').first().fill('admin')
  await page.locator('input[placeholder*="密码"], input[type="password"]').first().fill('admin123')
  await page.locator('button:has-text("登录"), button[type="submit"]').first().click()
  await page.waitForURL(url => !url.pathname.includes('login'), { timeout: 10000 })
}

async function openWorkspace(page: Page) {
  await page.goto('/planning')
  await expect(page.getByRole('heading', { name: '排产计划' })).toBeVisible({ timeout: 10000 })
}

test.describe('Phase 2 整机排产与物料缺料双工作台', () => {
  test.beforeEach(async ({ page }) => login(page))

  test('旧批次地址跳转到排产页且侧栏包含两个岗位入口', async ({ page }) => {
    await page.goto('/mrp/batches')
    await expect(page).toHaveURL(/\/planning$/)
    await expect(page.getByRole('heading', { name: '排产计划' })).toBeVisible()
    await expect(page.locator('.app-sidebar').getByText('排产计划', { exact: true })).toBeVisible()
    await expect(page.locator('.app-sidebar').getByText('物料缺料', { exact: true })).toBeVisible()
    await expect(page.locator('.app-sidebar').getByText('MRP批次', { exact: true })).toHaveCount(0)
  })

  test('当前版本只显示整机排产标签与物料跳转', async ({ page }) => {
    await openWorkspace(page)
    await expect(page.getByText('当前排产', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '历史版本' })).toBeVisible()
    await expect(page.getByRole('button', { name: '生成排产' })).toBeVisible()
    await expect(page.getByText('本次排产流程', { exact: true })).toHaveCount(0)
    await expect(page.locator('.batch-metrics')).toHaveCount(0)
    const dailyTab = page.locator('.el-tabs__item:has-text("日排产")')
    await expect(dailyTab).toBeVisible()
    const disabled = await dailyTab.getAttribute('class')
    if (!disabled?.includes('is-disabled')) {
      await expect(dailyTab).toHaveAttribute('aria-selected', 'true')
      await expect(page.locator('.rolling-grid')).toBeVisible()
    }
    await expect(page.locator('.workspace-tabs .el-tabs__item:has-text("库存分配")')).toHaveCount(0)
    const version = (await page.locator('.version-line').innerText()).match(/#(\d+)/)
    if (version) {
      await page.getByRole('button', { name: '物料缺料' }).click()
      await expect(page).toHaveURL(new RegExp(`/shortage/${version[1]}$`))
      await expect(page.getByRole('heading', { name: '物料缺料' })).toBeVisible()
      await expect(page.getByText(`批次 #${version[1]}`, { exact: false })).toBeVisible()
      await page.getByRole('button', { name: '返回整机排产' }).click()
      await expect(page).toHaveURL(new RegExp(`/planning/${version[1]}$`))
    }
  })

  test('历史版本在弹窗查询并切换主视图', async ({ page }) => {
    await openWorkspace(page)
    await page.getByRole('button', { name: '历史版本' }).click()
    const dialog = page.locator('.el-dialog').filter({ hasText: '历史版本' })
    await expect(dialog).toBeVisible()
    await expect(dialog.getByPlaceholder('搜索版本号或工厂')).toBeVisible()
    await expect(dialog.locator('.el-pagination')).toBeVisible()
    const view = dialog.getByRole('button', { name: '查看' }).first()
    if (await view.count()) {
      await view.click()
      await expect(page).toHaveURL(/\/planning\/\d+$/)
      await expect(page.getByRole('heading', { name: '排产计划' })).toBeVisible()
      const returnToCurrent = page.getByRole('button', { name: '返回当前版本' })
      if (await returnToCurrent.count()) {
        await expect(page.getByText('历史版本', { exact: true }).first()).toBeVisible()
        await returnToCurrent.click()
        await expect(page).toHaveURL(/\/planning$/)
      } else {
        await expect(page.getByText('当前排产', { exact: true })).toBeVisible()
      }
    }
  })

  test('已有进行中版本时创建入口给出明确提示', async ({ page }) => {
    await openWorkspace(page)
    const currentStatus = await page.locator('.status-bar').getByText('当前排产', { exact: true }).count()
    await page.getByRole('button', { name: '生成排产' }).click()
    if (currentStatus) {
      const warning = page.locator('.el-message--warning')
      const dialog = page.locator('.el-dialog').filter({ hasText: '生成排产' })
      expect(await warning.isVisible().catch(() => false) || await dialog.isVisible().catch(() => false)).toBeTruthy()
    }
  })

  test('整机页保留汇总，物控功能迁移到缺料页', async ({ page }) => {
    await openWorkspace(page)
    for (const label of ['日排产', '周汇总', '月汇总']) {
      await expect(page.locator(`.el-tabs__item:has-text("${label}")`)).toBeVisible()
    }
    for (const label of ['库存分配', '计算追溯', '待处理异常']) {
      await expect(page.locator(`.workspace-tabs .el-tabs__item:has-text("${label}")`)).toHaveCount(0)
    }
    await page.goto('/shortage')
    await expect(page.getByRole('heading', { name: '物料缺料' })).toBeVisible()
    await expect(page.getByRole('button', { name: '末级物料' })).toBeVisible()
    await expect(page.locator('.workspace-nav .el-select')).toBeVisible()
    await page.getByRole('button', { name: '数据口径' }).click()
    await expect(page.getByRole('dialog', { name: '数据口径' })).toContainText('采购在途')
  })

  test('不存在的历史版本显示可恢复错误而非白屏', async ({ page }) => {
    await page.goto('/planning/99999999')
    const alert = page.locator('.el-alert--error')
    await expect(alert).toBeVisible({ timeout: 10000 })
    await expect(alert.getByRole('button', { name: '重新加载' })).toBeVisible()
    await page.goto('/shortage/99999999')
    await expect(page.locator('.el-alert--error').getByRole('button', { name: '重新加载' })).toBeVisible()
  })

  test('配置页面保持可访问', async ({ page }) => {
    await page.goto('/mrp/material-policies')
    await expect(page.getByRole('heading', { name: '物料策略' })).toBeVisible()
    await page.goto('/mrp/production-calendars')
    await expect(page.getByRole('heading', { name: '生产日历' })).toBeVisible()
  })
})
