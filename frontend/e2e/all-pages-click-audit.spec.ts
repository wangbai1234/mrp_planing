import { expect, test } from '@playwright/test'
import { menuItems } from '../src/config/menu'

const dialogCommands = ['导入', '新增', '历史版本', '查看版本', '版本信息']

test('管理员逐页点击导航、搜索、分页和常见弹窗', async ({ page }, testInfo) => {
  test.setTimeout(180_000)
  const password = process.env.E2E_ADMIN_PASSWORD
  expect(password, 'E2E_ADMIN_PASSWORD').toBeTruthy()

  const errors: string[] = []
  page.on('pageerror', error => errors.push(`${page.url()}: ${error.message}`))
  page.on('response', response => {
    if (response.url().includes('/api/v1/') && response.status() >= 500) {
      errors.push(`${response.status()} ${response.url()}`)
    }
  })

  await page.goto('/login')
  await page.getByPlaceholder('邮箱').fill('admin')
  await page.getByPlaceholder('密码').fill(password!)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).not.toHaveURL(/\/login/)

  const visited: string[] = []
  for (const item of menuItems) {
    await page.locator('.app-sidebar .el-menu-item').filter({ hasText: item.label }).click()
    await expect(page).toHaveURL(new RegExp(`${item.path.replaceAll('/', '\\/')}$`))
    await expect(page.locator('main')).not.toBeEmpty()
    await expect(page.getByText('页面加载失败', { exact: false })).toHaveCount(0)

    const pager = page.locator('main .el-pagination:visible').first()
    if (await pager.count()) {
      const next = pager.locator('.btn-next')
      const totalText = await pager.locator('.el-pagination__total').innerText().catch(() => '')
      const total = Number(totalText.match(/\d+/)?.[0] || 0)
      const pageSizeText = await pager.locator('.el-select').innerText().catch(() => '20')
      const pageSize = Number(pageSizeText.match(/\d+/)?.[0] || 20)
      if (total > pageSize && await next.count() && await next.isEnabled()) {
        await next.click({ timeout: 3000 })
        const previous = pager.locator('.btn-prev')
        if (await previous.isEnabled()) await previous.click({ timeout: 3000 })
      }
      const position = await pager.evaluate(element => {
        const parent = element.parentElement!.getBoundingClientRect()
        return { right: element.getBoundingClientRect().right, parentRight: parent.right }
      })
      expect(position.parentRight - position.right, `${item.label} pagination alignment`).toBeLessThanOrEqual(32)
    }

    const search = page.locator('main input[placeholder*="搜索"]:visible').first()
    if (await search.count()) {
      await search.fill('ZZZ_NO_MATCH_2026')
      await search.press('Enter')
      await search.fill('')
    }

    for (const command of dialogCommands) {
      const button = page.locator('main button:visible').filter({ hasText: new RegExp(`^${command}$`) }).first()
      if (!await button.count() || !await button.isEnabled()) continue
      await button.click()
      const close = page.locator('.el-dialog:visible .el-dialog__headerbtn, .el-drawer:visible .el-drawer__close-btn').first()
      if (await close.count()) await close.click()
      else await page.keyboard.press('Escape')
      if (new URL(page.url()).pathname !== item.path) await page.goto(item.path)
    }

    await page.screenshot({ path: testInfo.outputPath(`${item.code}.png`), fullPage: false })
    visited.push(item.path)
  }

  for (const item of [
    { label: '用户', path: '/admin/users' },
    { label: '角色', path: '/admin/roles' },
    { label: '操作日志', path: '/admin/audit-logs' }
  ]) {
    await page.locator('.admin-sidebar .el-menu-item').filter({ hasText: item.label }).click()
    await expect(page).toHaveURL(new RegExp(`${item.path.replaceAll('/', '\\/')}$`))
    await expect(page.locator('main')).not.toBeEmpty()
    visited.push(item.path)
  }

  expect(visited).toHaveLength(menuItems.length + 3)
  expect(errors).toEqual([])
})

test('生产执行支持模糊搜索、清空恢复和服务端分页', async ({ page }) => {
  const password = process.env.E2E_ADMIN_PASSWORD
  expect(password, 'E2E_ADMIN_PASSWORD').toBeTruthy()
  await page.goto('/login')
  await page.getByPlaceholder('邮箱').fill('admin')
  await page.getByPlaceholder('密码').fill(password!)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).not.toHaveURL(/\/login/)
  await page.goto('/execution')

  const search = page.getByPlaceholder('搜索整机料号/工厂')
  const pager = page.locator('.execution-confirm .el-pagination')
  await expect(search).toBeVisible()
  await expect(pager).toBeVisible()
  await expect(page.locator('.execution-confirm .el-table__body tr').first()).toBeVisible()
  const material = (await page.locator('.execution-confirm .el-table__body tr').first().locator('td').nth(1).innerText()).trim()
  expect(material.length).toBeGreaterThan(2)
  const keyword = material.slice(1, Math.min(5, material.length))
  const searched = page.waitForResponse(response => response.url().includes('/execution/page?')
    && new URL(response.url()).searchParams.get('keyword') === keyword && response.status() === 200)
  await search.fill(keyword)
  await search.press('Enter')
  await searched
  await expect(page.locator('.execution-confirm .el-table__body tr').first()).toContainText(material)

  const cleared = page.waitForResponse(response => response.url().includes('/execution/page?')
    && !new URL(response.url()).searchParams.has('keyword') && response.status() === 200)
  await search.clear()
  await cleared

  const resized = page.waitForResponse(response => response.url().includes('/execution/page?')
    && new URL(response.url()).searchParams.get('pageSize') === '50' && response.status() === 200)
  await pager.locator('.el-select').click()
  await page.getByRole('option', { name: '50条/页' }).click()
  await resized
  const alignment = await pager.evaluate(element => {
    const parent = element.parentElement!.getBoundingClientRect()
    return parent.right - element.getBoundingClientRect().right
  })
  expect(alignment).toBeLessThanOrEqual(32)
})
