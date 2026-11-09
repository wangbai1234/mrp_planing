import { test, expect, type Page } from '@playwright/test'

async function login(page: Page) {
  await page.goto('/login')
  await page.locator('input[placeholder*="用户名"], input[type="text"]').first().fill('admin')
  await page.locator('input[placeholder*="密码"], input[type="password"]').first().fill('admin123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(url => !url.pathname.includes('/login'))
}

test.describe('工厂仓库与基础菜单验收', () => {
  test.beforeEach(async ({ page }) => login(page))

  test('按产品确认的分组、顺序展示菜单', async ({ page }) => {
    const menu = page.locator('.app-sidebar .el-menu')
    await expect(menu.locator('.menu-group-label')).toHaveText(['计划', '基础信息', '基础规则', '系统'])
    const sections = await menu.locator('.menu-group-label, .el-menu-item').allTextContents()
    expect(sections.map(text => text.trim())).toEqual([
      '计划', '排产计划', '物料缺料', '生产执行', '经营计划', '库存快照',
      '基础信息', '工厂管理', '仓库管理', '物料管理', '物料分类', 'BOM管理',
      '基础规则', '物料策略', '库存控制策略', '生产日历', '业务周期间',
      '系统', '用户与权限'
    ])
  })

  test('工厂、仓库列表与表单字段，旧入口跳转', async ({ page }) => {
    await page.goto('/mrp/factories')
    await expect(page.getByRole('heading', { name: '工厂管理' })).toBeVisible()
    await expect(page.getByText('工厂编码', { exact: true }).first()).toBeVisible()
    await expect(page.getByText('工厂名称', { exact: true }).first()).toBeVisible()
    await page.getByRole('button', { name: '新增', exact: true }).click()
    const factoryDialog = page.locator('.el-dialog').filter({ hasText: '新增工厂' })
    await expect(factoryDialog.getByRole('textbox', { name: /工厂编码/ })).toBeVisible()
    await expect(factoryDialog.getByRole('textbox', { name: /工厂名称/ })).toBeVisible()
    await factoryDialog.getByRole('button', { name: '取消' }).click()

    await page.goto('/mrp/factory-warehouse-mappings')
    await page.waitForURL(url => url.pathname === '/mrp/warehouses')
    await expect(page.getByRole('heading', { name: '仓库管理' })).toBeVisible()
    await expect(page.getByText('未填优先级的按首次维护顺序扣减')).toBeVisible()
    await page.getByRole('button', { name: '新增', exact: true }).click()
    const warehouseDialog = page.locator('.el-dialog').filter({ hasText: '新增仓库' })
    for (const label of ['工厂', '仓库编码', '仓库名称', '仓库管理员', '对应供应商', '是否良品仓', '仓库属性', '优先级']) {
      await expect(warehouseDialog.getByText(label, { exact: true }).first()).toBeVisible()
    }
    await warehouseDialog.getByRole('button', { name: '取消' }).click()
    await expect(page.locator('.el-pagination')).toBeVisible()
    await page.getByPlaceholder('搜索工厂或仓库').fill('QA_NO_MATCH_20261028')
    await page.getByPlaceholder('搜索工厂或仓库').press('Enter')
    await expect(page.getByText('暂无仓库')).toBeVisible()
    await expect(page.getByText('数据已同步')).toHaveCount(0)
    const pagination = await page.locator('.el-pagination').boundingBox()
    const card = await page.locator('.card').first().boundingBox()
    expect(pagination && card && pagination.x + pagination.width > card.x + card.width / 2).toBeTruthy()
  })

  test('基础信息和基础规则各页面均能加载', async ({ page }) => {
    for (const [path, heading] of [
      ['/material', '物料管理'], ['/material-category', '物料分类'], ['/bom', 'BOM管理'],
      ['/mrp/material-policies', '物料策略'], ['/mrp/stock-policies', '库存控制策略'],
      ['/mrp/production-calendars', '生产日历'], ['/mrp/business-weeks', '业务周期间'],
      ['/mrp/config', '基础规则']
    ]) {
      await page.goto(path)
      await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
    }
  })

  test('库存导入说明明确品质由仓库自动识别', async ({ page }) => {
    await page.goto('/inventory')
    await page.getByRole('button', { name: '导入', exact: true }).click()
    const dialog = page.locator('.el-dialog').filter({ hasText: '库存批量导入' })
    await expect(dialog.getByText(/品质依据已维护仓库属性自动识别，无需在 Excel 中填写品质列/)).toBeVisible()
    await expect(dialog.getByRole('button', { name: '开始导入' })).toBeDisabled()
  })
})
